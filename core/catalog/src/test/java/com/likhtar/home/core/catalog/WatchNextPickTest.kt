package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Продовжити» з Watch Next інших застосунків: що лишаємо і в якому порядку. */
class WatchNextPickTest {
    private fun row(title: String, pkg: String = "app", at: Long = 1, type: Int = 0, browsable: Boolean = true) =
        WatchNextRow(ServiceResume(title, null, "intent:$title", pkg, updatedAt = at), type, browsable)

    @Test fun `список перегляду й приховані — геть`() {
        val out = pickWatchNext(listOf(row("A", type = 3), row("B", browsable = false), row("C")))
        assertEquals(listOf("C"), out.map { it.title })
    }

    @Test fun `одна назва — один рядок, найсвіжіший`() =
        assertEquals(listOf(20L), pickWatchNext(listOf(row("A", at = 10), row("A", at = 20))).map { it.updatedAt })

    @Test fun `застосунок не займає більше половини перших місць, але нічого не губиться`() {
        val rows = (1..6).map { row("N$it", pkg = "netflix", at = 100L - it) } + row("Y", pkg = "youtube", at = 1)
        val out = pickWatchNext(rows).map { it.title }
        assertEquals(7, out.size)
        assertTrue(out.indexOf("Y") < 4)
    }

    @Test fun `один застосунок — порядок за свіжістю, без обмежень`() {
        val rows = (1..5).map { row("N$it", pkg = "netflix", at = 100L - it) }
        assertEquals(listOf("N1", "N2", "N3", "N4", "N5"), pickWatchNext(rows).map { it.title })
    }

    @Test fun `картинка лише https публічна, content чи android_resource`() {
        assertEquals("https://img.nflx/a.jpg", safeArtwork("https://img.nflx/a.jpg"))
        assertEquals("content://x/1", safeArtwork("content://x/1"))
        assertEquals("android.resource://pkg/1", safeArtwork("android.resource://pkg/1"))
        assertNull(safeArtwork(null))
        listOf(
            "http://cdn/a.jpg", "https://192.168.0.5/a.jpg", "https://10.0.0.1/a", "https://172.20.1.1/a",
            "https://127.0.0.1/a", "https://169.254.1.1/a", "https://100.64.0.1/a", "https://[::1]/a",
            "https://[fd00::1]/a", "https://localhost/a", "https://tv.local/a", "https://nas.lan/a",
            "file:///sdcard/a.jpg", "https://user@cdn/a",
        ).forEach { assertNull(it, safeArtwork(it)) }
    }

    @Test fun `IP у десятковому, вісімковому чи короткому записі й імена з крапкою — ні`() {
        listOf(
            "https://3232235525/a.jpg", "https://0300.0250.0.5/a.jpg", "https://0xC0A80005/a.jpg", "https://192.168.5/a.jpg",
            "https://tv.local./a.jpg", "https://localhost./a.jpg",
        ).forEach { assertNull(it, safeArtwork(it)) }
    }

    @Test fun `URL з пробілом не губить картинку`() =
        assertEquals("https://img.cdn/a%20b.jpg", safeArtwork("https://img.cdn/a b.jpg"))
}
