package com.likhtar.home.feature.home

import com.likhtar.home.feature.home.TvInputTile.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class TvInputsTest {
    @Test
    fun kindByCecName() {
        assertEquals(Kind.GAME, TvInputs.kindOf("PlayStation 5"))
        assertEquals(Kind.GAME, TvInputs.kindOf("PS5"))
        assertEquals(Kind.GAME, TvInputs.kindOf("Xbox Series X"))
        assertEquals(Kind.GAME, TvInputs.kindOf("Nintendo Switch"))
        assertEquals(Kind.PLAYER, TvInputs.kindOf("Apple TV"))
        assertEquals(Kind.PLAYER, TvInputs.kindOf("SHIELD"))
        assertEquals(Kind.OTHER, TvInputs.kindOf("HDMI 3"))
        assertEquals(Kind.OTHER, TvInputs.kindOf("HW-Q6C Series"))
        assertEquals(Kind.OTHER, TvInputs.kindOf("GPS5 tracker"))
    }
}
