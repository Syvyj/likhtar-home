package com.likhtar.home.core.catalog

import java.time.LocalDate

/**
 * «Відкрити всі» — не плоска сітка, а маленька сторінка: ряд ділиться на
 * частини за змістом, частини малюються різними формами карток, між першою й другою — банер.
 *
 * - «Нове на X» (дата додавання на картці): цього тижня / минулого тижня / раніше;
 * - «Встигни» («до 19.10»): цього тижня / до кінця місяця / пізніше;
 * - решта: фільми й серіали окремо.
 *
 * Порядок карток усередині частини — той самий, що в ряду.
 */
internal fun openAllSections(
    row: CatalogRow,
    today: LocalDate = LocalDate.now(),
    /** Назва частини мовою інтерфейсу. */
    name: (OpenAllPart) -> String,
): List<CatalogRow> {
    val parts = (byDate(row.items, today) ?: byKind(row.items)).map { (part, items) -> part?.let(name) to items }
    val layouts = listOf(RowLayout.POSTERS, RowLayout.DEFAULT)
    val out = mutableListOf<CatalogRow>()
    parts.forEachIndexed { index, (title, items) ->
        out += row.copy(
            id = "${row.id}~$index",
            title = title ?: row.title,
            items = items,
            layout = if (index == 0) RowLayout.FEATURED else layouts[(index - 1) % layouts.size],
        )
        if (index == 0) {
            // Банер — тайтл цього ж ряду з кадром, лого й описом, але не перша картка.
            row.items.drop(1).firstOrNull { it.logoPath != null && it.backdropPath != null && it.overview.isNotBlank() }
                ?.let { pick ->
                    out += row.copy(id = "${row.id}~banner", title = "", items = listOf(pick), layout = RowLayout.SPOTLIGHT)
                }
        }
    }
    return out
}

private val DAY = Regex("""(\d{2})\.(\d{2})""")

/** Частини сторінки «Відкрити всі»; назви — ресурсами в [CatalogRepository]. */
internal enum class OpenAllPart { THIS_WEEK, LAST_WEEK, EARLIER, MONTH_END, LATER, MOVIES, SERIES }

/** Частини за датою з підпису картки; null — дат у ряду замало, ділимо інакше. */
private fun byDate(items: List<CatalogItem>, today: LocalDate): List<Pair<OpenAllPart?, List<CatalogItem>>>? {
    val dated = items.map { it to dateOf(it.note, today) }
    if (dated.count { it.second != null } * 2 < items.size || items.isEmpty()) return null
    val expiring = items.any { "до " in it.note }
    val names = if (expiring) {
        listOf(OpenAllPart.THIS_WEEK, OpenAllPart.MONTH_END, OpenAllPart.LATER)
    } else {
        listOf(OpenAllPart.THIS_WEEK, OpenAllPart.LAST_WEEK, OpenAllPart.EARLIER)
    }
    val buckets = List(names.size) { mutableListOf<CatalogItem>() }
    for ((item, raw) in dated) {
        val bucket = when {
            raw == null -> names.lastIndex
            expiring -> {
                val date = if (raw.isBefore(today.minusDays(1))) raw.plusYears(1) else raw
                when {
                    !date.isAfter(today.plusDays(7)) -> 0
                    date.month == today.month && date.year == today.year -> 1
                    else -> 2
                }
            }
            else -> {
                val date = if (raw.isAfter(today)) raw.minusYears(1) else raw
                when {
                    !date.isBefore(today.minusDays(7)) -> 0
                    !date.isBefore(today.minusDays(14)) -> 1
                    else -> 2
                }
            }
        }
        buckets[bucket] += item
    }
    return names.zip(buckets).filter { it.second.isNotEmpty() }
}

private fun dateOf(note: String, today: LocalDate): LocalDate? {
    val match = DAY.findAll(note).lastOrNull() ?: return null
    val (day, month) = match.destructured
    return runCatching { LocalDate.of(today.year, month.toInt(), day.toInt()) }.getOrNull()
}

/** Фільми й серіали окремо; одна частина — без власної назви (лишається назва ряду). */
private fun byKind(items: List<CatalogItem>): List<Pair<OpenAllPart?, List<CatalogItem>>> {
    val movies = items.filter { it.kind == TitleKind.MOVIE }
    val shows = items.filter { it.kind != TitleKind.MOVIE }
    if (movies.isEmpty() || shows.isEmpty()) return listOf(null to items)
    return listOf(OpenAllPart.MOVIES to movies, OpenAllPart.SERIES to shows)
}
