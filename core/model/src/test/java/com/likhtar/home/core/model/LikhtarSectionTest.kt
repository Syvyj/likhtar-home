package com.likhtar.home.core.model

import com.likhtar.home.core.model.LikhtarSection.APPS
import com.likhtar.home.core.model.LikhtarSection.CINEMA
import com.likhtar.home.core.model.LikhtarSection.HOME
import com.likhtar.home.core.model.LikhtarSection.LIVE_TV
import com.likhtar.home.core.model.LikhtarSection.SEARCH
import com.likhtar.home.core.model.LikhtarSection.SETTINGS
import org.junit.Assert.assertEquals
import org.junit.Test

class LikhtarSectionTest {

    @Test
    fun `own order keeps Home first, Settings last and unknown sections at the end`() {
        val all = listOf(HOME, SEARCH, CINEMA, LIVE_TV, APPS, SETTINGS)
        assertEquals(all, LikhtarSection.ordered(all, emptyList()))
        assertEquals(
            listOf(HOME, LIVE_TV, CINEMA, SEARCH, APPS, SETTINGS),
            LikhtarSection.ordered(all, listOf("settings", "livetv", "home", "cinema", "search")),
        )
    }
}
