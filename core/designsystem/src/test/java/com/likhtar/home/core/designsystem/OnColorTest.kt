package com.likhtar.home.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnColorTest {

    @Test
    fun `на яскраво-жовтому текст темний`() {
        // Саме цей випадок видно на кадрі: «Viasat Serial HD» білим по жовтому.
        assertTrue("на жовтому білий текст не читається", onColor(Color(0xFFF2E85C)) != Color.White)
    }

    @Test
    fun `на темно-синьому текст світлий`() {
        assertEquals(Color.White, onColor(Color(0xFF2A3A8C)))
    }

    @Test
    fun `яскравість рахується за WCAG, а не за середнім каналом`() {
        // Зелений яскравіший за синій тієї ж «середньої» величини — саме через ваги 0.7152
        // проти 0.0722. Середнє арифметичне дало б однакові числа.
        val green = relativeLuminance(Color(0xFF00FF00))
        val blue = relativeLuminance(Color(0xFF0000FF))
        assertTrue("зелений має бути яскравішим за синій: $green проти $blue", green > blue * 5)
    }

    @Test
    fun `крайності не плутаються`() {
        assertEquals(Color.White, onColor(Color.Black))
        assertTrue(onColor(Color.White) != Color.White)
    }
}
