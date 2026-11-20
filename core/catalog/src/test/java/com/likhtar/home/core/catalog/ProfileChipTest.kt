package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** «Хто дивиться» у верхньому меню: ім'я активного профілю, коли є з кого вибирати. */
class ProfileChipTest {
    private val home = Profile("default", "Дім", kids = false)
    private val kid = Profile("p1", "Марко", kids = true)

    @Test fun `один профіль — кнопки немає`() = assertNull(profileChipName(listOf(home), "default"))

    @Test fun `сервер не відповів — кнопки немає`() = assertNull(profileChipName(emptyList(), "default"))

    @Test fun `кілька — ім'я активного`() = assertEquals("Марко", profileChipName(listOf(home, kid), "p1"))

    @Test fun `активного вже немає на сервері — як «default», тобто Дім`() =
        assertEquals("Дім", profileChipName(listOf(home, kid), "p-deleted"))
}
