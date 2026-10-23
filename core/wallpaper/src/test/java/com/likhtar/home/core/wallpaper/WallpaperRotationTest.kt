package com.likhtar.home.core.wallpaper

import com.likhtar.home.core.model.BackdropSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperRotationTest {

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        title = id,
        backdrop = BackdropSpec(baseGradient = listOf(0xFF000000, 0xFF111111)),
    )

    private fun provider(
        items: List<Wallpaper>,
        mode: WallpaperUpdateMode = WallpaperUpdateMode.TIME_ELAPSED,
    ) = object : WallpaperProvider {
        override val id = "test"
        override val name = "Test"
        override val updateMode = mode
        override suspend fun wallpapers() = items
    }

    private fun config(intervalMinutes: Int = 15) = WallpaperRepository.Config(
        providerId = "test",
        intervalMinutes = intervalMinutes,
        dimPercent = 30,
        showAttribution = true,
    )

    @Test
    fun `set rotates in order and wraps around`() = runTest {
        val items = listOf(wallpaper("a"), wallpaper("b"), wallpaper("c"))

        // runTest пропускає delay у віртуальному часі, тому чекати справжні хвилини не треба.
        val emitted = WallpaperRepository.rotate(provider(items), config(intervalMinutes = 15))
            .take(5)
            .toList()
            .map { it.wallpaper?.id }

        assertEquals(listOf("a", "b", "c", "a", "b"), emitted)
    }

    @Test
    fun `static provider emits once and completes`() = runTest {
        val flow = WallpaperRepository.rotate(
            provider(listOf(wallpaper("only")), WallpaperUpdateMode.STATIC),
            config(),
        )

        val emitted = flow.toList()
        assertEquals(1, emitted.size)
        assertEquals("only", emitted.single().wallpaper?.id)
    }

    @Test
    fun `single wallpaper is not rotated`() = runTest {
        val emitted = WallpaperRepository.rotate(provider(listOf(wallpaper("one"))), config()).toList()
        assertEquals(1, emitted.size)
    }

    @Test
    fun `empty source yields a state without wallpaper but keeps display settings`() = runTest {
        val emitted = WallpaperRepository.rotate(provider(emptyList()), config()).toList()

        assertEquals(1, emitted.size)
        assertNull(emitted.single().wallpaper)
        assertEquals(30, emitted.single().dimPercent)
        assertTrue(emitted.single().showAttribution)
    }

    @Test
    fun `missing provider does not crash the home screen`() = runTest {
        val emitted = WallpaperRepository.rotate(provider = null, config = config()).toList()
        assertNull(emitted.single().wallpaper)
    }
}
