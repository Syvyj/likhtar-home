package com.likhtar.home.core.catalog

/** Де людина в серіалі — для плашки під постером «Моїх серіалів». */
enum class ShowState { DROPPED, NEW, WATCHING, WAITING_EPISODE, WAITING_SEASON, DONE, NONE }

/**
 * Серіал у трекері. [season]/[episode]: для NEW — нова серія, для WATCHING — наступна до перегляду,
 * для WAITING_EPISODE — та, що вийде. [watched]/[total] — у сезоні наступної до перегляду.
 * [date] — ISO-дата: для WAITING_EPISODE — коли вийде, для NEW/WATCHING — коли вийшла остання.
 */
data class TrackedShow(
    val item: CatalogItem,
    val state: ShowState,
    val season: Int = 0,
    val episode: Int = 0,
    val watched: Int = 0,
    val total: Int = 0,
    val date: String = "",
    /** Остання вийшла серія — для «Переглянуто до SxEy» (не наступна, що ще не вийшла). */
    val lastSeason: Int = 0,
    val lastEpisode: Int = 0,
)

/**
 * Статус серіалу з розкладу, прогресу й позначки — перше правило, що підходить.
 *
 * [reached] — (сезон, наступна до перегляду) з автопрогресу (лише домашня збірка). Позначка
 * «переглянуто до SxEy» дає те саме; з двох береться далі. Про озвучку нічого не стверджуємо.
 */
fun trackShow(schedule: ShowSchedule, reached: Pair<Int, Int>?, mark: TitleMark?, today: String): TrackedShow {
    val item = schedule.item
    if (mark?.mark == "hide") return TrackedShow(item, ShowState.DROPPED)
    if (mark?.mark == "watched" && mark.scope == "all") return TrackedShow(item, ShowState.DONE)

    // Позначка сезоном — це «до останньої серії сезону».
    val byMark = when {
        mark?.mark != "watched" || mark.season <= 0 -> null
        mark.scope == "season" -> mark.season to (schedule.seasonSizes[mark.season] ?: 0) + 1
        mark.episode > 0 -> mark.season to mark.episode + 1
        else -> null
    }
    val at = listOfNotNull(reached, byMark).map { schedule.normalize(it) }.maxWithOrNull(compareBy({ it.first }, { it.second }))
    val next = schedule.next?.takeIf { it.airDate >= today }

    if (at == null) {
        return if (next != null) {
            TrackedShow(item, ShowState.WAITING_EPISODE, next.season, next.episode, date = next.airDate, lastSeason = schedule.last?.season ?: 0, lastEpisode = schedule.last?.episode ?: 0)
        } else {
            TrackedShow(item, ShowState.NONE)
        }
    }
    val (season, episode) = at
    val total = schedule.seasonSizes[season] ?: 0
    val last = schedule.last
    val ls = last?.season ?: 0
    val le = last?.episode ?: 0
    if (last != null && (last.season to last.episode).isAtOrAfter(at)) {
        // «Нова» — вийшло недавно (вікно дзвіночка), хай навіть цілий сезон одразу; давній хвіст — «Дивлюсь».
        val fresh = last.airDate >= shiftIso(today, -NEW_WINDOW_DAYS)
        val state = if (fresh) ShowState.NEW else ShowState.WATCHING
        return TrackedShow(item, state, season, episode, episode - 1, total, date = last.airDate, lastSeason = ls, lastEpisode = le)
    }
    return when {
        next != null -> TrackedShow(item, ShowState.WAITING_EPISODE, next.season, next.episode, date = next.airDate, lastSeason = ls, lastEpisode = le)
        schedule.status == "Returning Series" -> TrackedShow(item, ShowState.WAITING_SEASON, lastSeason = ls, lastEpisode = le)
        else -> TrackedShow(item, ShowState.DONE, lastSeason = ls, lastEpisode = le)
    }
}

/** S2E11 при десяти серіях у сезоні — це S3E1. */
private fun ShowSchedule.normalize(at: Pair<Int, Int>): Pair<Int, Int> {
    val size = seasonSizes[at.first] ?: return at
    return if (size > 0 && at.second > size) at.first + 1 to 1 else at
}

private fun Pair<Int, Int>.isAtOrAfter(other: Pair<Int, Int>): Boolean =
    first > other.first || (first == other.first && second >= other.second)

/** Вікно «нового» — те саме, що в дзвіночку ([NewEpisodes.WINDOW_DAYS]). */
private const val NEW_WINDOW_DAYS = 14

private fun shiftIso(iso: String, days: Int): String = runCatching {
    val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val c = java.util.Calendar.getInstance().apply { time = f.parse(iso)!!; add(java.util.Calendar.DAY_OF_YEAR, days) }
    f.format(c.time)
}.getOrDefault(iso)
