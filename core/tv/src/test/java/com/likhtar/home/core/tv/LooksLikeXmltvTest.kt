package com.likhtar.home.core.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Заглушка провайдера не лягає в кеш як програма на день. */
class LooksLikeXmltvTest {
    private fun file(bytes: ByteArray) = java.io.File.createTempFile("epg", ".xml").apply { writeBytes(bytes); deleteOnExit() }
    private fun file(text: String) = file(text.toByteArray())

    @Test fun `xmltv and gzip pass`() {
        assertTrue(looksLikeXmltv(file("<?xml version=\"1.0\"?>\n<tv generator-info-name=\"x\">")))
        assertTrue(looksLikeXmltv(file("﻿  <tv>")))
        assertTrue(looksLikeXmltv(file(byteArrayOf(0x1f, 0x8b.toByte(), 8, 0))))
    }

    @Test fun `html stub and empty fail`() {
        assertFalse(looksLikeXmltv(file("<!DOCTYPE html><html><body>Login</body></html>")))
        assertFalse(looksLikeXmltv(file("<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">")))
        assertFalse(looksLikeXmltv(file("")))
    }
}
