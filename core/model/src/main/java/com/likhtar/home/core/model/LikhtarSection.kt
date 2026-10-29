package com.likhtar.home.core.model

/**
 * Розділи лаунчера у порядку верхнього меню та нижньої панелі на Головній.
 * [route] використовується як маршрут навігації.
 */
enum class LikhtarSection(
    /** Українська назва; для екрана — [titleRes] (мова інтерфейсу). */
    val title: String,
    val route: String,
    val titleRes: Int,
) {
    // Порядок — це порядок верхнього меню: Ліхтарик одразу за Головною.
    HOME("Головна", "home", R.string.model_section_home),
    SEARCH("Ліхтарик", "search", R.string.model_section_search),
    CINEMA("Кінозал", "cinema", R.string.model_section_cinema),
    LIVE_TV("ТВ Ефір", "livetv", R.string.model_section_live_tv),
    REALITY("Реаліті", "reality", R.string.model_section_reality),
    SMART_HOME("Смарт дім", "smarthome", R.string.model_section_smart_home),
    GAMES("Ігри", "games", R.string.model_section_games),
    APPS("Застосунки", "apps", R.string.model_section_apps),
    SETTINGS("Налаштування", "settings", R.string.model_section_settings);

    companion object {
        fun fromRoute(route: String?): LikhtarSection? = entries.firstOrNull { it.route == route }

        /**
         * [sections] у порядку людини ([order] — маршрути). Головна завжди перша, Налаштування
         * останні; розділ, якого немає в [order] (новий у лаунчері), стає за своїм сусідом
         * за замовчуванням — у кінець перед Налаштуваннями.
         */
        fun ordered(sections: List<LikhtarSection>, order: List<String>): List<LikhtarSection> {
            if (order.isEmpty()) return sections
            fun rank(s: LikhtarSection) = when (s) {
                HOME -> -1
                SETTINGS -> Int.MAX_VALUE
                else -> order.indexOf(s.route).let { if (it < 0) Int.MAX_VALUE - 1 else it }
            }
            return sections.sortedBy(::rank)
        }
    }
}

/**
 * «Пошук» з доку Головної відкриває сторінку Ліхтарика одразу з фокусом у полі пошуку;
 * картка «Ліхтарик» — просто сторінку. Прапорець, а не аргумент маршруту:
 * верхнє меню й Back розпізнають розділ за чистим `route`, і параметр зламав би обидва.
 * Сторінка знімає прапорець, щойно прочитала.
 */
object SearchLaunch {
    @Volatile
    var focusField: Boolean = false

    /** «Закладки» з Кінозалу: ряд закладок — першим і у фокусі. */
    @Volatile
    var focusBookmarks: Boolean = false

    /** Готовий запит із Головної («Ліхтарик підкаже»): Ліхтарик одразу відповідає на нього. */
    @Volatile
    var query: String? = null
}


/**
 * Розділ Налаштувань, на якому їх відкрити: «Додати плейлист» з ТВ Ефіру веде в «ТВ», а не
 * на «Головну». Ім'я розділу `SettingsSection`; Налаштування забирають його один раз.
 */
object SettingsTarget {
    @Volatile var section: String? = null

    fun take(): String? = section.also { section = null }
}
