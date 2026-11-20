package com.likhtar.home.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Пульт із телефона: кнопка сторінки → команда ADB телевізора. */
class TvRemoteTest {
    @Test fun `стрілки, OK, назад і медіа`() {
        assertEquals("cmd input keyevent KEYCODE_DPAD_UP", remoteKeyCommand("up"))
        assertEquals("cmd input keyevent KEYCODE_DPAD_CENTER", remoteKeyCommand("ok"))
        assertEquals("cmd input keyevent KEYCODE_BACK", remoteKeyCommand("back"))
        assertEquals("cmd input keyevent KEYCODE_MEDIA_PLAY_PAUSE", remoteKeyCommand("playpause"))
        assertEquals("cmd input keyevent KEYCODE_VOLUME_UP", remoteKeyCommand("volup"))
    }

    @Test fun `невідома кнопка й «дім» — не keyevent`() {
        assertNull(remoteKeyCommand("rm -rf"))
        assertNull(remoteKeyCommand("home")) // дім — запуск Ліхтаря, не системний HOME
    }

    @Test fun `текст — у лапках, пробіл як %s`() =
        assertEquals("cmd input text 'my%sWi-Fi%s123'", remoteTextCommand("my Wi-Fi 123"))

    @Test fun `апостроф і спецсимволи shell не виходять з лапок`() =
        assertEquals("cmd input text 'it'\\''s;\$(x)'", remoteTextCommand("it's;\$(x)"))

    @Test fun `не ASCII чи порожньо — ні`() {
        assertNull(remoteTextCommand("пароль"))
        assertNull(remoteTextCommand(""))
        assertNull(remoteTextCommand("a\nb"))
    }

    @Test fun `задовгий текст обрізаємо`() = assertEquals(200 + "cmd input text ''".length, remoteTextCommand("x".repeat(500))!!.length)
}
