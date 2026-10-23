package com.likhtar.home.core.catalog.media

import com.likhtar.home.core.data.Secrets
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.TmdbClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Джерело медіатеки. DLNA: [address] — опис пристрою (location). SMB: `smb://host/share/шлях`,
 * [user] порожній — гостьовий вхід.
 *
 * ponytail: пароль SMB лежить у приватному DataStore лаунчера відкритим текстом — як і токен
 * Home Assistant; шифрувати, коли з'явиться спільне сховище секретів.
 */
data class MediaSource(
    val id: String,
    val kind: Kind,
    val name: String,
    val address: String,
    val user: String = "",
    val password: String = "",
) {
    enum class Kind { DLNA, SMB }
}

/** Файл медіатеки. [url] — пряме посилання DLNA; для SMB порожній (див. [MediaLibrary.playUrl]). */
data class LocalFile(
    val sourceId: String,
    val path: String,
    val name: String,
    val url: String = "",
    val size: Long = 0,
    val season: Int = 0,
    val episode: Int = 0,
    /** Назва джерела — показати, звідки файл, коли однакові лежать у двох місцях. */
    val sourceName: String = "",
)

/** Фільм чи серіал із TMDB, для якого в медіатеці є файли. */
data class LocalTitle(val item: CatalogItem, val files: List<LocalFile>)

data class LibraryState(
    val titles: List<LocalTitle> = emptyList(),
    /** Файли, які не вдалося зіставити з TMDB: домашнє відео, концерти. */
    val other: List<LocalFile> = emptyList(),
    val scanning: Boolean = false,
    /** Що робить скан зараз або чим закінчився («Знайдено 42 файли, 17 назв»). */
    val status: String = "",
    val scannedAt: Long = 0,
)

private val Context.mediaStore: DataStore<Preferences> by preferencesDataStore(name = "likhtar_media")
private val KEY_SOURCES = stringPreferencesKey("sources")

/**
 * Медіатека: свої DLNA-сервери й SMB-папки. Скан обходить джерела, розбирає імена файлів
 * ([MediaNames]) і шукає кожну назву в TMDB — далі файл живе як звичайна картка каталогу.
 * Індекс — JSON у приватних файлах, переживає перезапуск; оновлюється кнопкою в налаштуваннях.
 */
