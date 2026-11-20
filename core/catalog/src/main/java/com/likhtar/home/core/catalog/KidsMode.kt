package com.likhtar.home.core.catalog

/** Жанри TMDB, яких немає в дитячому режимі: жахи, трилер. */
private val KIDS_BLOCKED = setOf(27, 53)

/**
 * Дитячий режим (Nottilo «Modalità famiglia»): тайтл у рядах лишається, якщо серед його жанрів немає
 * жахів і трилерів. Вікових сертифікатів у рядах немає — їх враховує лише порадник сервера ([advisorKidsParam]).
 * Без жанрів — лишаємо: невідоме не ховаємо.
 */
fun kidsSafe(genreIds: Set<Int>): Boolean = genreIds.none { it in KIDS_BLOCKED }

/** Порадник сервера в дитячому режимі відсіює за віковими сертифікатами (`kidsCerts` на сервері). */
fun advisorKidsParam(kidsMode: Boolean): String = if (kidsMode) "&kids=1" else ""

/** Літерні рейтинги, які в дитячому режимі можна: до PG‑13 / TV‑14 (Nottilo — «fino al PG‑13»). */
private val KIDS_LETTERS = setOf("G", "PG", "PG-13", "U", "12A", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG", "TV-14")

/** Найстарший вік у числових рейтингах (UA «12+», DE «12»), що ще можна. */
private const val KIDS_MAX_AGE = 12

/** Країни рейтингу за пріоритетом — як у сервера (`certOrder` на сервері). */
private val CERT_ORDER = listOf("UA", "US", "GB")

/** Віковий рейтинг тайтлу з усіх країн TMDB: Україна, США, Британія, далі будь-який непорожній. */
fun pickCertification(byCountry: Map<String, String>): String? =
    CERT_ORDER.firstNotNullOfOrNull { byCountry[it]?.trim()?.takeIf(String::isNotEmpty) }
        ?: byCountry.values.firstNotNullOfOrNull { it.trim().takeIf(String::isNotEmpty) }

/**
 * Чи тайтл у дитячому режимі: рейтинг відомий — він і вирішує (серіали-жахи в TMDB мають жанри
 * «драма, детектив», а не «жахи», — «Крістал Лейк»), і жанр усе одно не жахи/трилер;
 * рейтингу немає — лише жанри ([kidsSafe]).
 */
fun kidsAllowed(certification: String?, genreIds: Set<Int>): Boolean {
    if (!kidsSafe(genreIds)) return false
    val cert = certification?.trim()?.uppercase()?.takeIf(String::isNotEmpty) ?: return true
    cert.removeSuffix("+").toIntOrNull()?.let { return it <= KIDS_MAX_AGE }
    return cert in KIDS_LETTERS
}

/**
 * Картка в дитячому режимі: сторінка франшизи чи всесвіту ([CatalogItem.opens]) — ні, бо її id не
 * тайтл і рейтинг узявся б чужий («Матриця» R проходила); тайтли всередині перевіряються
 * поштучно на самій сторінці. Решта — [kidsAllowed].
 */
fun kidsCard(item: CatalogItem, certification: String?, genreIds: Set<Int>): Boolean =
    item.opens.isEmpty() && kidsAllowed(certification, genreIds)
