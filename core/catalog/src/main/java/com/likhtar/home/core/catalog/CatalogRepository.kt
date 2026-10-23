package com.likhtar.home.core.catalog

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import java.net.URLEncoder
import com.likhtar.home.core.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Пошук по каталогу — окремим контрактом.
 *
 * Потрібен, щоб екран пошуку можна було перевірити тестом: інакше ViewModel залежав би від
 * конкретного репозиторію з мережею всередині, і жоден тест його не створив би.
 */
interface TitleSearch {
    suspend fun search(query: String): List<CatalogItem>

    /** Той самий тайтл із логотипом — для банера. */
    suspend fun withLogo(item: CatalogItem): CatalogItem = item

    /** Ряди «з чого почати» для порожнього пошуку. */
    suspend fun starters(): List<SearchShelf> = emptyList()

    /** «Ліхтарик»-радник: назва, опис чи прохання («порадь комедію на вечір») — потоком. */
    fun ask(query: String): Flow<AskEvent> = emptyFlow()

    /** Чи є кому відповісти на прохання: у публічній збірці — лише з підключеним AI людини. */
    suspend fun canAsk(): Boolean = true

    /** «Ліхтарик радить»: схоже на те, що дивились і відклали. */
    suspend fun advise(like: List<String>): Advice = Advice()

    /** Щоденні особисті добірки профілю: складає Antigravity на сервері, тут — готові. */
    suspend fun shelves(profile: String): List<Advice> = emptyList()
}

/** Подія відповіді Ліхтарика: думка (наростає) чи звірена з TMDB картка; rank 0 — найкращий вибір. */
sealed interface AskEvent {
    data class Thought(val text: String) : AskEvent

    /** «Подумав ще» — друга думка (Antigravity), приходить пізніше за першу. */
    data class Refined(val text: String) : AskEvent
    data class Found(val rank: Int, val item: CatalogItem, val why: String) : AskEvent
}

/** Поради Ліхтарика: що він помітив у смаку, картки й «чому» до кожної (за [CatalogItem.id]). */
data class Advice(
    val thought: String = "",
    /** Назва ряду — у щоденних добірках («Бо вам сподобалась «Темна матерія»»). */
    val title: String = "",
    val items: List<CatalogItem> = emptyList(),
    val why: Map<String, String> = emptyMap(),
)

/** Ряд на порожньому екрані пошуку: назва, картки й ті, що з українською доріжкою. */
data class SearchShelf(val title: String, val items: List<CatalogItem>, val ukrainian: Set<Int>)

/** Стан каталогу Кінозалу. */
data class CatalogState(
    val configured: Boolean = false,
    val loading: Boolean = false,
    val rows: List<CatalogRow> = emptyList(),
    val moods: List<MoodTile> = emptyList(),
    /** Плитки сервісів: кожна веде на сторінку з усією курацією цього сервісу. */
    val studios: List<CatalogStudio> = emptyList(),
    val chronologies: List<Chronology> = emptyList(),
    val message: String? = null,
)

/**
 * Каталог Кінозалу: куровані рядки Ліхтаря, наповнені через TMDB.
 *
 * Рядки наповнюються паралельно — їх два десятки, і послідовно це були б десятки секунд
 * на приставці. Порожні рядки відкидаються: краще коротший екран, ніж заголовок без карток.
 */
