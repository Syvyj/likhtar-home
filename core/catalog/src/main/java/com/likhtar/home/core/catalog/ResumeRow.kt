package com.likhtar.home.core.catalog

import com.likhtar.home.core.catalog.media.LibraryResume
import com.likhtar.home.core.catalog.media.MediaLibrary
import android.content.Context
import com.likhtar.home.core.data.AppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.model.LikhtarSection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Картка ряду «Продовжити»: недодивлене або канал, який зараз в ефірі. */
data class ResumeCard(
    val title: String,
    val subtitle: String,
    val progress: Float? = null,
    val live: Boolean = false,
    val section: LikhtarSection,
    /** Широкий кадр або обкладинка: «Продовжити» завжди читається як кіно, не як меню. */
    val imageUrl: String? = null,
    /** Логотип назви замість тексту; `null` — назва текстом. */
    val logoUrl: String? = null,
    /** Не `null` — картка не веде в розділ, а вмикає наступну серію просто звідси. */
    val continueItem: ContinueItem? = null,
    /**
     * Не `null` — картка вмикається в чужому застосунку (MEGOGO).
     *
     * Своєї сторінки для неї немає й не буде: ми знаємо про цей перегляд лише те, що
     * сказав сервіс, — назву, кадр і чим відкрити.
     */
    val launch: LaunchAction? = null,
    /** Не `null` — картка веде на сторінку тайтла («Вийшла українською», радар). */
    val openTitle: CatalogItem? = null,
    /** Не `null` — ext-ключ проєкту Реаліті: картка веде на сторінку шоу. */
    val showKey: String? = null,
) {
    /** Ключ для LazyList і кешу превʼю: дублікатів у ряду немає за побудовою. */
    val id: String get() = "${section.name}-$title-$subtitle"
}

/**
 * Ряд «Продовжити перегляд» — один для всього застосунку.
 *
 * Головна, повна історія і Кінозал показують один список: раніше Кінозал будував свій
 * із системної таблиці WatchNext (те, що пишуть сторонні застосунки), і два ряди з
 * однаковою назвою вели на різне — Головна вмикала нашу наступну серію, Кінозал
 * відкривав чужий застосунок.
 *
 * Один тайтл — одна картка. Сервер віддає окремий рядок на кожен tmdb_id, і той
 * самий серіал приїжджав двічі: у полиці на три місця дублікат зʼїдав чуже.
 */
fun resumeCards(
    watching: List<ContinueItem>,
    /**
     * Те, що людина дивиться в MEGOGO. Ряд один на застосунок, тож і ці позиції — у ньому:
     * окремий ряд «Продовжити на MEGOGO» був би саме тим сервісним рядом, проти якого
     * заперечували.
     */
    services: List<ServiceResume> = emptyList(),
    /** Підпис картки нашої позиції — [ContinueItem.episodeLabel] мовою інтерфейсу. */
    subtitle: (ContinueItem) -> String,
    /** Недодивлене на сервері медіатеки (Jellyfin) — веде на сторінку тайтла. */
    library: List<LibraryResume> = emptyList(),
): List<ResumeCard> {
    // Одна шкала часу на обидва джерела: сервер виводить, коли дивились у MEGOGO, з того,
    // як міняється його порядок. Раніше тут було чергування «наше, MEGOGO, наше» — дат
    // MEGOGO не мав, і кожна друга картка була чужою, хоч би коли її дивились.
    // Шоу й матчі MEGOGO — у ряд реаліті (showResumeCards), як і наші.
    val ours = watching.distinctBy { it.title to subtitle(it) }.map { it.updatedAt to it.card(LikhtarSection.CINEMA, subtitle(it)) }
    val theirs = services.filterNot { it.show }.map { it.updatedAt to it.card(LikhtarSection.CINEMA) }
    // Тайтл, який уже є в нашій історії, другою карткою не дублюємо.
    val known = watching.map { it.tmdbId to it.kind }.toSet()
    val shelf = library.filterNot { (it.item.tmdbId to it.item.kind) in known }.distinctBy { it.item.id }
        .map { it.playedAt to it.card() }
    return byTime(ours + theirs + shelf)
}

/** Стабільно, від найсвіжішого: позиції MEGOGO без часу (0) — у хвості, у його ж порядку. */
private fun byTime(cards: List<Pair<Long, ResumeCard>>) = cards.sortedByDescending { it.first }.map { it.second }

private fun ContinueItem.card(section: LikhtarSection, subtitle: String) = ResumeCard(
    title = title,
    subtitle = subtitle,
    progress = progress.takeUnless { finished },
    section = section,
    imageUrl = TmdbImages.backdropThumb(backdropPath) ?: TmdbImages.poster(posterPath),
    logoUrl = TmdbImages.logo(logoPath),
    continueItem = this,
)