@Singleton
class MediaLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdb: TmdbClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexFile = File(context.filesDir, "media_library.json")
    private val scanLock = Mutex()
    private val smb = ConcurrentHashMap<String, SmbAccess>()
    private val stream = LocalStream { id -> smb[id] }

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    val sources: Flow<List<MediaSource>> = context.mediaStore.data.map { decodeSources(Secrets.open(it[KEY_SOURCES])) }

    init {
        scope.launch {
            runCatching { if (indexFile.exists()) _state.value = decodeIndex(indexFile.readText()) }
                .onFailure { Log.w(TAG, "індекс медіатеки", it) }
            // Джерела з паролями, збережені до шифрування, — переписати шифрованими.
            context.mediaStore.edit { prefs ->
                prefs[KEY_SOURCES]?.takeIf { it.isNotEmpty() && !Secrets.isSealed(it) }?.let { prefs[KEY_SOURCES] = Secrets.seal(it) }
            }
            sources.first().forEach(::remember)
            // Нові файли підхоплюються самі: застарілий індекс — скан. Перший — не одразу,
            // щоб не битися за мережу й TMDB із запуском лаунчера.
            // ponytail: опитування за часом; стежити за змінами папок (SMB2 CHANGE_NOTIFY) — коли знадобиться миттєво.
            delay(FIRST_CHECK_MS)
            while (true) {
                if (sources.first().isNotEmpty() && System.currentTimeMillis() - state.value.scannedAt > RESCAN_MS) {
                    runCatching { scan() }.onFailure { Log.w(TAG, "автоскан", it) }
                }
                delay(CHECK_MS)
            }
        }
    }

    suspend fun discoverDlna(): List<DlnaServer> = Dlna.discover(context)

    suspend fun add(source: MediaSource) {
        context.mediaStore.edit { prefs ->
            val list = decodeSources(Secrets.open(prefs[KEY_SOURCES])).filterNot { it.address == source.address } + source
            // Паролі SMB — лише шифрованими (Secrets), увесь список одним рядком.
            prefs[KEY_SOURCES] = Secrets.seal(encodeSources(list))
        }
        remember(source)
    }

    suspend fun remove(id: String) {
        context.mediaStore.edit { prefs -> prefs[KEY_SOURCES] = Secrets.seal(encodeSources(decodeSources(Secrets.open(prefs[KEY_SOURCES])).filterNot { it.id == id })) }
        smb.remove(id)?.close()
        _state.update { s ->
            val titles = s.titles.mapNotNull { t -> t.copy(files = t.files.filterNot { it.sourceId == id }).takeIf { it.files.isNotEmpty() } }
            val other = s.other.filterNot { it.sourceId == id }
            s.copy(titles = titles, other = other, status = summary(titles.sumOf { it.files.size } + other.size, titles.size, other.size))
        }
        save()
    }

    /** Перевірити SMB-джерело до збереження: текст помилки або `null`, якщо папка відкрилась. */
    suspend fun check(source: MediaSource): String? = withContext(Dispatchers.IO) {
        runCatching { SmbAccess(source).apply { list("") }.close() }.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    /** Посилання для плеєра; `null` — джерело вже прибрали. */
    fun playUrl(file: LocalFile): String? = when {
        file.url.isNotBlank() -> file.url
        smb.containsKey(file.sourceId) -> stream.urlFor(file.sourceId, file.path, file.name)
        else -> null
    }

    /** Файли цієї назви, серії — по порядку. */
    fun filesFor(item: CatalogItem): List<LocalFile> = state.value.titles
        .firstOrNull { it.item.tmdbId == item.tmdbId && it.item.kind == item.kind }?.files.orEmpty()

    fun rescan() {
        scope.launch { scan() }
    }

    suspend fun scan() = scanLock.withLock {
        _state.update { it.copy(scanning = true, status = "Обходжу папки…") }
        val files = mutableListOf<LocalFile>()
        for (source in sources.first()) {
            _state.update { it.copy(status = "Обходжу «${source.name}»…") }
            runCatching { files += walk(source) }.onFailure {
                Log.w(TAG, "скан ${source.name}", it)
                _state.update { s -> s.copy(status = "«${source.name}»: ${it.message ?: "недоступне"}") }
            }
        }
        _state.update { it.copy(status = "Шукаю назви в TMDB… (файлів: ${files.size})") }
        val (titles, other) = match(files)
        val done = LibraryState(
            titles = titles,
            other = other,
            status = summary(files.size, titles.size, other.size),
            scannedAt = System.currentTimeMillis(),
        )
        _state.value = done
        save()
    }

    private fun remember(source: MediaSource) {
        if (source.kind == MediaSource.Kind.SMB) smb[source.id] = SmbAccess(source)
    }

    private fun walk(source: MediaSource): List<LocalFile> = when (source.kind) {
        MediaSource.Kind.SMB -> walkSmb(source)
        MediaSource.Kind.DLNA -> walkDlna(source)
    }

    private fun walkSmb(source: MediaSource): List<LocalFile> {
        val access = smb.getOrPut(source.id) { SmbAccess(source) }
        val out = mutableListOf<LocalFile>()
        fun go(dir: String, depth: Int) {
            if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
            for (e in access.list(dir).sortedBy { it.name }) {
                if (e.name.startsWith('.') || e.name.startsWith('$')) continue
                if (e.folder) go(e.path, depth + 1)
                else if (MediaNames.isVideo(e.name) && e.size >= MIN_SIZE) out += file(source, e.path, e.name, "", e.size)
            }
        }
        go("", 0)
        return out
    }

    private fun walkDlna(source: MediaSource): List<LocalFile> {
        val server = Dlna.describe(source.address) ?: error("сервер не відповідає")
        // Один файл DLNA показує в кількох «видах» (Усе відео, Папки, За датою) — лишаємо той
        // шлях, де більше папок: з нього назва серіалу береться надійніше.
        val byUrl = LinkedHashMap<String, LocalFile>()
        val seenFolders = HashSet<String>()
        fun go(id: String, path: String, depth: Int) {
            if (depth > MAX_DEPTH || byUrl.size >= MAX_FILES || !seenFolders.add(id)) return
            for (e in Dlna.browse(server.control, id)) {
                val p = if (path.isEmpty()) e.title else "$path/${e.title}"
                if (e.folder) {
                    go(e.id, p, depth + 1)
                } else if (e.url.isNotBlank() && (e.mime.startsWith("video/") || MediaNames.isVideo(e.title)) && (e.size == 0L || e.size >= MIN_SIZE)) {
                    val name = if (MediaNames.isVideo(e.title)) e.title else e.title + "." + extOf(e.url)
                    val f = file(source, if (path.isEmpty()) name else "$path/$name", name, e.url, e.size)
                    val old = byUrl[e.url]
                    if (old == null || old.path.count { it == '/' } < f.path.count { it == '/' }) byUrl[e.url] = f
                }
            }
        }
        go("0", "", 0)
        return byUrl.values.toList()
    }

    private fun file(source: MediaSource, path: String, name: String, url: String, size: Long): LocalFile {
        val parsed = MediaNames.parse(path)
        return LocalFile(source.id, path, name, url, size, parsed.season, parsed.episode, source.name)
    }

    /** Файли групуються за прочитаною назвою, кожна група — один пошук у TMDB. */
    private suspend fun match(files: List<LocalFile>): Pair<List<LocalTitle>, List<LocalFile>> {
        val groups = files.groupBy { f ->
            val n = MediaNames.parse(f.path)
            Triple(n.title.lowercase(), n.year, n.isEpisode)
        }
        val titles = LinkedHashMap<String, LocalTitle>()
        val other = mutableListOf<LocalFile>()
        for ((key, group) in groups) {
            val (title, year, episodes) = key
            val sample = MediaNames.parse(group.first().path).title
            val item = if (title.isBlank()) null else runCatching {
                val kinds = if (episodes) listOf(TitleKind.TV) else listOf(TitleKind.MOVIE, TitleKind.TV)
                kinds.firstNotNullOfOrNull { kind -> pick(tmdb.searchKind(kind, sample, 0), sample, year) }
            }.getOrNull()
            if (item == null) {
                other += group
                continue
            }
            val sorted = group.sortedWith(compareBy({ it.season }, { it.episode }, { it.name }))
            titles.merge(item.id, LocalTitle(item, sorted)) { a, b -> a.copy(files = (a.files + b.files).sortedWith(compareBy({ it.season }, { it.episode }, { it.name }))) }
        }
        return titles.values.sortedBy { it.item.title } to other.sortedBy { it.path }
    }

    private fun save() {
        runCatching { indexFile.writeText(encodeIndex(state.value)) }.onFailure { Log.w(TAG, "запис індексу", it) }
    }

    private fun summary(files: Int, titles: Int, other: Int) =
        "Файлів: $files, назв: $titles" + if (other > 0) ", не впізнано: $other" else ""

    private fun extOf(url: String) = url.substringBefore('?').substringAfterLast('.', "mkv").take(4)

    companion object {
        private const val TAG = "LikhtarMedia"
        private const val FIRST_CHECK_MS = 2 * 60_000L
        private const val CHECK_MS = 30 * 60_000L
        /** Індекс старший за 6 годин — пересканувати. */
        private const val RESCAN_MS = 6 * 60 * 60_000L
        private const val MAX_DEPTH = 8
        private const val MAX_FILES = 10_000
        /** Менше 20 МБ — трейлери, семпли й мініатюри, не кіно. */
        private const val MIN_SIZE = 20L * 1024 * 1024

        /**
         * TMDB віддає перший-ліпший результат, і «Відпустка 2023 моя.mp4» ставала б чужим
         * фільмом «Відпустка». Беремо, лише коли назва збігається з українською чи оригінальною
         * (без розділових знаків і регістру) і рік, якщо він є в імені, — плюс-мінус один.
         */
        /**
         * Серед однойменних — з тим самим роком, а без року в імені найновіший: «Темна
         * матерія» без року — серіал 2024-го, не 2015-го.
         *
         * ponytail: «найновіший» — евристика; надійніше звіряти кількість сезонів із файлами.
         */
        internal fun pick(results: List<CatalogItem>, name: String, year: Int): CatalogItem? =
            results.filter { sameTitle(it, name, year) }.maxByOrNull { it.year.take(4).toIntOrNull() ?: 0 }
                ?.let { newest -> if (year > 0) results.firstOrNull { sameTitle(it, name, year) } else newest }

        internal fun sameTitle(item: CatalogItem, name: String, year: Int): Boolean {
            val q = norm(name)
            if (q.isEmpty()) return false
            val names = listOf(item.title, item.originalTitle).map(::norm).filter(String::isNotEmpty)
            val nameOk = q in names
            val y = item.year.take(4).toIntOrNull()
            val yearOk = year == 0 || y == null || kotlin.math.abs(y - year) <= 1
            return nameOk && yearOk
        }

        private fun norm(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("""[^\p{L}\p{Nd}]+"""), " ").trim()

        internal fun encodeSources(list: List<MediaSource>): String = JSONArray(
            list.map {
                JSONObject().put("id", it.id).put("kind", it.kind.name).put("name", it.name)
                    .put("address", it.address).put("user", it.user).put("password", it.password)
            },
        ).toString()

        internal fun decodeSources(raw: String?): List<MediaSource> {
            if (raw.isNullOrBlank()) return emptyList()
            val a = JSONArray(raw)
            return (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val kind = runCatching { MediaSource.Kind.valueOf(o.optString("kind")) }.getOrNull() ?: return@mapNotNull null
                MediaSource(o.optString("id"), kind, o.optString("name"), o.optString("address"), o.optString("user"), o.optString("password"))
            }
        }

        private fun JSONObject.str(name: String): String? = optString(name).takeIf { it.isNotBlank() && it != "null" }

        private fun fileJson(f: LocalFile) = JSONObject().put("source", f.sourceId).put("path", f.path).put("name", f.name)
            .put("url", f.url).put("size", f.size).put("season", f.season).put("episode", f.episode).put("sourceName", f.sourceName)

        private fun fileOf(o: JSONObject) = LocalFile(
            o.optString("source"), o.optString("path"), o.optString("name"), o.optString("url"),
            o.optLong("size"), o.optInt("season"), o.optInt("episode"), o.optString("sourceName"),
        )

        internal fun encodeIndex(s: LibraryState): String = JSONObject()
            .put("at", s.scannedAt).put("status", s.status)
            .put(
                "titles",
                JSONArray(
                    s.titles.map { t ->
                        val i = t.item
                        JSONObject().put("tmdb", i.tmdbId).put("kind", i.kind.name).put("title", i.title)
                            .put("original", i.originalTitle).put("year", i.year).put("poster", i.posterPath)
                            .put("backdrop", i.backdropPath).put("rating", i.rating).put("overview", i.overview)
                            .put("files", JSONArray(t.files.map(::fileJson)))
                    },
                ),
            )
            .put("other", JSONArray(s.other.map(::fileJson)))
            .toString()

        internal fun decodeIndex(raw: String): LibraryState {
            val o = JSONObject(raw)
            val titles = o.optJSONArray("titles") ?: JSONArray()
            val other = o.optJSONArray("other") ?: JSONArray()
            return LibraryState(
                titles = (0 until titles.length()).map { n ->
                    val t = titles.getJSONObject(n)
                    val files = t.optJSONArray("files") ?: JSONArray()
                    LocalTitle(
                        CatalogItem(
                            tmdbId = t.optInt("tmdb"),
                            kind = runCatching { TitleKind.valueOf(t.optString("kind")) }.getOrDefault(TitleKind.MOVIE),
                            title = t.optString("title"),
                            originalTitle = t.optString("original"),
                            year = t.optString("year"),
                            posterPath = t.str("poster"),
                            backdropPath = t.str("backdrop"),
                            rating = t.optDouble("rating", 0.0),
                            overview = t.optString("overview"),
                        ),
                        (0 until files.length()).map { fileOf(files.getJSONObject(it)) },
                    )
                },
                other = (0 until other.length()).map { fileOf(other.getJSONObject(it)) },
                status = o.optString("status"),
                scannedAt = o.optLong("at"),
            )
        }
    }
}
