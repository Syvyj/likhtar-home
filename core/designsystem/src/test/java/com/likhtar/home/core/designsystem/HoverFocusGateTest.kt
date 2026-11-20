package com.likhtar.home.core.designsystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Миша й аеромиша: коли наведення переводить фокус (free-my-tv `HomeView` hover). */
class HoverFocusGateTest {
    private val a = Any()
    private val b = Any()

    @Test fun `навели на картку — фокус`() = assertTrue(HoverFocusGate().entered(a, 100f, 100f, fullyVisible = true, atMs = 0))

    @Test fun `рука тремтить на тій самій картці — не тягне фокус назад`() {
        val gate = HoverFocusGate()
        gate.entered(a, 100f, 100f, fullyVisible = true, atMs = 0)
        // Пультом пішли на b, вказівник лишився на a й здригнувся.
        assertFalse(gate.entered(a, 103f, 101f, fullyVisible = true, atMs = 0))
    }

    @Test fun `інша картка — фокус`() {
        val gate = HoverFocusGate()
        gate.entered(a, 100f, 100f, fullyVisible = true, atMs = 0)
        assertTrue(gate.entered(b, 400f, 100f, fullyVisible = true, atMs = 1_000))
    }

    @Test fun `вказівник стоїть, а ряд проїхав під ним — не фокус (без каскаду прокрутки)`() {
        val gate = HoverFocusGate()
        gate.entered(a, 100f, 100f, fullyVisible = true, atMs = 0)
        assertFalse(gate.entered(b, 101f, 100f, fullyVisible = true, atMs = 0))
    }

    @Test fun `картка видна не вся — не фокус, бо ряд прокрутився б`() =
        assertFalse(HoverFocusGate().entered(a, 100f, 100f, fullyVisible = false, atMs = 0))

    @Test fun `одразу після фокуса сусідня картка під рухомим вказівником не перехоплює`() {
        val gate = HoverFocusGate()
        gate.entered(a, 960f, 690f, fullyVisible = true, atMs = 0)
        assertFalse(gate.entered(b, 960f, 700f, fullyVisible = true, atMs = 100))
        assertTrue(gate.entered(b, 960f, 720f, fullyVisible = true, atMs = 300))
    }
}
