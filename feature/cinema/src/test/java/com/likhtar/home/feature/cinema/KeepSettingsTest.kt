package com.likhtar.home.feature.cinema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** load() замінює стан цілком — налаштування сторінки мають пережити заміну. */
class KeepSettingsTest {
    @Test fun `live stills and trailer on up survive a fresh state`() {
        val before = TitleUiState(liveStills = false, trailerOnUp = false, actionsOrder = emptyList())
        val after = TitleUiState(loading = false).keepSettings(before)
        assertFalse(after.liveStills)
        assertFalse(after.trailerOnUp)
        assertEquals(before.actionsOrder, after.actionsOrder)
    }
}
