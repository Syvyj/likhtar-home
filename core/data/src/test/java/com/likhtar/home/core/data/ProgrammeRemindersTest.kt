package com.likhtar.home.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammeRemindersTest {
    @Test
    fun `туди й назад, зіпсоване — порожньо`() {
        val list = listOf(ProgrammeReminder("http://h/1", "TVP 1", "Новини", 1_000L))
        assertEquals(list, decodeReminders(encodeReminders(list)))
        assertTrue(decodeReminders("{").isEmpty())
        assertTrue(decodeReminders(null).isEmpty())
    }
}
