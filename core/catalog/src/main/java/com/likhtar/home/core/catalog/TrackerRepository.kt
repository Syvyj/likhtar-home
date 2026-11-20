package com.likhtar.home.core.catalog

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

/**
 * «Мої серіали» й «Цього тижня»: серіали із закладок і з автопрогресу, зі статусом і подіями.
 *
 * Обидві збірки: у публічній прогресу немає (сервер лише домашній) — там «що я дивлюсь» дають
 * закладки й позначки «переглянуто до SxEy» (вони в публічній на приставці). Помилка джерела —
 * порожньо, не виняток: сторінка не чекає й не падає через один серіал.
 */
@Singleton
class TrackerRepository @Inject constructor(
    private val tmdb: TmdbClient,
    private val watchlist: WatchlistRepository,
    private val progress: WatchProgressRepository,
    private val marks: TitleMarksRepository,
    private val config: CatalogConfig,
) {
    @Volatile private var showsCache: Pair<Long, List<TrackedShow>>? = null
    @Volatile private var eventsCache: Pair<Long, Pair<String, List<CalendarEvent>>>? = null

    // Одночасні виклики (Ліхтарик, плитка, календар) не ганяють TMDB двічі: другий чекає на перший.
    private val showsLock = kotlinx.coroutines.sync.Mutex()
    private val eventsLock = kotlinx.coroutines.sync.Mutex()

    suspend fun shows(force: Boolean = false): List<TrackedShow> = showsLock.withLock { loadShows(force) }

    private suspend fun loadShows(force: Boolean): List<TrackedShow> {
        showsCache?.takeIf { !force && fresh(it.first) }?.let { return it.second }
        val watching = runCatching { progress.continueWatching(limit = MAX_SHOWS, all = true, kind = "cinema") }
            .getOrDefault(emptyList()).filter { it.kind == TitleKind.TV }
        val saved = runCatching { watchlist.items.first() }.getOrDefault(emptyList()).filter { it.kind == TitleKind.TV }
        val reached = watching.associate { it.tmdbId to (it.season to it.nextEpisode) }
        val dropped = runCatching { marks.droppedShows() }.getOrDefault(emptyList())
        val items = trackedItems(watching.map { it.asCatalogItem() }, saved, dropped)
        val (schedules, markMap) = coroutineScope {
            val s = items.map { async { runCatching { tmdb.showSchedule(it.tmdbId) }.getOrNull() } }
            val m = items.map { item -> async { item.tmdbId to runCatching { marks.markOf(item) }.getOrNull() } }
            s.awaitAll().filterNotNull() to m.awaitAll().toMap()
        }
        val result = assemble(schedules, reached, markMap, today())
        // Хтось із серіалів не відповів (мережа, TMDB) — не запам'ятовуємо на дві години неповне.
        if (cacheable(items.size, schedules.size)) showsCache = System.currentTimeMillis() to result
        return result
    }

    /**
     * Серії моїх серіалів і виходи фільмів із закладок у [from]…[to]. Тягнемо завжди одне вікно
     * календаря (−7…+30) і з нього фільтруємо: тиждень Ліхтарика й календар більше не витісняють
     * одне одного з кешу й не ганяють TMDB по колу.
     */
    suspend fun events(from: String, to: String, force: Boolean = false): List<CalendarEvent> =
        allEvents(force).filter { it.date in from..to }

    private suspend fun allEvents(force: Boolean): List<CalendarEvent> = eventsLock.withLock {
        val (from, to) = window(today())
        val key = "$from..$to"
        eventsCache?.takeIf { !force && fresh(it.first) && it.second.first == key }?.let { return@withLock it.second.second }
        val shows = shows(force).filter { it.state != ShowState.DROPPED }
        val region = runCatching { config.locale().country }.getOrDefault("UA")
        val movies = runCatching { watchlist.items.first() }.getOrDefault(emptyList()).filter { it.kind == TitleKind.MOVIE }.take(MAX_MOVIES)
        val result = coroutineScope {
            val episodes = shows.map { show ->
                async {
                    // Сезон наступної до перегляду й сезон, що виходить зараз, — інших серій у вікні не буває.
                    val seasons = setOfNotNull(show.season.takeIf { it > 0 }, scheduleSeason(show)).take(2)
                    seasons.flatMap { season ->
                        val eps = runCatching { tmdb.seasonEpisodes(show.item, season) }.getOrDefault(emptyList())
                        episodeEvents(show, eps, season, from, to)
                    }
                }
            }
            val films = movies.map { item ->
                async { movieEvents(item, runCatching { tmdb.releaseDates(item.tmdbId, region) }.getOrDefault(emptyList()), from, to) }
            }
            (episodes + films).awaitAll().flatten().distinctBy { "${it.item.id}:${it.season}:${it.episode}:${it.kind}" }
        }
        eventsCache = System.currentTimeMillis() to (key to result)
        result
    }

    /** «Усі прем'єри»: нові серіали й фільми онлайн у країні контенту. */
    suspend fun premieres(from: String, to: String): List<CalendarEvent> {
        val region = runCatching { config.locale().country }.getOrDefault("UA")
        val dropped = shows().filter { it.state == ShowState.DROPPED }.map { it.item.tmdbId }.toSet()
        return withoutDropped(runCatching { tmdb.premieres(from, to, region) }.getOrDefault(emptyList()).map { (date, item) ->
            CalendarEvent(date, item, if (item.kind == TitleKind.TV) EventKind.EPISODE else EventKind.ONLINE, if (item.kind == TitleKind.TV) 1 else 0, if (item.kind == TitleKind.TV) 1 else 0)
        }, dropped)
    }

    /** Після зміни позначки: статуси й тиждень мають перерахуватись одразу, а не за дві години. */
    fun invalidate() {
        showsCache = null
        eventsCache = null
    }

    private fun scheduleSeason(show: TrackedShow): Int? =
        show.season.takeIf { show.state == ShowState.WAITING_EPISODE || show.state == ShowState.NEW }

    private fun fresh(at: Long) = isFresh(at, System.currentTimeMillis(), TitleMarksRevision.changedAt)

    companion object {
        private const val MAX_SHOWS = 40
        private const val MAX_MOVIES = 20

        /** Свіже — молодше двох годин і новіше за останню зміну позначок (де б її не зробили). */
        internal fun isFresh(at: Long, now: Long, marksChangedAt: Long): Boolean =
            now - at < CACHE_MS && at >= marksChangedAt

        /** Кешуємо лише повну картину: якщо розклад прийшов не для всіх — це збій, а не правда. */
        internal fun cacheable(requested: Int, got: Int): Boolean = got >= requested
        private const val CACHE_MS = 2 * 60 * 60 * 1000L

        private val ORDER = listOf(
            ShowState.NEW, ShowState.WATCHING, ShowState.WAITING_EPISODE, ShowState.WAITING_SEASON,
            ShowState.NONE, ShowState.DONE, ShowState.DROPPED,
        )

        internal fun assemble(
            schedules: List<ShowSchedule>,
            reached: Map<Int, Pair<Int, Int>>,
            marks: Map<Int, TitleMark?>,
            today: String,
        ): List<TrackedShow> = schedules
            .map { trackShow(it, reached[it.item.tmdbId], marks[it.item.tmdbId], today) }
            .sortedWith(
                compareBy<TrackedShow> { ORDER.indexOf(it.state) }.thenComparator { a, b ->
                    // Нові — найсвіжіша перша; «Чекаю серію» — найближча перша.
                    if (a.state == ShowState.NEW) b.date.compareTo(a.date) else a.date.ifBlank { "9999" }.compareTo(b.date.ifBlank { "9999" })
                },
            )

        /** Мої серіали: з прогресу, із закладок і кинуті (щоб їх можна було повернути), без повторів. */
        internal fun trackedItems(watching: List<CatalogItem>, saved: List<CatalogItem>, dropped: List<CatalogItem>): List<CatalogItem> =
            (watching + saved + dropped).distinctBy { it.tmdbId }.take(MAX_SHOWS)

        /** «Усі прем'єри» без того, що людина кинула. */
        internal fun withoutDropped(events: List<CalendarEvent>, dropped: Set<Int>): List<CalendarEvent> =
            events.filterNot { it.item.tmdbId in dropped }

        private fun today(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }
}
