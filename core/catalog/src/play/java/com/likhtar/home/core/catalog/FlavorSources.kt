package com.likhtar.home.core.catalog

// Збірка для магазину: лише офіційні сервіси, сторонніх шукачів і сайтів немає.
internal val UNIVERSAL_SEARCH = emptyList<WatchOptionsResolver.Target>()
internal val FLAVOR_SITE_NAMES = emptyMap<String, String>()
internal val FLAVOR_VARIANT_SITES = emptySet<String>()
internal val LAST_TIER_SITE: String? = null
internal const val FLAVOR_ROWS_QUERY = ""

/** Свій підпис варіанта для джерел збірки; `null` — звичайний «сайт · якість». */
internal fun flavorOptionLabel(source: String, name: String, rank: Int?): String? = null

/** Праворуч у рядку озвучки для кількох варіантів зі збірки; `null` — звичайний підпис. */
internal fun flavorRankNote(sources: Int, best: Int?): String? = null
