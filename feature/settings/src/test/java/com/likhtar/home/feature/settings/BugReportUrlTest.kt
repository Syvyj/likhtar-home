package com.likhtar.home.feature.settings

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** QR «Повідомити про помилку»: issue на GitHub з уже вписаними приставкою й версією. */
class BugReportUrlTest {
    private val device = BugReportDevice(maker = "Acme", model = "TV Box 4K", android = "14", sdk = 34, version = "0.3.3", build = "personal")
    private val url = bugReportUrl("https://github.com/Syvyj/likhtar-home", device)

    private fun param(name: String) = url.substringAfter("?").split("&")
        .first { it.startsWith("$name=") }.substringAfter("=").let { URLDecoder.decode(it, "UTF-8") }

    @Test fun `веде на нову issue цього репо`() = assertTrue(url.startsWith("https://github.com/Syvyj/likhtar-home/issues/new?"))

    @Test fun `у тілі — приставка, Android і версія`() {
        val body = param("body")
        assertTrue(body.contains("Acme TV Box 4K"))
        assertTrue(body.contains("Android 14 (API 34)"))
        assertTrue(body.contains("Likhtar 0.3.3 (personal)"))
    }

    @Test fun `назва з версією`() = assertEquals("[0.3.3] ", param("title"))

    @Test fun `пробіли й символи закодовано — QR без сміття`() {
        assertTrue(' ' !in url && '\n' !in url && '#' !in url)
    }

    @Test fun `довга модель не роздуває посилання`() {
        val long = bugReportUrl("https://github.com/x/y", device.copy(model = "M".repeat(5000)))
        assertTrue(long.length <= 1500)
    }
}
