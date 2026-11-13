package com.likhtar.home.core.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** «Зараз на каналі» Sweet.TV — з розкладу; `currentEpg` у них буває вчорашнім днем і завтрашнім. */
class SweetScheduleTest {
    private fun p(title: String, start: Long, end: Long) = EpgProgramme(title, start, end)
    private val day = listOf(p("Ранок", 0, 100), p("День", 100, 200), p("Вечір", 300, 400))

    @Test
    fun `currentEpg відстала — беремо ту, що йде за розкладом`() {
        val s = sweetSchedule(day, current = p("Ранок", 0, 100), now = 150)!!
        assertEquals("День", s.live?.title)
        assertEquals(listOf("Вечір"), s.upcoming.map { it.title })
    }

    @Test
    fun `пауза між передачами — канал лишається з найближчою наступною`() {
        val s = sweetSchedule(day, current = p("Вечір", 300, 400), now = 250)!!
        assertEquals("Вечір", s.live?.title)
        assertEquals(300L, s.live?.startMillis)
    }

    @Test
    fun `розкладу немає — currentEpg, якщо вона справді йде`() {
        assertEquals("Фільм", sweetSchedule(emptyList(), p("Фільм", 0, 100), now = 50)?.live?.title)
        assertNull(sweetSchedule(emptyList(), p("Фільм", 0, 100), now = 150))
        assertNull(sweetSchedule(emptyList(), null, now = 150))
    }
}
