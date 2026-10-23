package com.likhtar.home.core.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Картка серії показує номер окремим рядком, тому назва «Серія 3» дублювала б його.
 *
 * Перевіряємо саме межі: номер у назві мусить збігатися з номером серії, інакше це вже
 * осмислена назва («Серія 7» у третій серії — це не дубль, а справжній заголовок).
 */
class EpisodeOwnTitleTest {

    @Test
    fun `назва з номером тієї самої серії не вважається власною`() {
        assertFalse(Episode(number = 3, title = "Серія 3").hasOwnTitle)
        assertFalse(Episode(number = 3, title = "Епізод 3").hasOwnTitle)
        assertFalse(Episode(number = 3, title = "Episode 3").hasOwnTitle)
        assertFalse(Episode(number = 3, title = "серія 3").hasOwnTitle)
        assertFalse(Episode(number = 1, title = "").hasOwnTitle)
    }

    @Test
    fun `справжня назва лишається на місці`() {
        assertTrue(Episode(number = 1, title = "Пілот").hasOwnTitle)
        assertTrue(Episode(number = 1, title = "Final Call").hasOwnTitle)
        // Чужий номер у назві — це назва, а не наш підставлений плейсхолдер.
        assertTrue(Episode(number = 3, title = "Серія 7").hasOwnTitle)
    }
}
