package com.likhtar.home.core.model

/**
 * Встановлений застосунок, показаний лаунчером.
 *
 * [packageName] + [activityName] однозначно ідентифікують точку запуску (leanback- або
 * звичайна launcher-активність). [bannerUri] / [iconUri] — `android.resource://`-посилання
 * на ресурси застосунку, які вміє завантажити Coil. [glyph] і [accent] лишаються для mock-даних
 * і як запасний вигляд, коли застосунок не має ні банера, ні іконки.
 */
data class LauncherApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val bannerUri: String? = null,
    val iconUri: String? = null,
    val glyph: String = "",
    val accent: Long = 0xFF9AA6AC,
    /** Застосунок сам оголосив себе відео-застосунком (`ApplicationInfo.CATEGORY_VIDEO`). */
    val isVideo: Boolean = false,
    /** Категорія, як її визначила система; користувач може перевизначити (див. [AppStates]). */
    val category: AppCategory = AppCategory.OTHER,
) {
    val id: String get() = "$packageName/$activityName"

    /** Перше, що варто спробувати намалювати: банер TV, інакше звичайна іконка. */
    val artworkUri: String? get() = bannerUri ?: iconUri
}

/**
 * Група, у яку застосунок потрапляє на сторінці «Застосунки».
 *
 * Навмисно чотири й без дрібніших: на сітці з пульта кожен зайвий заголовок — це ще один
 * ↓ між користувачем і застосунком. «Приховані» — не категорія, а окремий стан: приховане
 * не показується взагалі.
 */
enum class AppCategory(
    /** Українська назва; для екрана — [titleRes] (мова інтерфейсу). */
    val title: String,
    val titleRes: Int,
) {
    MEDIA("Кіно та відео", R.string.model_category_media),
    GAMES("Ігри", R.string.model_category_games),
    TOOLS("Інструменти", R.string.model_category_tools),
    OTHER("Інше", R.string.model_category_other),
    ;

    companion object {
        fun fromName(value: String?): AppCategory? = entries.firstOrNull { it.name == value }
    }
}

/** Локальний стан застосунку, керований користувачем зі сторінки «Застосунки». */
data class AppState(
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val pinnedToCinema: Boolean = false,
    val pinnedToLiveTv: Boolean = false,
    val pinnedToHome: Boolean = false,
    /** Категорія, яку користувач призначив вручну; `null` — лишається системна. */
    val category: AppCategory? = null,
)

/** Застосунок разом з його локальним станом. */
data class LauncherAppWithState(
    val app: LauncherApp,
    val state: AppState = AppState(),
)

/**
 * Стани всіх застосунків у вигляді множин ідентифікаторів — саме так вони лежать у сховищі,
 * тому додавання нового прапорця не змінює схему даних.
 */
data class AppStates(
    val hidden: Set<String> = emptySet(),
    val locked: Set<String> = emptySet(),
    val pinnedToCinema: Set<String> = emptySet(),
    val pinnedToLiveTv: Set<String> = emptySet(),
    /** Док Головної: YouTube і компанія за два натискання, а не через сторінку «Застосунки». */
    val pinnedToHome: Set<String> = emptySet(),
    /** Ручні категорії: id застосунку → категорія. Порожньо — усе за системними даними. */
    val categories: Map<String, AppCategory> = emptyMap(),
) {
    fun stateOf(id: String): AppState = AppState(
        hidden = id in hidden,
        locked = id in locked,
        pinnedToCinema = id in pinnedToCinema,
        pinnedToLiveTv = id in pinnedToLiveTv,
        pinnedToHome = id in pinnedToHome,
        category = categories[id],
    )
}

/** Категорія, у якій застосунок реально показується: ручна перемагає системну. */
val LauncherAppWithState.effectiveCategory: AppCategory
    get() = state.category ?: app.category
