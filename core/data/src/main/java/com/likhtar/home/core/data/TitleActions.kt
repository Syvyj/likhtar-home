package com.likhtar.home.core.data

/**
 * Групи кнопок на картці фільму та їхній порядок.
 *
 * Порядок за замовчуванням — відповідь на питання, з яким людина приходить на картку:
 * спершу «дивитись», тоді сервіси, де фільм уже є, і лише тоді решта. Але це смак, а не
 * істина, тому його можна переставити в налаштуваннях Кінозалу.
 *
 * Переставляються **групи**, не окремі кнопки: набір сервісів різний на кожному тайтлі, і
 * порядок усередині них віддає сервер (`order` у `/api/watch`), який бачить і підписки, і
 * наші джерела. Персональний порядок сервісів означав би два правила на одну річ.
 */
object TitleActions {
    /** Головна дія: «Дивитись» або «Продовжити перегляд». */
    const val PLAY = "play"

    /** Пігулки сервісів, де фільм уже є (`/api/watch` плюс відповіді застосунків). */
    const val SERVICES = "services"

    /** «Варіанти» — вибір потоку: якість, озвучка, розмір. */
    const val SOURCES = "sources"

    const val WATCHLIST = "watchlist"

    /** Кнопка франшизи; на картках без франшизи її просто немає. */
    const val FRANCHISE = "franchise"

    val DEFAULT = listOf(PLAY, SERVICES, SOURCES, WATCHLIST, FRANCHISE)

    /**
     * Групи, які ця збірка показує: у збірці для магазину немає власних потоків, тож
     * «Дивитись» і «Джерела» — геть; головні там кнопки сервісів (Distribution).
     */
    fun visible(order: List<String>): List<String> =
        if (com.likhtar.home.core.model.Distribution.store) order - PLAY - SOURCES else order

    /** Назва групи мовою інтерфейсу: `context.getString(labelRes(key))`; 0 — невідомий ключ. */
    fun labelRes(key: String): Int = when (key) {
        PLAY -> R.string.data_action_play
        SERVICES -> R.string.data_action_services
        SOURCES -> R.string.data_action_sources
        WATCHLIST -> R.string.data_action_watchlist
        FRANCHISE -> R.string.data_action_franchise
        else -> 0
    }

    @Deprecated("Завжди українською; для екрана — labelRes(key)")
    fun label(key: String): String = when (key) {
        PLAY -> "Дивитись"
        SERVICES -> "Сервіси, де фільм є"
        SOURCES -> "Варіанти"
        WATCHLIST -> "У список"
        FRANCHISE -> "Франшиза"
        else -> key
    }

    /**
     * Збережений порядок, доповнений тим, чого в ньому немає.
     *
     * Невідомі ключі відкидаємо, забуті — додаємо в кінець: інакше нова кнопка після
     * оновлення застосунку не з'явилася б у того, хто вже переставляв порядок, і виглядало б
     * це як зникла функція.
     */
    fun parse(raw: String?): List<String> {
        val saved = raw?.split(",").orEmpty().map { it.trim() }.filter { it in DEFAULT }
        return (saved + DEFAULT).distinct()
    }

    /** Пересунути ключ на позицію вище. Перший лишається першим — рухати нікуди. */
    fun moveUp(order: List<String>, key: String): List<String> = swap(order, key, -1)

    /** Пересунути ключ на позицію нижче. Останній лишається останнім. */
    fun moveDown(order: List<String>, key: String): List<String> = swap(order, key, 1)

    private fun swap(order: List<String>, key: String, step: Int): List<String> {
        val index = order.indexOf(key)
        val target = index + step
        if (index < 0 || target !in order.indices) return order
        val moved = order.toMutableList()
        moved[index] = moved[target]
        moved[target] = key
        return moved
    }
}