@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rows: LikhtarRows,
    private val tmdb: TmdbClient,
    private val cards: LikhtarCards,
    private val watchOptions: WatchOptionsResolver,
    private val resolver: ResolverClient,
    private val config: CatalogConfig,
    private val settings: SettingsRepository,
    private val ukrainian: UkrainianCatalog,
    private val sectionRows: SectionRowsRepository,
    private val ownAi: OwnAiAdvisor,
) : TitleSearch {
    suspend fun load(): CatalogState {
        if (config.tmdbKey() == null) {
            return CatalogState(
                configured = false,
                message = noKeyMessage(),
            )
        }

        val curation = rows.load()
        if (curation.rows.isEmpty()) {
            return CatalogState(
                configured = true,
                message = context.getString(R.string.catalog_msg_no_curated_rows),
            )
        }

        val filled = fill(curation.rows)

        return CatalogState(
            configured = true,
            rows = filled,
            moods = curation.moods,
            studios = curation.studios,
            chronologies = curation.chronologies,
            message = if (filled.isEmpty()) context.getString(R.string.catalog_msg_tmdb_no_rows) else null,
        )
    }

    /**
     * Курація без наповнення: лише плитки сервісів, хронології та настрої.
     *
     * Кінозал показує стрічки самих застосунків, а куровані рядки живуть на сторінках
     * сервісів — тому наповнювати їх при відкритті екрана немає сенсу. Це ще й п'ять
     * запитів до TMDB, яких більше немає на шляху до першого кадру.
     */
    suspend fun curation(): CatalogState {
        if (config.tmdbKey() == null) {
            return CatalogState(
                configured = false,
                message = noKeyMessage(),
            )
        }

        val curation = rows.load()
        return CatalogState(
            configured = true,
            moods = curation.moods,
            studios = curation.studios,
            chronologies = curation.chronologies,
        )
    }

    /**
     * Окрема сторінка каталогу: сервіс, хронологія або франшиза.
     *
     * Три різні джерела під одним контрактом навмисно: на екрані це той самий список рядків
     * постерів, і три майже однакові екрани розійшлися б у поведінці фокуса.
     */
    suspend fun listing(
        kind: CatalogListingKind,
        key: String,
        limit: Int = SectionRowsRepository.BRAND_LIMIT,
    ): CatalogListing = when (kind) {
        CatalogListingKind.STUDIO -> studioListing(key, limit)
        CatalogListingKind.CHRONOLOGY -> chronologyListing(key)
        CatalogListingKind.FRANCHISE -> franchiseListing(key.toIntOrNull())
        CatalogListingKind.COLLECTION -> collectionListing(key)
        CatalogListingKind.UKRAINIAN -> ukrainianListing()
        CatalogListingKind.NETWORK -> networkListing(key)
    }

    /** Мовники з живими джерелами — сервер порожніх не віддає, тож фільтрувати нічого. */
    suspend fun networks(): List<CatalogNetwork> = withContext(Dispatchers.IO) {
        val base = config.streamsUrl() ?: return@withContext emptyList()
        runCatching {
            val body = httpGet("$base/api/networks", NETWORK_TIMEOUT_MS) ?: return@runCatching emptyList()
            val array = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val name = json.optString("network").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CatalogNetwork(
                    name = name,
                    titles = json.optInt("titles"),
                    // Порожньо означає «не знаємо», і джерел цього два: TMDB країни не
                    // дав, або назва мовника неоднозначна (NBC — і американська National
                    // Broadcasting, і японська Nagasaki). Ні те, ні те не підстава
                    // ховати канал, тому за порожньою країною не відсіваємо.
                    country = json.optString("country").uppercase(),
                )
            }
        }.onFailure { Log.w(TAG, "Мовники не читаються", it) }.getOrDefault(emptyList())
    }

    private suspend fun networkListing(network: String): CatalogListing = withContext(Dispatchers.IO) {
        val base = config.streamsUrl()
            ?: return@withContext CatalogListing(message = context.getString(R.string.catalog_msg_no_server_address))
        runCatching {
            val body = httpGet("$base/api/networks?network=${encode(network)}", NETWORK_TIMEOUT_MS)
                ?: return@runCatching CatalogListing(message = context.getString(R.string.catalog_msg_server_no_reply))
            val json = JSONObject(body)
            val array = json.optJSONArray("results")
            val items = (0 until (array?.length() ?: 0)).mapNotNull { index ->
                val row = array?.optJSONObject(index) ?: return@mapNotNull null
                val tmdbId = row.optInt("tmdb_id").takeIf { it > 0 } ?: return@mapNotNull null
                CatalogItem(
                    tmdbId = tmdbId,
                    kind = TitleKind.fromTmdb(row.optString("kind")),
                    title = row.optString("title"),
                    year = row.optInt("year").takeIf { it > 0 }?.toString().orEmpty(),
                    posterPath = row.optString("poster_path").takeIf { it.isNotBlank() },
                )
            }
            CatalogListing(
                title = json.optString("network").ifBlank { network },
                subtitle = context.getString(R.string.catalog_subtitle_channel),
                rows = if (items.isEmpty()) emptyList() else listOf(
                    CatalogRow(
                        id = "network",
                        title = "",
                        // Ряд уже наповнений сервером, тож дозапитувати нічого: `source` і
                        // `path` тут порожні саме тому, що це не запит до TMDB.
                        source = "likhtar",
                        path = "",
                        params = emptyMap(),
                        items = items,
                    ),
                ),
                message = if (items.isEmpty()) context.getString(R.string.catalog_msg_network_empty) else null,
            )
        }.onFailure { Log.w(TAG, "Мовник $network не читається", it) }
            .getOrDefault(CatalogListing(message = context.getString(R.string.catalog_msg_network_failed)))
    }

    /** Склад франшизи для таба на сторінці матеріалу: частини в порядку виходу. */
    suspend fun franchiseParts(collectionId: Int): List<CatalogItem> =
        tmdb.collection(collectionId)?.parts.orEmpty()

    override suspend fun withLogo(item: CatalogItem): CatalogItem {
        // «Двері» (франшиза, хронологія): tmdbId — це id колекції, а не фільму, і лого
        // за ним було б чужим. Своє лого сервер кладе сам.
        if (item.logoPath != null || item.opensListing != null) return item
        val logo = tmdb.logo(item)
        return if (logo != null) item.copy(logoPath = logo) else item
    }

    /**
     * Кадр, опис і лого для карток, що прийшли без них. «Закладки» з сервера несуть лише
     * id, назву й постер — горизонтальна картка ряду стояла порожньою, без кадру й лого.
     */
    suspend fun withArt(items: List<CatalogItem>): List<CatalogItem> = coroutineScope {
        items.map { item ->
            async {
                val full = if (item.backdropPath == null) {
                    tmdb.card(item.tmdbId, item.kind)?.let { card ->
                        item.copy(
                            backdropPath = card.backdropPath,
                            posterPath = item.posterPath ?: card.posterPath,
                            overview = item.overview.ifBlank { card.overview },
                            rating = if (item.rating > 0) item.rating else card.rating,
                        )
                    } ?: item
                } else {
                    item
                }
                withLogo(full)
            }
        }.map { it.await() }
    }

    suspend fun attachLogos(items: List<CatalogItem>): List<CatalogItem> = coroutineScope {
        items.map { item ->
            async { withLogo(item) }
        }.map { it.await() }
    }

    /**
     * Рядок типової добірки: спершу наш каталог, і лише потім TMDB.
     *
     * Порядок саме такий, бо йдеться не про смак, а про склад: у каталозі 2214 придатних
     * фільмів і 1074 серіали, і сервер віддає ряд, у якому кожна картка вже відкривається
     * українською. Двадцятка `discover` після того самого фільтра давала одиниці — це і
     * був ряд «Топи» з пʼяти карток. Коли сервер ряду не знає (або його немає), лишається
     * TMDB — так «Нове на підписках» і живе далі.
     */
    /**
     * Ряд від сервера разом із його анотацією: які позиції мають українську доріжку.
     *
     * Ряд приходить ГОТОВИМ — світовий каталог TMDB, упорядкований нашими шарами. Клієнт
     * його більше не перефільтровує: саме друга фільтрація на клієнті й робила ряди
     * порожніми, щойно позиції не було в НАШІЙ базі.
     */
    suspend fun serverRow(collection: WatchCollection): SectionRow? = runCatching {
        sectionRows.cinema(onlyPlayable()).firstOrNull { it.key == collection.title }
    }.getOrNull()

    /** Налаштування людини: показувати лише те, що можна ввімкнути. */
    /**
     * Готові ряди Кінозалу в тому порядку, у якому їх віддає сервер.
     *
     * Порядок і склад екрана живуть на сервері, а не в enum на клієнті: підбірки
     * стрімінгів там, і на чужій інсталяції вони приїдуть ті самі.
     */
    suspend fun cinemaSection(): List<SectionRow> = withoutAnime(sectionRows.cinema(onlyPlayable()))

    /**
     * «Показувати аніме» вимкнено — аніме немає ніде: ні в героїні, ні в топах, ні в рядах
     * (раніше ховався лише ряд «Аніме»). Ряд «Аніме» при цьому лишається порожнім і
     * не показується.
     */
    private suspend fun withoutAnime(rows: List<SectionRow>): List<SectionRow> =
        if (settings.settings.first().showAnime) rows else rows.map { row -> row.copy(items = row.items.filterNot { it.anime }) }

    /** Плитки стрімінгів у кінці Кінозалу — тією ж відповіддю, що й ряди. */
    /**
     * Неігрове — документальне, реаліті, ток-шоу, новини, музика: там українська бажана, а не
     * обов'язкова. За id жанрів TMDB, а не назвами: назви — мовою інтерфейсу, і «Документальний»
     * в англійському лаунчері тихо не впізнавався б. «Реаліті» — власна мітка сервера для шоу
     * порталів (поза TMDB), тому лишається назвою.
     */
    suspend fun isUnscripted(item: CatalogItem): Boolean =
        "Реаліті" in item.genres || tmdb.genreIds(item.genres).any { it in UnscriptedGenreIds }

    suspend fun cinemaTiles(): List<SectionBrand> = sectionRows.cinemaBrands(onlyPlayable())

    suspend fun cinemaGenres(): List<SectionGenre> = sectionRows.cinemaGenres(onlyPlayable())

    /**
     * «Топ-10» Кінозалу — щоразу іншого сервісу: випадковий із тих, чию
     * плитку сервер позначив `top`, ряд сторінки з позначкою `top`. Топ буває порожнім
     * (SkyShowtime його не віддає) — тоді наступний сервіс, не більше трьох спроб.
     * `null` — топу немає ні в кого.
     */
    suspend fun cinemaTop(): SectionRow? =
        cinemaTiles().filter { it.top }.shuffled().take(3).firstNotNullOfOrNull { brand ->
            withoutAnime(sectionRows.cinemaBrandRows(brand.key, limit = 10)).firstOrNull { it.top && it.items.isNotEmpty() }
        }

    private suspend fun onlyPlayable(): Boolean =
        !settings.settings.first().showWithoutUaAudio && config.ukrainianAudience()

    suspend fun collection(collection: WatchCollection): List<CatalogItem> = runCatching {
        val fromUs = serverRow(collection)?.items
        if (!fromUs.isNullOrEmpty()) return@runCatching attachLogos(fromUs)
        val items = tmdb.discover(collection.path, collection.params, ROW_LIMIT)
        attachLogos(items)
    }.getOrDefault(emptyList())

    /** «Відкрити всі» з рядка Кінозалу: той самий запит, тільки на повну сітку. */
    /**
     * «Відкрити всі» для ряду «Суто українське».
     *
     * Ряд його не мав: сторінки для нього просто не існувало, бо він єдиний у Кінозалі
     * збирається не з одного запиту TMDB, а зі злиття курації з нашим каталогом. Тепер
     * сторінка будується ТИМ САМИМ методом, лише з більшою стелею.
     */
    private suspend fun ukrainianListing(): CatalogListing {
        val rows = ukrainianRows(LISTING_LIMIT)
        return CatalogListing(
            title = rows.firstOrNull()?.title ?: context.getString(R.string.catalog_ukrainian_title),
            subtitle = context.getString(R.string.catalog_subtitle_catalog),
            rows = rows,
            message = if (rows.isEmpty()) context.getString(R.string.catalog_msg_no_ukrainian) else null,
        )
    }

    /** «Відкрити всі» для серверного ряду, якого немає в enum: та сама стрічка, довша. */
    private suspend fun serverRowListing(title: String): CatalogListing {
        val row = runCatching {
            sectionRows.cinemaRow(title, onlyPlayable(), SectionRowsRepository.OPEN_ALL_LIMIT)
        }.getOrNull() ?: return CatalogListing(message = context.getString(R.string.catalog_msg_no_collection))
        // Ряд уже наповнений сервером — дозапитувати нічого.
        val full = CatalogRow(
            id = row.key, title = row.title, source = "likhtar", path = "", params = emptyMap(),
            items = attachLogos(row.items), keepOrder = row.keep,
        )
        return CatalogListing(title = row.title, subtitle = context.getString(R.string.catalog_subtitle_catalog), rows = openAllSections(full, name = ::partName), sectioned = true)
    }

    /**
     * «Відкрити всі» ряду зі сторінки: `row|<вид сторінки>|<ключ>|<id ряду>`. Сторінка
     * збирається тим самим шляхом і віддає ряд цілим — сітка показує все, що в ряду лежить
     * за десятою карткою.
     */
    private suspend fun rowListing(key: String): CatalogListing? {
        val parts = key.split('|', limit = 4)
        if (parts.size != 4 || parts[0] != "row") return null
        val kind = CatalogListingKind.fromRoute(parts[1]) ?: return null
        val row = listing(kind, parts[2], SectionRowsRepository.OPEN_ALL_LIMIT).rows.firstOrNull { it.id == parts[3] }
            ?: return CatalogListing(message = context.getString(R.string.catalog_msg_no_collection))
        return CatalogListing(title = row.title, subtitle = context.getString(R.string.catalog_subtitle_catalog), rows = openAllSections(row, name = ::partName), sectioned = true)
    }

    private suspend fun collectionListing(key: String): CatalogListing {
        rowListing(key)?.let { return it }
        val collection = WatchCollection.byKey(key)
        // Ряди Кінозалу тепер приходять із сервера й свого ключа в enum не мають: назва
        // ряду і є ключем. Сітка тоді — той самий ряд, лише довший.
            ?: return serverRowListing(key)
        // Те саме джерело, що в ряду: сторінка «Відкрити всі» не має показувати інше, ніж
        // ряд, з якого в неї зайшли.
        val ours = runCatching {
            sectionRows.cinemaRow(collection.title, onlyPlayable())
        }.getOrNull()?.items
        val items = if (!ours.isNullOrEmpty()) {
            // Логотипи назв — окремий запит на картку, і він потрібен сітці так само, як
            // ряду: інакше «Відкрити всі» виглядає інакше за ряд, з якого в неї зайшли.
            attachLogos(ours)
        } else {
            tmdb.discover(collection.path, collection.params, LISTING_LIMIT)
        }
        return CatalogListing(
            title = collection.title,
            subtitle = context.getString(R.string.catalog_subtitle_catalog),
            rows = listOf(
                CatalogRow(
                    id = collection.key,
                    title = collection.title,
                    source = "tmdb",
                    path = collection.path,
                    params = collection.params,
                    items = items,
                ),
            ),
            message = if (items.isEmpty()) context.getString(R.string.catalog_msg_tmdb_nothing_for, collection.title) else null,
        )
    }

    /**
     * Кандидати на вечір: відповідь на два питання Кінозалу.
     *
     * Не «рекомендації»: беремо чесні зрізи TMDB за настроєм і тривалістю, а причину «чи
     * варто» тягнемо з картки Ліхтаря. Якщо картки ще немає — збираємо рядок із того, що
     * справді знаємо, і нічого не вигадуємо.
     */
    /**
     * Поради бере сервер, а не ми.
     *
     * Правила вже там і дублювати їх не треба: не радить переглянуте цим профілем за
     * пів року, закладки йдуть першими, `time=90` не поверне тригодинний фільм і не
     * поверне тайтл із невідомим хронометражем, дитяче — лише за офіційним рейтингом TMDB.
     * Раніше клієнт ходив у TMDB discover сам і не знав нічого з цього.
     */
    suspend fun candidates(time: WatchTime, mood: WatchMood?, round: Int, format: WatchFormat? = null): List<Candidate> =
        withContext(Dispatchers.IO) {
            val base = config.streamsUrl() ?: return@withContext emptyList()
            runCatching {
                val current = settings.settings.first()
                val profile = current.activeProfileId
                val url = buildString {
                    append(base).append("/api/advisor?limit=").append(CANDIDATES)
                    append("&profile=").append(encode(profile))
                    mood?.let { append("&mood=").append(encode(it.moodRoot)) }
                    // Без формату й з вимкненим аніме — сервер відсіює японську анімацію.
                    (format?.param ?: "noanime".takeIf { !current.showAnime })?.let { append("&format=").append(it) }
                    // «≈ 90 хв» — це фільм на вечір, а не три серії по 40 (було: серіали 43 хв).
                    if (time == WatchTime.NINETY) append("&time=90&kind=movie")
                    if (time == WatchTime.EPISODE) append("&kind=tv")
                }
                val body = httpGet(url, ADVISOR_TIMEOUT_MS) ?: return@runCatching emptyList()
                // `results: null` — сервер нічого не підібрав; це не помилка, а відповідь.
                val array = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
                (0 until array.length()).mapNotNull { index ->
                    val json = array.optJSONObject(index) ?: return@mapNotNull null
                    val tmdbId = json.optInt("tmdb_id").takeIf { it > 0 } ?: return@mapNotNull null
                    val minutes = json.optInt("minutes")
                    val sources = json.optInt("sources")
                    Candidate(
                        item = CatalogItem(
                            tmdbId = tmdbId,
                            kind = TitleKind.fromTmdb(json.optString("kind")),
                            title = json.optString("title"),
                            year = json.optInt("year").takeIf { it > 0 }?.toString().orEmpty(),
                            posterPath = json.optString("poster_path").takeIf { it.isNotBlank() },
                            // Банер поради — широкий кадр; постер у ньому обрізався й не центрувався.
                            backdropPath = json.optString("backdrop_path").takeIf { it.isNotBlank() },
                            overview = json.optString("summary"),
                        ),
                        meta = listOfNotNull(
                            json.optInt("year").takeIf { it > 0 }?.toString(),
                            minutes.takeIf { it > 0 }?.let { context.getString(R.string.catalog_minutes, it) },
                        ).joinToString(" · "),
                        worth = json.optString("worth"),
                        // Чому саме це: жанр із ваших 👍 (сервер, advisor.go).
                        why = json.optString("why"),
                        // Скільки джерел — це і є відповідь на «а є що вмикати».
                        freeOn = sources.takeIf { it > 0 }?.let { context.getString(R.string.catalog_sources_short, it) },
                    )
                }
            }.onFailure { Log.w(TAG, "Порадник не відповів", it) }
                // Збій мережі — виняток, а не «нічого не знайшов»: Пошук має сказати «немає
                // зв'язку», а не радити інший настрій.
                .getOrThrow()
                .let { found -> if (config.ukrainianAudience()) found else localized(found) }
        }

    /**
     * Порадник сервера живе на українській базі карток: назва, опис, постер і «чому» — українською.
     * Не українцю назву, опис і постер беремо з TMDB його мовою, а українські пояснення не
     * показуємо зовсім (напр. «Невразливий» і «Czy warto: Супергеройська…»
     * у польському Пошуку). Перекладати картки — справа сервера; поки їх немає, краще мовчати.
     */
    private suspend fun localized(found: List<Candidate>): List<Candidate> = coroutineScope {
        found.map { c ->
            async {
                val own = runCatching { tmdb.card(c.item.tmdbId, c.item.kind) }.getOrNull()
                c.copy(
                    item = if (own == null) c.item else c.item.copy(
                        title = own.title.ifBlank { c.item.title },
                        posterPath = own.posterPath ?: c.item.posterPath,
                        overview = own.overview,
                    ),
                    worth = "",
                    why = "",
                )
            }
        }.map { it.await() }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /**
     * Одна свіжа стрічка українських фільмів і серіалів, без трендових дублів.
     *
     * [limit] існує заради «Відкрити всі»: сітка має показувати той самий ряд, лише
     * довший. Якби вона будувалась окремим кодом, два списки розійшлися б при першій же
     * правці правил — і людина, яка натиснула «Відкрити всі», побачила б інше кіно.
     */
    suspend fun ukrainianRows(limit: Int = ROW_ITEMS): List<CatalogRow> = coroutineScope {
        if (!config.ukrainianAudience()) return@coroutineScope emptyList()
        val curated = rows.load().rows
            .filter { it.source == UKRAINIAN_SOURCE && it.isFresh() }
        if (curated.isEmpty()) return@coroutineScope emptyList()

        val filled = fill(curated)
        val catalog = runCatching { ukrainian.titles() }.getOrDefault(emptyList())
            // «Показуємо лише те, що справді відкривається»: позиція без джерел — це
            // картка, яка нічого не вмикає.
            .filter { it.sources > 0 && it.tmdbId != null }

        val tmdbItems = filled.flatMap { it.items }.distinctBy { it.id }
        val known = tmdbItems.map { it.tmdbId }.toSet()
        val extra = catalog.asSequence().filter { it.tmdbId !in known }.take(limit).toList()
        val ownCards = extra.map { title -> async { tmdb.card(title.tmdbId!!, title.kind) } }
            .mapNotNull { it.await() }
        val items = (tmdbItems + ownCards)
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<CatalogItem> { it.releaseDate.takeIf { date -> date.length >= 10 } ?: it.year }
                    .thenByDescending { it.year },
            )
            .take(limit)
        if (items.isEmpty()) emptyList() else listOf(
            CatalogRow(
                id = "ukrainian",
                title = context.getString(R.string.catalog_ukrainian_title),
                source = UKRAINIAN_SOURCE,
                path = "",
                params = emptyMap(),
                items = items,
            ),
        )
    }

    /** Ряд новинок — той, що впорядкований датою виходу: саме в нього ллється каталог. */
    private fun CatalogRow.isFresh(): Boolean =
        params["sort_by"].orEmpty().startsWith("primary_release_date") ||
            params["sort_by"].orEmpty().startsWith("first_air_date")

    private suspend fun studioListing(id: String, limit: Int = SectionRowsRepository.BRAND_LIMIT): CatalogListing {
        // «Суто українське» — у подачі стрімінгів: кадр із
        // назвою, великі картки першого ряду, топ із номерами, банер тайтлу, мікс форм.
        // «Для вас» (ряди від 👍) — та сама подача стрімінгу: великі картки, далі мікс форм.
        if (id == UKRAINIAN_FEED || id == FOR_YOU || id == UPCOMING) {
            serviceListing(id, context.getString(SERVER_PAGES.getValue(id)), limit)?.let { return it }
        }
        // Плитка «Жанрів» — сторінкою стрімінга: ряди жанру з сервера.
        if (id.startsWith("genre-")) {
            serviceListing(id, sectionRows.genreTitle(id) ?: context.getString(R.string.catalog_genre), limit)?.let { return it }
        }
        val studio = rows.load().studios.firstOrNull { it.id == id }
            ?: return serverPageListing(id, limit)
        // Сторінка стрімінгу — з рядів сервера: там уже лого, якість і озвучка кожної
        // картки. Курація клієнта лишається запасом, коли сервер не відповів.
        serviceListing(id, studio.title, limit)?.let { return it }
        val filled = fill(studio.rows)
        return CatalogListing(
            title = studio.title,
            subtitle = context.getString(R.string.catalog_subtitle_curation),
            rows = filled,
            message = if (filled.isEmpty()) context.getString(R.string.catalog_msg_tmdb_nothing_for, studio.title) else null,
        )
    }

    /**
     * Сторінка, яку знає лише сервер: «Всі фільми», «Всі серіали», «Суто українське».
     *
     * Досі «Відкрити всі» на серверних рядах шукав ключ у клієнтській курації стрімінгів і
     * відповідав «такого сервісу немає» — сторінку вже зібрано на сервері, а клієнт до неї
     * не доходив.
     */
    /**
     * Номер ряду сервера для курації: сталий `cat`, якщо сервер його дав; позиція — лише
     * для старого сервера, який `cat` ще не шле (жоден ряд не має номера).
     */
    private fun catOf(rows: List<SectionRow>, position: Int): Int =
        if (rows.any { it.cat >= 0 }) rows[position].cat else position

    /**
     * Сторінка стрімінгу в подачі всесвітів: кадр із wordmark сервісу, далі мікс форматів
     *. Перший ряд — великі картки, «В тренді» — топ із номерами,
     * після нього банер одного тайтлу, решта — постери й горизонтальні ряди по черзі.
     */
    private suspend fun serviceListing(
        id: String,
        title: String,
        limit: Int = SectionRowsRepository.BRAND_LIMIT,
    ): CatalogListing? {
        val page = runCatching { sectionRows.cinemaBrandRows(id, limit) }.getOrDefault(emptyList())
            .filter { it.items.isNotEmpty() || it.genreTiles.isNotEmpty() }
        if (page.isEmpty()) return null
        // Сторінку будує сервер за шаблоном: нове, топ, що прибирають,
        // банер, анонси, жанри, підбірки. Ручна курація з картками на вересень пішла: вона
        // старіла, а сервер знає, що на сервісі сьогодні. Тут лише форма кожного ряду.
        val rows = mutableListOf<CatalogRow>()
        // Банера від сервера немає (старий сервер, сторінки жанрів) — ставимо свій, як досі.
        val serverBanner = page.any { it.wide != null }
        var spotlightDone = serverBanner
        page.forEachIndexed { index, row ->
            if (row.genreTiles.isNotEmpty()) {
                rows += CatalogRow(
                    id = "service-$id-genres", title = row.title, source = "server", path = "",
                    params = emptyMap(), layout = RowLayout.GENRES, genres = row.genreTiles,
                )
                return@forEachIndexed
            }
            if (row.wide != null) {
                rows += CatalogRow(
                    id = "service-$id-spotlight", title = "", source = "server", path = "",
                    params = emptyMap(), items = row.items.take(1), layout = RowLayout.SPOTLIGHT, keepOrder = true,
                )
                return@forEachIndexed
            }
            val trend = row.top || "тренд" in row.title.lowercase() || "топ" in row.title.lowercase()
            // Твори порталів мають лише широкі кадри з YouTube і Телепорталу: у вертикальному
            // постері вони обрізались до «…НУЛОГО», а картка без лого лишалась без назви —
            // «шо це, хто це?». Такі ряди — горизонтальні картки з назвою.
            val portal = row.items.count { it.showId.isNotBlank() } * 2 > row.items.size
            // Ряд із підписами на картках («вийде 12 жовтня») — лише горизонтальні картки:
            // постер підпису не має, а в анонсах саме він і є змістом.
            val noted = row.items.any { it.note.isNotBlank() }
            val layout = when {
                index == 0 -> RowLayout.FEATURED
                noted -> RowLayout.DEFAULT
                trend -> RowLayout.TOP
                portal -> RowLayout.DEFAULT
                index % 2 == 1 -> RowLayout.POSTERS
                else -> RowLayout.DEFAULT
            }
            rows += CatalogRow(
                id = "service-$id-$index", title = row.title, source = "server", path = "",
                params = emptyMap(), items = row.items, layout = layout, keepOrder = layout == RowLayout.TOP || row.keep,
            )
            // Банер одного тайтлу — після топу або після третього ряду, один на сторінку.
            if (!spotlightDone && (trend || index == 2)) {
                row.items.filter { it.logoPath != null && it.backdropPath != null && it.overview.isNotBlank() }
                    .randomOrNull()?.let { pick ->
                        spotlightDone = true
                        rows += CatalogRow(
                            id = "service-$id-spotlight", title = "", source = "server", path = "",
                            params = emptyMap(), items = listOf(pick), layout = RowLayout.SPOTLIGHT, keepOrder = true,
                        )
                    }
            }
        }
        return CatalogListing(
            title = title,
            rows = rows,
            backdropPath = if (id == UKRAINIAN_FEED) CatalogListing.NEUTRAL_BACKDROP
            else rotatingPick(id, page.take(3).flatMap { it.items })?.backdropPath,
        )
    }

    private suspend fun serverPageListing(id: String, limit: Int = SectionRowsRepository.BRAND_LIMIT): CatalogListing {
        val page = runCatching { sectionRows.cinemaBrandRows(id, limit) }.getOrDefault(emptyList())
        if (page.isEmpty()) return CatalogListing(message = context.getString(R.string.catalog_msg_page_empty, id))
        return CatalogListing(
            title = SERVER_PAGES[id]?.let(context::getString) ?: page.first().title,
            subtitle = context.getString(R.string.catalog_subtitle_cinema),
            rows = page.map { row ->
                CatalogRow(
                    id = row.key, title = row.title, source = "server", path = "", params = emptyMap(),
                    items = row.items, spotlight = row.spotlight,
                )
            },
        )
    }

    /**
     * Хронологія: назви й роки перетворюються в картки паралельно.
     *
     * Послідовно 34 позиції MCU — це півхвилини очікування на приставці; паралельно це один
     * раунд запитів. Позиції, яких TMDB не знає, просто зникають зі списку.
     */
    private suspend fun chronologyListing(key: String): CatalogListing {
        val chronology = rows.load().chronologies.firstOrNull { it.key == key }
            ?: return CatalogListing(message = context.getString(R.string.catalog_msg_no_chronology))

        val found = coroutineScope {
            chronology.entries.map { entry -> async { tmdb.findMovie(entry.title, entry.year) } }.map { it.await() }
        }
        // Лого — для горизонтальних карток сторінки: кадр і лого замість постера.
        val items = attachLogos(ChronologyMatch.assemble(chronology.entries, found))

        val collections = collectionCards(chronology.collections)
        val timeline = CatalogRow(
            id = "chronology-$key", title = context.getString(R.string.catalog_chronology_timeline), source = "", path = "",
            params = emptyMap(), items = items, numbered = true,
        )
        val collectionsRow = { title: String ->
            CatalogRow(
                id = "chronology-$key-collections", title = title, source = "", path = "",
                params = emptyMap(), items = collections,
            )
        }
        // Сторінка всесвіту — ряди за змістом, як Marvel у Disney+: саги, герої, вулиці,
        // анімація, банери окремих фільмів, а в кінці колекції.
        val rows = if (chronology.page.isNotEmpty()) {
            universeRows(key, chronology.page, timeline, collectionsRow)
        } else {
            val series = chronology.tvQuery?.let { query ->
                val params = query.split("&").mapNotNull { pair ->
                    pair.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
                }.toMap() + ("sort_by" to "popularity.desc")
                attachLogos(tmdb.discover("discover/tv", params, ROW_LIMIT))
            }.orEmpty()
            listOf(
                timeline,
                CatalogRow(
                    id = "chronology-$key-tv", title = context.getString(R.string.catalog_universe_series), source = "", path = "",
                    params = emptyMap(), items = series,
                ),
                collectionsRow(context.getString(R.string.catalog_collections)),
            )
        }.filter { it.items.isNotEmpty() }
        return CatalogListing(
            title = chronology.title,
            rows = rows,
            message = if (items.isEmpty()) context.getString(R.string.catalog_msg_chronology_empty) else null,
            backdropPath = items.firstNotNullOfOrNull { it.backdropPath },
            logoPath = chronology.logoPath,
        )
    }

    /**
     * Ряди кураторської сторінки: тайтли за id — паралельно, кожен один раз.
     *
     * Той самий опис і для сторінки всесвіту, і для сторінки стрімінгу; у стрімінгу
     * `server` ставить на своє місце ряд сервера, а `worlds` — картки всесвітів і колекцій.
     */
    private suspend fun universeRows(
        key: String,
        page: List<UniverseRow>,
        timeline: CatalogRow?,
        collectionsRow: (String) -> CatalogRow?,
        serverRows: List<SectionRow> = emptyList(),
    ): List<CatalogRow> = coroutineScope {
        val wanted = page.flatMap { it.items }.distinct()
        val cards = wanted.map { (kind, id) -> async { (kind to id) to tmdb.card(id, kind) } }
            .mapNotNull { job -> job.await().let { (k, v) -> v?.let { k to it } } }.toMap()
        val withLogos = attachLogos(cards.values.toList()).associateBy { it.kind to it.tmdbId }
        // Банери — щоразу інший тайтл із кандидатів ряду, і два банери сторінки не
        // повторюють один одного.
        val spotlightTaken = mutableSetOf<Pair<TitleKind, Int>>()
        val worlds = page.flatMap { it.pages }.distinct()
            .map { ref -> async { ref to pageCard(ref) } }
            .mapNotNull { job -> job.await().let { (k, v) -> v?.let { k to it } } }.toMap()
        page.mapIndexedNotNull { index, row ->
            when (row.layout) {
                "chronology" -> timeline?.copy(title = row.title.ifBlank { timeline.title })
                "collections" -> collectionsRow(row.title.ifBlank { context.getString(R.string.catalog_collections) })
                "worlds" -> CatalogRow(
                    id = "universe-$key-$index", title = row.title, source = "", path = "",
                    params = emptyMap(), items = row.pages.mapNotNull { worlds[it] }, keepOrder = true,
                )
                "server" -> serverRows.withIndex().firstOrNull { catOf(serverRows, it.index) == row.serverIndex }?.value?.let { server ->
                    CatalogRow(
                        id = "universe-$key-$index", title = server.title, source = "server", path = "",
                        params = emptyMap(), items = server.items,
                        layout = if (row.title == "top") RowLayout.TOP else RowLayout.DEFAULT,
                        keepOrder = row.title == "top" || server.keep,
                    )
                }
                else -> CatalogRow(
                    id = "universe-$key-$index",
                    title = row.title,
                    source = "", path = "", params = emptyMap(),
                    items = if (row.layout == "spotlight") {
                        listOfNotNull(
                            row.items.filter { it !in spotlightTaken && withLogos[it] != null }
                                .randomOrNull()?.also { spotlightTaken += it }?.let { withLogos[it] },
                        )
                    } else {
                        row.items.mapNotNull { withLogos[it] }
                    },
                    keepOrder = true,
                    layout = when (row.layout) {
                        "featured" -> RowLayout.FEATURED
                        "spotlight" -> RowLayout.SPOTLIGHT
                        "posters" -> RowLayout.POSTERS
                        "top" -> RowLayout.TOP
                        else -> RowLayout.DEFAULT
                    },
                )
            }
        }
    }

    /** Картка сторінки: `u:mcu` — всесвіт із курації, `c:119` — колекція TMDB. */
    private suspend fun pageCard(ref: String): CatalogItem? {
        val (kind, key) = ref.split(":", limit = 2).takeIf { it.size == 2 } ?: return null
        return when (kind) {
            "c" -> key.toIntOrNull()?.let { collectionCards(listOf(it)).firstOrNull() }
            "u" -> {
                val world = rows.load().chronologies.firstOrNull { it.key == key } ?: return null
                val art = world.collections.firstOrNull()
                CatalogItem(
                    tmdbId = art ?: 0,
                    kind = TitleKind.MOVIE,
                    title = world.title,
                    year = "",
                    posterPath = art?.let { tmdb.textlessCollectionPoster(it) },
                    logoPath = world.logoPath,
                    opens = "chronology:$key",
                )
            }
            else -> null
        }
    }

    /**
     * Картки колекцій для кінця сторінки всесвіту — ті самі, що в ряду «Франшизи й
     * всесвіти»: арт без напису, лого, «КОЛЕКЦІЯ». Клік веде на сторінку колекції.
     */
    private suspend fun collectionCards(ids: List<Int>): List<CatalogItem> = coroutineScope {
        ids.map { id ->
            async {
                val collection = tmdb.collection(id) ?: return@async null
                val name = collection.name.substringBefore(" | ")
                CatalogItem(
                    tmdbId = id,
                    kind = TitleKind.MOVIE,
                    title = name,
                    year = "",
                    posterPath = tmdb.textlessCollectionPoster(id) ?: collection.parts.firstNotNullOfOrNull { it.posterPath },
                    backdropPath = collection.backdropPath,
                    logoPath = franchiseLogo(name, collection.parts),
                    opens = "franchise:$id",
                )
            }
        }.mapNotNull { it.await() }
    }

    private suspend fun franchiseListing(collectionId: Int?): CatalogListing {
        if (collectionId == null) return CatalogListing(message = context.getString(R.string.catalog_msg_unknown_franchise))
        val (name, backdrop, bare) = tmdb.collection(collectionId)
            ?: return CatalogListing(message = context.getString(R.string.catalog_msg_franchise_failed))
        val parts = attachLogos(bare)

        return CatalogListing(
            // «Гаррі Поттер | Колекція»: слово «колекція» голова сторінки пише сама.
            title = name.substringBefore(" | ").ifBlank { context.getString(R.string.catalog_franchise) },
            subtitle = context.getString(R.string.catalog_franchise_subtitle),
            backdropPath = backdrop ?: parts.firstNotNullOfOrNull { it.backdropPath },
            logoPath = franchiseLogo(name.substringBefore(" | "), parts),
            rows = if (parts.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    CatalogRow(
                        id = "franchise-$collectionId",
                        title = context.getString(R.string.catalog_franchise_parts, parts.size),
                        source = "",
                        path = "",
                        params = emptyMap(),
                        items = parts,
                        numbered = true,
                    ),
                )
            },
            message = if (parts.isEmpty()) context.getString(R.string.catalog_msg_franchise_empty) else null,
        )
    }

    /**
     * Лого франшизи — лого її фільму: того, що зветься так само («Дюна», «Шрек»), інакше
     * першої частини. Своїх лого в колекцій TMDB немає.
     */
    private suspend fun franchiseLogo(name: String, parts: List<CatalogItem>): String? {
        val norm = { t: String -> t.lowercase().filter(Char::isLetterOrDigit) }
        val part = parts.firstOrNull { norm(it.title) == norm(name) } ?: parts.firstOrNull() ?: return null
        return tmdb.logo(part)
    }

    /** Наповнення рядків паралельне: їх десятки, і послідовно це були б десятки секунд. */
    private suspend fun fill(definitions: List<CatalogRow>): List<CatalogRow> = coroutineScope {
        definitions
            .map { row -> async { row.copy(items = tmdb.discover(row.path, row.params, ROW_LIMIT)) } }
            .map { it.await() }
            .filter { it.items.isNotEmpty() }
    }

    /** Повні дані сторінки фільму: TMDB, картка Ліхтаря і варіанти відкриття — паралельно. */
    /** Серії сезону — прямий проброс до TMDB, кеш живе в клієнта сторінки. */
    suspend fun seasonEpisodes(item: CatalogItem, season: Int): List<Episode> =
        tmdb.seasonEpisodes(item, season)

    suspend fun details(item: CatalogItem): TitleDetails = coroutineScope {
        val detailsTask = async { tmdb.details(item) }
        val providersTask = async { tmdb.watchProviders(item) }
        val cardTask = async { cards.card(item) }

        val base = detailsTask.await().copy(
            card = cardTask.await(),
            providerNames = providersTask.await(),
        )
        // Черга сервера — після деталей: назву для тайтлів поза каталогом він бере з
        // `original`, а в маршрут сторінки приходить лише тип і TMDB id.
        val resolved = resolver.watch(base.item)
        base.copy(
            watchOptions = watchOptions.resolve(base, resolved),
            dubStudios = resolver.dubStudiosOf(base.item),
            scan = resolver.scanOf(base.item),
        )
    }

    override suspend fun search(query: String): List<CatalogItem> = tmdb.search(query, SEARCH_LIMIT)

    override suspend fun canAsk(): Boolean = !com.likhtar.home.core.model.Distribution.store || ownAi.ready()

    // Публічна збірка: Ліхтарик говорить AI самої людини, без нашого сервера.
    override fun ask(query: String): Flow<AskEvent> =
        if (com.likhtar.home.core.model.Distribution.store) ownAi.ask(query) else tmdb.ask(query)

    override suspend fun advise(like: List<String>): Advice =
        if (com.likhtar.home.core.model.Distribution.store) ownAi.advise(like) else tmdb.advise(like)

    override suspend fun shelves(profile: String): List<Advice> = tmdb.shelves(profile)

    /**
     * Низ Ліхтарика для нової людини: без історії й закладок особистих рядів немає, і сторінка
     * закінчувалась порадником. Тренди тижня TMDB — відкриті дані, без сервера.
     */
    override suspend fun starters(): List<SearchShelf> = coroutineScope {
        listOf(
            R.string.catalog_starter_movies to "trending/movie/week",
            R.string.catalog_starter_tv to "trending/tv/week",
        ).map { (title, path) ->
            async { SearchShelf(context.getString(title), runCatching { tmdb.discover(path, emptyMap(), ROW_LIMIT) }.getOrDefault(emptyList()), emptySet()) }
        }.awaitAll().let { shelves ->
            if (settings.settings.first().showAnime) shelves else shelves.map { it.copy(items = it.items.filterNot(CatalogItem::anime)) }
        }.filter { it.items.isNotEmpty() }
    }

    // Переказ у публічній збірці — теж моделлю людини: шлюз /api/recap більше не для публіки.
    suspend fun recap(tmdbId: Int, season: Int): Pair<String, String> =
        if (com.likhtar.home.core.model.Distribution.store) ownAi.recap(tmdbId, season) else tmdb.recap(tmdbId, season)

    suspend fun recapVideos(tmdbId: Int, season: Int): List<TitleVideo> =
        if (com.likhtar.home.core.model.Distribution.store) emptyList() else tmdb.recapVideos(tmdbId, season)

    // Український трейлер — лише українському інтерфейсу: англійцю першим ішов «Кришталеве Озеро |
    // Український трейлер».
    suspend fun uaTrailer(tmdbId: Int, kind: TitleKind): TitleVideo? =
        if (config.ukrainianAudience()) tmdb.uaTrailer(tmdbId, kind) else null

    private fun partName(part: OpenAllPart): String = context.getString(
        when (part) {
            OpenAllPart.THIS_WEEK -> R.string.catalog_part_this_week
            OpenAllPart.LAST_WEEK -> R.string.catalog_part_last_week
            OpenAllPart.EARLIER -> R.string.catalog_part_earlier
            OpenAllPart.MONTH_END -> R.string.catalog_part_month_end
            OpenAllPart.LATER -> R.string.catalog_part_later
            OpenAllPart.MOVIES -> R.string.catalog_page_films
            OpenAllPart.SERIES -> R.string.catalog_page_series
        },
    )

    private companion object {
        /** Група курації з українськими рядами в `likhtar-rows.json`. */
        const val UKRAINIAN_SOURCE = "Українською"

        /** Скільки карток лишаємо в ряду: рівно стільки, скільки показує сам ряд. */
        const val ROW_ITEMS = 20

        const val ROW_LIMIT = 20
        const val CANDIDATES = 10

        /** Порадник рахує правила на сервері, тож відповідає не миттєво. */
        const val ADVISOR_TIMEOUT_MS = 15_000
        const val NETWORK_TIMEOUT_MS = 12_000
        const val TAG = "LikhtarAdvisor"
        const val LISTING_LIMIT = 60

        const val UKRAINIAN_FEED = "ukrainian_feed"
        const val FOR_YOU = "for_you"
        const val UPCOMING = "upcoming"

        /** Назви серверних сторінок Кінозалу за їхнім ключем. */
        val SERVER_PAGES = mapOf(
            "films" to R.string.catalog_page_films,
            "series" to R.string.catalog_page_series,
            "ukrainian_feed" to R.string.catalog_ukrainian_title,
            "for_you" to R.string.catalog_page_for_you,
            "anime" to R.string.catalog_page_anime,
            "upcoming" to R.string.catalog_page_upcoming,
        )
        val FREE_OFFERS = setOf("FREE", "ADS")
        const val SEARCH_LIMIT = 40
    }

    /**
     * Без ключа TMDB. У своїй збірці — куди його вписати; у публічній розділу «Підключення» поза
     * Україною немає, і підказка вела в глухий кут — там чесно: каталог недоступний.
     */
    private fun noKeyMessage(): String = context.getString(
        if (com.likhtar.home.core.model.Distribution.store) R.string.catalog_msg_offline else R.string.catalog_msg_no_tmdb_key,
    )

}

/** TMDB: документальний (99), музика (10402), новини (10763), реаліті (10764), ток-шоу (10767). */
private val UnscriptedGenreIds = setOf(99, 10402, 10763, 10764, 10767)