private fun LibraryResume.card() = ResumeCard(
    title = item.title,
    subtitle = if (file.season > 0) String.format(java.util.Locale.US, "S%02dE%02d", file.season, file.episode) else item.year.take(4),
    progress = progress,
    section = LikhtarSection.CINEMA,
    imageUrl = TmdbImages.backdropThumb(item.backdropPath) ?: TmdbImages.poster(item.posterPath),
    openTitle = item,
)

private fun ShowContinue.card() = ResumeCard(
    title = title,
    subtitle = episodeLabel,
    progress = progress.takeIf { it > 0f },
    section = LikhtarSection.REALITY,
    imageUrl = TmdbImages.backdropThumb(image.ifBlank { null }),
    showKey = extKey,
)

/**
 * Повна історія: усе, що дивились, — кіно, шоу, випуски порталів і MEGOGO — однією
 * шкалою часу. Ті самі картки, що в рядах «Продовжити», лише без відбору.
 */
fun historyCards(
    watched: List<ContinueItem>,
    shows: List<ShowContinue>,
    services: List<ServiceResume>,
    subtitle: (ContinueItem) -> String,
): List<ResumeCard> = byTime(
    watched.distinctBy { it.title to subtitle(it) }.map { it.updatedAt to it.card(LikhtarSection.CINEMA, subtitle(it)) } +
        shows.map { it.updatedAt to it.card() } +
        services.map { it.updatedAt to it.card(if (it.show) LikhtarSection.REALITY else LikhtarSection.CINEMA) },
)

/**
 * Ряди «Продовжити» й історія для всіх екранів — один шлях за даними.
 *
 * Досі кожен екран питав сервер сам і по-своєму: Кінозал без `kind=cinema` (шоу з TMDB
 * лізли в кіно), Реаліті без MEGOGO й без шоу з TMDB, історія — чергуванням. Порядок
 * «жив своїм життям». Тепер відбір і порядок — тут, і лише тут.
 */
@Singleton
class ResumeRows @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchProgress: WatchProgressRepository,
    private val showProgress: ShowProgressRepository,
    private val services: ServiceContinueRepository,
    private val apps: AppWatchNext,
    private val media: MediaLibrary,
) {
    /** Чужі застосунки: MEGOGO з акаунта + системний WatchNext (Netflix, YouTube…). */
    private suspend fun megogo() = runCatching { services.megogo() }.getOrDefault(emptyList()) +
        runCatching { apps.programs() }.getOrDefault(emptyList())

    suspend fun cinema(limit: Int = ROW_MAX): List<ResumeCard> =
        resumeCards(watchProgress.continueWatching(limit, kind = "cinema"), megogo(), ::label, runCatching { media.resume() }.getOrDefault(emptyList()))

    private fun label(item: ContinueItem) = item.episodeLabel(context)

    suspend fun reality(limit: Int = ROW_MAX): List<ResumeCard> = showResumeCards(
        shows = runCatching { showProgress.continueShows(limit) }.getOrDefault(emptyList()),
        tmdbShows = watchProgress.continueWatching(limit, kind = "show"),
        services = megogo(),
        subtitle = ::label,
    )

    suspend fun history(limit: Int = HISTORY_MAX): List<ResumeCard> = historyCards(
        watched = watchProgress.continueWatching(limit, all = true),
        shows = runCatching { showProgress.continueShows(limit) }.getOrDefault(emptyList()),
        services = megogo(),
        subtitle = ::label,
    )

    companion object {
        const val ROW_MAX = 20
        const val HISTORY_MAX = 50
    }
}

/**
 * «Продовжити» відкриває сторінку тайтла, а серіал — одразу на серіях, з фокусом на
 * серії, яку продовжувати (досі вмикався плеєр, і вибрати іншу якість чи
 * озвучку було ніде). Сторінка забирає позначку сама (TitleViewModel) — так не треба
 * протягувати новий параметр через усі екрани й навігацію.
 */
object OpenOnEpisodes {
    @Volatile private var tmdbId = 0

    fun request(id: Int) { tmdbId = id }

    /** Чи просили відкрити [id] на серіях; позначка одноразова. */
    fun consume(id: Int): Boolean = (tmdbId == id && id != 0).also { if (it) tmdbId = 0 }
}

/**
 * Куди веде картка «Продовжити» — однаково на кожному екрані: наша позиція — сторінка
 * тайтла (серіал — на серіях, див. [OpenOnEpisodes]), решта — за тим, що про картку
 * відомо. Однаково на всіх екранах, і компаньйон пошуку теж.
 */
