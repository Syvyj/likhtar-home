package com.likhtar.home.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteKeysTest {
    private val yt = KeyAction.App("com.google.android.youtube.tv")
    private val live = KeyAction.Section("livetv")

    @Test
    fun `без довгої дії коротка — одразу на DOWN, UP нічого не робить`() {
        val t = RemotePressTracker()
        assertEquals(yt, t.onEvent(down = true, repeatCount = 0, eventTime = 0, short = yt, long = null))
        assertNull(t.onEvent(down = false, repeatCount = 0, eventTime = 150, short = yt, long = null))
    }

    @Test
    fun `з довгою дією коротке натискання — на UP, фантомний повтор не довге`() {
        val t = RemotePressTracker()
        assertNull(t.onEvent(true, 0, 0, yt, live))
        assertNull(t.onEvent(true, 1, 194, yt, live))
        assertEquals(yt, t.onEvent(false, 0, 250, yt, live))
    }

    @Test
    fun `утримання — довга дія один раз, UP після неї нічого`() {
        val t = RemotePressTracker()
        assertNull(t.onEvent(true, 0, 0, yt, live))
        assertEquals(live, t.onEvent(true, 5, 650, yt, live))
        assertNull(t.onEvent(true, 6, 700, yt, live))
        assertNull(t.onEvent(false, 0, 900, yt, live))
    }

    @Test
    fun `пульт без автоповтору — довге впізнається на UP`() {
        val t = RemotePressTracker()
        assertNull(t.onEvent(true, 0, 0, yt, live))
        assertEquals(live, t.onEvent(false, 0, 800, yt, live))
    }

    @Test
    fun `прив'язка переживає кодування`() {
        listOf(
            KeyBinding(265, KeyPress.SHORT, KeyAction.Streaming("netflix")),
            KeyBinding(172, KeyPress.LONG, live),
            KeyBinding(266, KeyPress.SHORT, yt),
            KeyBinding(23, KeyPress.LONG, KeyAction.Assistant),
        ).forEach { assertEquals(it, RemoteKeys.decode(RemoteKeys.encode(it))) }
        assertNull(RemoteKeys.decode("сміття"))
    }
}
