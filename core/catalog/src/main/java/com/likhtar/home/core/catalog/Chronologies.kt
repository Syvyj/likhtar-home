package com.likhtar.home.core.catalog

/**
 * Правила складання хронології з того, що віддав TMDB.
 *
 * Виділено окремо й без мережі, бо це єдине місце, де ми можемо помилитися тихо: пошук за
 * назвою завжди щось знаходить, і без перевірки року «Alien» 1979-го легко став би
 * однойменним фільмом іншого десятиліття.
 */
object ChronologyMatch {
    /** Дати прем'єр у різних країнах не збігаються, тому рік-два розбіжності прощаємо. */
    const val YEAR_TOLERANCE = 2

    fun accepts(entry: ChronologyEntry, found: CatalogItem): Boolean {
        if (entry.year <= 0) return true
        val year = found.year.toIntOrNull() ?: return false
        return kotlin.math.abs(year - entry.year) <= YEAR_TOLERANCE
    }

    /**
     * Зшиває позиції з результатами пошуку, зберігаючи порядок хронології.
     *
     * `found` — результат для кожної позиції в тому самому порядку; `null` означає, що TMDB
     * нічого не віддав. Повтори прибираються: у серії перезапусків різні назви нерідко
     * ведуть на той самий фільм, і подвійна картка виглядала б як помилка списку.
     */
    fun assemble(entries: List<ChronologyEntry>, found: List<CatalogItem?>): List<CatalogItem> {
        val seen = mutableSetOf<Int>()
        return entries.zip(found).mapNotNull { (entry, item) ->
            item?.takeIf { accepts(entry, it) && seen.add(it.tmdbId) }
        }
    }
}