fun ResumeCard.open(
    onShow: (String) -> Unit,
    onTitle: (CatalogItem) -> Unit,
    onLaunch: (ResumeCard) -> Unit,
    onSection: (LikhtarSection) -> Unit,
) {
    val item = continueItem
    val key = showKey
    val title = openTitle
    when {
        // Наступна серія ще не вийшла — вмикати нічого: сторінка тайтла, там дата й серії.
        item != null && item.nextAirDate.isNotBlank() -> onTitle(item.asCatalogItem())
        item != null -> {
            if (item.kind == TitleKind.TV) OpenOnEpisodes.request(item.tmdbId)
            onTitle(item.asCatalogItem())
        }
        key != null -> onShow(key)
        title != null -> onTitle(title)
        launch != null -> onLaunch(this)
        else -> onSection(section)
    }
}

private fun ServiceResume.card(section: LikhtarSection) = ResumeCard(
    title = title,
    subtitle = label,
    progress = progress,
    section = section,
    imageUrl = imageUrl,
    logoUrl = TmdbImages.logo(logoPath),
    launch = LaunchAction(packageName = packageName, uri = deeplink),
)

/**
 * Спільний запуск «Продовжити»: Головна, повна історія і Кінозал вмикають серію одним шляхом.
 * Повертає `false`, коли джерела немає — позицію тоді варто перечитати чи прибрати.
 */
@Singleton
class ContinuePlayback @Inject constructor(
    private val watchProgress: WatchProgressRepository,
    private val streams: LikhtarStreams,
    private val settingsRepository: SettingsRepository,
    private val playback: PlaybackController,
) {
    /** Прогрес після плеєра записано — ряд «Продовжити» варто перечитати. */
    val saved get() = playback.saved

    private val _preparing = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Картка шукає джерело — екран показує PreparingScrim. Один стан на всі екрани. */
    val preparing: kotlinx.coroutines.flow.StateFlow<Boolean> = _preparing

    suspend fun play(item: ContinueItem): Boolean {
        // Уже шукаємо джерело — друге OK не запускає ще одну картку: екран очікування
        // пульт не тримає, і натискання проходило в сусідню («Зникла» замість «Матерії»).
        if (_preparing.value) return true
        _preparing.value = true
        return try {
            start(item)
        } finally {
            _preparing.value = false
        }
    }

    private suspend fun start(item: ContinueItem): Boolean {
        val played = watchProgress.play(item) ?: return false
        val player = settingsRepository.settings.first().videoPlayerPackage
        val playlist = if (item.kind == TitleKind.TV) {
            streams.playlistUrl(item.title, item.originalTitle, item.year, item.season, item.nextEpisode, tmdbId = item.tmdbId)
        } else {
            null
        }
        // Продовжуємо саме з того місця, де плеєр зупинився минулого разу.
        val resumeMs = watchProgress.positionOf(
            tmdbId = item.tmdbId,
            kind = item.kind,
            season = item.season,
            episode = item.nextEpisode,
        )
        // Плеєрам без m3u (Just+) — сама серія й черга `video_list`, як зі сторінки тайтла.
        // Без них Just+ отримував плейлист як відео й казав «Потік пошкоджено».
        // Усі джерела сезону — щоб у плеєрі були інші озвучки серії (панель «Якість» у
        // Just+), а наступні серії йшли з того самого сайту, що й стартова (інакше
        // з «Продовжити» в плеєрі був лише стандартний набір якостей).
        val all = runCatching { streams.streams(item.asCatalogItem(), item.season.takeIf { it > 0 }) }
            .getOrDefault(emptyList()).filter { it.url.startsWith("http") }
        val host = runCatching { java.net.URL(played.url).authority.orEmpty() }.getOrDefault("")
        val queue = playlist?.let { streams.playlistEntries(it) }.orEmpty().map { e ->
            if (e.episode == item.nextEpisode) e.copy(url = played.url)
            else all.firstOrNull { it.episode == e.episode && it.source == host && host.isNotBlank() }
                ?.let { e.copy(url = it.url) } ?: e
        }
        // Фільм: ще й роздачі, яких TorrServer не знає, — інакше 4K, що ніхто не вмикав
        // (Толока), у панелі «Якість» не з'являвся.
        val cold = if (item.kind == TitleKind.TV) emptyList() else streams.coldTorrents(
            item.asCatalogItem(),
            (all.mapNotNull { torrentRefOf(it.url) } + listOfNotNull(torrentRefOf(played.url))).toSet(),
        )
        val dvFirst = DolbyVision.supported
        val variants = episodeVariants(all + cold, item.nextEpisode, played.url, dvFirst)
        val queueVariants = if (queue.size > 1) queue.map { e -> episodeVariants(all, e.episode, e.url, dvFirst) } else emptyList()
        playback.play(
            PlayRequest(
                uri = playlist ?: played.url,
                single = played.url,
                queue = queue,
                variants = variants,
                queueVariants = queueVariants,
                mime = if (playlist != null) "audio/x-mpegurl" else "video/*",
                packageName = player,
                resumeMs = resumeMs,
                title = PlayingTitle(
                    item = item.asCatalogItem(),
                    season = item.season,
                    episode = item.nextEpisode,
                ),
                ref = played.ref,
                source = played.source,
                quality = played.quality,
            ),
        )
        return true
    }
}

