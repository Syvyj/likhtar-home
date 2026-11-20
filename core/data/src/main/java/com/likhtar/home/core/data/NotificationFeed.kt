package com.likhtar.home.core.data

/** `NotificationManager.IMPORTANCE_*` — без Android, щоб фільтр перевірявся юніт-тестом. */
const val IMPORTANCE_LOW = 2
const val IMPORTANCE_DEFAULT = 3

/** Те, що фільтру треба знати про системне сповіщення. */
data class NotificationFacts(
    val pkg: String,
    val ongoing: Boolean,
    val media: Boolean,
    val groupSummary: Boolean,
    val importance: Int,
    val title: String,
    val text: String,
    /** `isClearable`: чого не прибрати, те стан пристрою («Ready to Cast»), а не новина. */
    val clearable: Boolean,
)

/**
 * Чи показувати сповіщення в панелі Ліхтаря (lounge `NotificationFeed`): без постійних (завантаження,
 * служби) й неприбиральних, медіа (це плашка «Що грає»), групових підсумків (дублюють дітей), тихіших за LOW,
 * своїх і схованих застосунків, порожніх.
 */
fun showInFeed(n: NotificationFacts, ownPkg: String, muted: Set<String>): Boolean =
    n.clearable && !n.ongoing && !n.media && !n.groupSummary && n.importance >= IMPORTANCE_LOW &&
        n.pkg != ownPkg && n.pkg !in muted && (n.title.isNotBlank() || n.text.isNotBlank())
