package com.likhtar.home.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Зміна адреси з телефона не переносить збережений токен чи ключ на інший сервер. */
class SameServerTest {
    @Test fun `same host and port`() {
        assertTrue(sameServer("http://192.168.1.10:8123", "http://192.168.1.10:8123/"))
        assertTrue(sameServer("http://HA.local:8123/api", "http://ha.local:8123"))
    }

    @Test fun `other host, port or scheme is another server`() {
        assertFalse(sameServer("http://evil:8123", "http://192.168.1.10:8123"))
        assertFalse(sameServer("http://192.168.1.10:9000", "http://192.168.1.10:8123"))
        assertFalse(sameServer("https://192.168.1.10:8123", "http://192.168.1.10:8123"))
        assertFalse(sameServer("http://x", ""))
        assertFalse(sameServer("", ""))
    }
}
