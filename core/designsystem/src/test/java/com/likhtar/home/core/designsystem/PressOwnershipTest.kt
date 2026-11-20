package com.likhtar.home.core.designsystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Чиє натискання OK: повтор утримання, почате на іншому елементі, — не наше. */
class PressOwnershipTest {
    @Test fun `свіже натискання — наше`() = assertTrue(ownsPress(repeatCount = 0, pressedBefore = false))

    @Test fun `повтор свого утримання — наш`() = assertTrue(ownsPress(repeatCount = 3, pressedBefore = true))

    @Test fun `повтор чужого утримання — не наш`() = assertFalse(ownsPress(repeatCount = 3, pressedBefore = false))
}
