package com.likhtar.home.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun `номери порівнюються числами, а не рядками`() {
        assertTrue(AppUpdate.newer("0.1.10", "0.1.9"))
        assertTrue(AppUpdate.newer("0.2.0", "0.1.4"))
        assertTrue(AppUpdate.newer("0.1.9.1", "0.1.9"))
        assertTrue(AppUpdate.newer("0.2.0", "0.1.9.1"))
        assertFalse(AppUpdate.newer("0.1.4", "0.1.4"))
        assertFalse(AppUpdate.newer("0.1.3", "0.1.4"))
        assertFalse(AppUpdate.newer("0.1.0", "0.1.0-alpha01"))
    }

    @Test
    fun `випуск GitHub — номер без v, посилання на APK, примітки простим текстом`() {
        val json = """{"tag_name":"v0.1.5","body":"**Fix.** See [docs](https://x).\n\nSHA-256: `abc`",
            "assets":[{"name":"other.zip","browser_download_url":"https://x/o.zip"},
                      {"name":"likhtar-home.apk","browser_download_url":"https://x/likhtar-home.apk"}]}"""
        val r = AppUpdate.parse(json)!!
        assertEquals("0.1.5", r.version)
        assertEquals("https://x/likhtar-home.apk", r.apkUrl)
        assertEquals("Fix. See docs.", r.notes)
    }

    @Test
    fun `без APK у вкладеннях — оновлення немає`() {
        assertNull(AppUpdate.parse("""{"tag_name":"v0.1.5","assets":[]}"""))
    }
}