/** Картка чужого застосунку: вмикаємо там, де воно лежить. `false` — нема чим відкрити. */
fun ResumeCard.openIn(apps: AppsRepository): Boolean {
    val action = launch ?: return false
    val uri = action.uri ?: return false
    return apps.launchIntentUri(uri, action.packageName)
}

/** Шоу, на яких зупинились: випуски порталів, шоу з TMDB і MEGOGO — ряд «Продовжити реаліті». */
fun showResumeCards(
    shows: List<ShowContinue>,
    tmdbShows: List<ContinueItem> = emptyList(),
    services: List<ServiceResume> = emptyList(),
    subtitle: (ContinueItem) -> String,
): List<ResumeCard> = byTime(
    shows.map { it.updatedAt to it.card() } +
        tmdbShows.distinctBy { it.title to subtitle(it) }.map { it.updatedAt to it.card(LikhtarSection.REALITY, subtitle(it)) } +
        services.filter { it.show }.map { it.updatedAt to it.card(LikhtarSection.REALITY) },
)

/** Ext-ключ проєкту → id сторінки шоу; `null` — нашої сторінки в нього немає. */
fun realityRouteId(extKey: String, serial: Boolean = false): String? = when {
    extKey.startsWith("teleportal:") ->
        (if (serial) "serials/" else "show/") + extKey.removePrefix("teleportal:")
    extKey.startsWith("1plus1:") -> "plus1/" + extKey.removePrefix("1plus1:")
    // Твір YouTube-книги: картка «Продовжити» відкриває ту саму сторінку, що й ряд.
    extKey.startsWith("nashe:") -> "youtube/" + extKey.removePrefix("nashe:")
    else -> null
}

/**
 * Інші джерела тієї самої серії для плеєра: підпис → адреса, поточне — першим. Джерело 2 — у
 * кінці (той самий ярус, що й на сервері). Порожньо — вибору немає, панель лишиться штатною.
 */
fun variantLabel(raw: String): String {
    val parts = cleanStreamLabel(raw).split(" · ").map { it.trim() }
        .filter { it.isNotEmpty() && !QUALITY_PART.matches(it) && !EPISODE_PART.matches(it) }
    val site = parts.indexOfFirst { it in VARIANT_SITES }
    return (if (site >= 0) parts.take(site + 1) else parts).joinToString(" · ").ifBlank { raw }
}

private val QUALITY_PART = Regex("""\d{3,4}p""")
private val EPISODE_PART = Regex("""Серія \d+""")
private val VARIANT_SITES = setOf("Торент", "роздача", "сайт студії") + FLAVOR_VARIANT_SITES

internal fun episodeVariants(
    all: List<StreamOption>,
    episode: Int,
    currentUrl: String,
    dolbyVisionFirst: Boolean = true,
): List<Pair<String, String>> {
    val same = all.filter { episode == 0 || it.episode == episode }
    if (same.none { it.url != currentUrl }) return emptyList()
    // Підпис — чия озвучка й звідки: «DniproFilm · Джерело», «Джерело 2». Якість у панелі Just+
    // і так стоїть рядками вище, «Серія 5» і назва серії — шум.
    val label = { o: StreamOption -> variantLabel(o.label) }
    val current = same.firstOrNull { it.url == currentUrl }
    val rest = same.filter { it.url != currentUrl }
        .sortedWith(compareBy<StreamOption> { LAST_TIER_SITE?.let(it.label::contains) == true }.thenByDescending { bestFirstKey(it.label, dolbyVisionFirst) })
    // Адреса з /api/play може не збігтися з адресою списку — тоді поточне першим окремо.
    val head = current?.let { label(it) to it.url } ?: ("Зараз" to currentUrl)
    return (listOf(head) + rest.map { label(it) to it.url }).take(12)
}
