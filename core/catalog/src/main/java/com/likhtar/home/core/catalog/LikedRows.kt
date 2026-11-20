package com.likhtar.home.core.catalog

/** Скільки вподобаного пам'ятаємо на приставці — для рядів вистачає найсвіжішого. */
const val LIKED_MAX = 20

/** Скільки рядів «Бо сподобалось» у Кінозалі — за найсвіжішими like. */
const val LIKED_ROWS = 2

/** Менше схожого — ряду немає: два-три постери виглядають як помилка, а не добірка. */
private const val LIKED_ROW_MIN = 4
private const val LIKED_ROW_MAX = 20

/**
 * Список вподобаного (like у «Позначити»): найсвіжіше — першим, без повторів (фільм і серіал з
 * одним id — різні), не довший за [LIKED_MAX]. Сервер списку позначок не віддає (лише одну за
 * тайтлом), тож список — на приставці, як «Кинуті» ([TitleMarksRepository.droppedShows]).
 */
fun updateLiked(list: List<CatalogItem>, item: CatalogItem, liked: Boolean): List<CatalogItem> {
    val rest = list.filterNot { it.tmdbId == item.tmdbId && it.kind == item.kind }
    return (if (liked) listOf(item) + rest else rest).take(LIKED_MAX)
}

/**
 * Картки ряду «Бо сподобалось «[source]»» з рекомендацій TMDB: без самого тайтлу й без іншого
 * вже вподобаного (його людина бачила), без повторів; замало — порожньо (Nottilo).
 */
fun likedRowItems(source: CatalogItem, recs: List<CatalogItem>, liked: List<CatalogItem>): List<CatalogItem> {
    val seen = (liked + source).map { it.kind to it.tmdbId }.toSet()
    val items = recs.filterNot { (it.kind to it.tmdbId) in seen }.distinctBy { it.kind to it.tmdbId }.take(LIKED_ROW_MAX)
    return if (items.size < LIKED_ROW_MIN) emptyList() else items
}
