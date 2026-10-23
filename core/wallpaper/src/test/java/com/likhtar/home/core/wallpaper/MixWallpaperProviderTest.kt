package com.likhtar.home.core.wallpaper

import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixWallpaperProviderTest {

    private fun source(id: String, vararg titles: String) = object : WallpaperProvider {
        override val id = id
        override val name = id
        override val updateMode = WallpaperUpdateMode.TIME_ELAPSED
        override suspend fun wallpapers() = titles.map { Wallpaper(id = it, title = it) }
    }

    private fun mix(vararg sources: WallpaperProvider): MixWallpaperProvider {
        lateinit var provider: MixWallpaperProvider
        provider = MixWallpaperProvider(Provider { sources.toSet() + provider }) { "Mix" }
        return provider
    }

    @Test
    fun `джерела перебираються по колу, а не склеюються поспіль`() = runTest {
        // Інакше людина півдня дивилась би на один набір, перш ніж дійти до наступного.
        val mixed = mix(source("a", "a1", "a2", "a3"), source("b", "b1")).wallpapers()
        assertEquals(listOf("a1", "b1", "a2", "a3"), mixed.map { it.id })
    }

    @Test
    fun `сам мікс у свій набір не входить`() = runTest {
        // Пряме включення дало б нескінченну рекурсію на першому ж виклику.
        assertEquals(listOf("a1"), mix(source("a", "a1")).wallpapers().map { it.id })
    }

    @Test
    fun `відмова від фону в мікс не потрапляє`() = runTest {
        val mixed = mix(source("plain", "порожньо"), source("a", "a1")).wallpapers()
        assertEquals(listOf("a1"), mixed.map { it.id })
    }

    @Test
    fun `джерело, яке впало, не валить увесь мікс`() = runTest {
        val broken = object : WallpaperProvider {
            override val id = "broken"
            override val name = "broken"
            override val updateMode = WallpaperUpdateMode.TIME_ELAPSED
            override suspend fun wallpapers(): List<Wallpaper> = error("мережі немає")
        }
        val mixed = mix(broken, source("a", "a1")).wallpapers()
        assertTrue("решта джерел має лишитись", mixed.map { it.id } == listOf("a1"))
    }
}
