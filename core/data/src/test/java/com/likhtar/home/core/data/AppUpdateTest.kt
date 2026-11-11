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

    @Test
    fun `контрольна сума з приміток — для свого вкладення, а з тексту прибрана`() {
        val home = "e6e344ab7c9a5466e63a8c067dfbea0ada509a24e3682c5fcbad7551711f6060"
        val tv = "c99ab8b88169d73227ed344a76653a1ade1986f97e8b3ac3f8925fd2dd409206"
        val body = "**New.** Channels.\n\nSHA-256\n`likhtar-home.apk` `$home`\n`likhtar-tv.apk` `$tv`"
        val json = org.json.JSONObject().put("tag_name", "v0.3.0").put("body", body).put(
            "assets", org.json.JSONArray().put(org.json.JSONObject().put("name", "likhtar-home.apk").put("browser_download_url", "https://x/h.apk")),
        ).toString()
        val r = AppUpdate.parse(json)!!
        assertEquals(home, r.sha256)
        assertEquals(tv, AppUpdate.sha256Of(body, "likhtar-tv.apk"))
        assertEquals("", AppUpdate.sha256Of(body, "likhtar-installer.apk"))
        assertEquals("New. Channels.", r.notes)
    }

    @Test
    fun `SHA-256 файла`() {
        val f = java.io.File.createTempFile("apk", ".bin").apply { writeText("abc"); deleteOnExit() }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AppUpdate.sha256Of(f))
    }
}
