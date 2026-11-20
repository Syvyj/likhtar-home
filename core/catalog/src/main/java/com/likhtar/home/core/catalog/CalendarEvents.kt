package com.likhtar.home.core.catalog

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class EventKind { EPISODE, ONLINE, CINEMA }

/** Подія календаря: серія мого серіалу чи вихід фільму із закладок. [date] — ISO, без години. */
data class CalendarEvent(
    val date: String,
    val item: CatalogItem,
    val kind: EventKind,
    val season: Int = 0,
    val episode: Int = 0,
    val state: ShowState = ShowState.NONE,
)

/** Вікно календаря: тиждень назад і місяць наперед, обидва краї включно. */
fun window(today: String): Pair<String, String> = shift(today, -WINDOW_BACK) to shift(today, WINDOW_AHEAD)

/** Сім ISO-дат тижня, у якому [today], — з понеділка. */
fun weekDays(today: String): List<String> {
    val cal = calendarOf(today)
    // SUNDAY = 1 … SATURDAY = 7; понеділок — перший день тижня.
    val back = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val monday = shift(today, -back)
    return (0 until 7).map { shift(monday, it) }
}

/** Серії сезону [season] з датою у [from]…[to]; кинутий серіал — без подій. */
fun episodeEvents(show: TrackedShow, episodes: List<Episode>, season: Int, from: String, to: String): List<CalendarEvent> {
    if (show.state == ShowState.DROPPED) return emptyList()
    return episodes.filter { it.airDate.length >= 10 && it.airDate in from..to }
        .map { CalendarEvent(it.airDate, show.item, EventKind.EPISODE, season, it.number, show.state) }
}

/** Вихід фільму у [from]…[to]: тип 4 — онлайн, 3 — у кіно. */
fun movieEvents(item: CatalogItem, releases: List<Pair<Int, String>>, from: String, to: String): List<CalendarEvent> =
    releases.filter { it.second in from..to }.mapNotNull { (type, date) ->
        when (type) {
            4 -> CalendarEvent(date, item, EventKind.ONLINE)
            3 -> CalendarEvent(date, item, EventKind.CINEMA)
            else -> null
        }
    }

/** Події по днях: дні за датою; у дні — серії, потім онлайн, потім кіно, далі за назвою. */
fun byDay(events: List<CalendarEvent>): List<Pair<String, List<CalendarEvent>>> =
    events.groupBy { it.date }.toSortedMap().map { (day, list) ->
        day to list.sortedWith(compareBy<CalendarEvent>({ it.kind.ordinal }, { it.item.title }, { it.season }, { it.episode }))
    }

private const val WINDOW_BACK = 7
private const val WINDOW_AHEAD = 30

// SimpleDateFormat, а не java.time: minSdk 24. Новий на кожен виклик — він не потокобезпечний.
private fun format() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private fun calendarOf(iso: String): Calendar = Calendar.getInstance().apply { time = format().parse(iso)!! }

private fun shift(iso: String, days: Int): String {
    val cal = calendarOf(iso)
    cal.add(Calendar.DAY_OF_YEAR, days)
    return format().format(cal.time)
}
