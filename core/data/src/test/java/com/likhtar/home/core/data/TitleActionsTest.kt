package com.likhtar.home.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Порядок кнопок на картці. Логіка дрібна, але ламається тихо: помилка тут не падає, а
 * прибирає кнопку з екрана або втрачає збережений вибір.
 */
class TitleActionsTest {

    @Test
    fun `забуті ключі додаються в кінець, невідомі відкидаються`() {
        // Так виглядає збережений порядок після оновлення, яке додало нову кнопку.
        val parsed = TitleActions.parse("sources,play")

        assertEquals(listOf("sources", "play"), parsed.take(2))
        // Решта на місці — інакше нова кнопка «зникла» б у того, хто вже переставляв порядок.
        assertEquals(TitleActions.DEFAULT.size, parsed.size)
        assertEquals(TitleActions.DEFAULT.toSet(), parsed.toSet())
        // Ключ, якого більше немає в застосунку, до екрана не доходить.
        assertEquals(TitleActions.DEFAULT.size, TitleActions.parse("play,вигаданий").size)
    }

    @Test
    fun `порожнє й зіпсоване значення дають порядок за замовчуванням`() {
        assertEquals(TitleActions.DEFAULT, TitleActions.parse(null))
        assertEquals(TitleActions.DEFAULT, TitleActions.parse(""))
        assertEquals(TitleActions.DEFAULT, TitleActions.parse(",,,"))
    }

    @Test
    fun `рух униз міняє місцями саме сусідів`() {
        val order = listOf("play", "services", "sources", "watchlist")

        assertEquals(
            listOf("play", "sources", "services", "watchlist"),
            TitleActions.moveDown(order, "services"),
        )
        // Останній лишається останнім: стрілка вниз у нього неактивна, але й логіка не мусить
        // покладатися на UI.
        assertEquals(order, TitleActions.moveDown(order, "watchlist"))
    }

    @Test
    fun `рух угору міняє місцями саме сусідів`() {
        val order = listOf("play", "services", "sources", "watchlist")

        assertEquals(
            listOf("play", "sources", "services", "watchlist"),
            TitleActions.moveUp(order, "sources"),
        )
        // Перший лишається першим: рухати нікуди, і список не мусить «прокручуватись».
        assertEquals(order, TitleActions.moveUp(order, "play"))
        // Невідомий ключ нічого не ламає.
        assertEquals(order, TitleActions.moveUp(order, "франшиза"))
    }
}
