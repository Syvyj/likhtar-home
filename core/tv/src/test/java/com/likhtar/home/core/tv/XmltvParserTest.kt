package com.likhtar.home.core.tv

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XmltvParserTest {

    private val now = 1_786_000_000_000L

    @Test
    fun `розбирає канали й передачі`() {
        val guide = parse(SAMPLE)

        assertEquals(listOf("1+1", "1+1 HD"), guide.channelNames["1plus1.ua"])
        assertEquals(2, guide.programmes.size)
        assertEquals("Вечірні новини", guide.programmes.first().title)
        assertEquals("ТСН", guide.programmes.first().description)
    }

    @Test
    fun `передачі відсортовані за часом початку`() {
        val starts = parse(SAMPLE).programmes.map { it.startMillis }
        assertEquals(starts.sorted(), starts)
    }

    @Test
    fun `читає gzip так само як звичайний xml`() {
        val gzipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(SAMPLE.toByteArray()) }
        }.toByteArray()

        val guide = XmltvParser(nowMillis = { now }).parse(gzipped.inputStream())

        assertEquals(2, guide.programmes.size)
    }

    @Test
    fun `лишає тільки потрібні канали`() {
        val guide = XmltvParser(keepChannelIds = setOf("ictv.ua"), nowMillis = { now })
            .parse(SAMPLE.byteInputStream())

        assertEquals(listOf("ictv.ua"), guide.programmes.map { it.channelId })
    }

    @Test
    fun `лишає канал, що збігся лише за назвою`() {
        val guide = XmltvParser(
            keepChannelIds = setOf("1Plus1Ukraina.ua@SD"),
            keepChannelNames = setOf(normalizeChannelName("1+1")),
            nowMillis = { now },
        ).parse(SAMPLE.byteInputStream())

        assertEquals(listOf("1plus1.ua"), guide.programmes.map { it.channelId })
    }

    @Test
    fun `відкидає передачі за межами горизонту`() {
        val guide = XmltvParser(horizonMillis = 1, nowMillis = { now }).parse(SAMPLE.byteInputStream())

        assertTrue(guide.programmes.isEmpty())
    }

    @Test
    fun `час зі зсувом зони й без нього`() {
        // 18:30 у зоні +0300 — це 15:30 UTC.
        val withZone = XmltvParser.parseXmltvTime("20260812183000 +0300")
        val asUtc = XmltvParser.parseXmltvTime("20260812153000")

        assertEquals(asUtc, withZone)
        assertEquals(XmltvParser.parseXmltvTime("20260812183000+0300"), withZone)
    }

    @Test
    fun `невалідний час не кидає виняток`() {
        assertNull(XmltvParser.parseXmltvTime(null))
        assertNull(XmltvParser.parseXmltvTime("вчора ввечері"))
        assertNull(XmltvParser.parseXmltvTime("2026"))
    }

    @Test
    fun `нормалізація назви прибирає позначки якості й пунктуацію`() {
        assertEquals(normalizeChannelName("1+1"), normalizeChannelName("1 + 1 HD"))
        assertEquals(normalizeChannelName("ICTV"), normalizeChannelName("ictv"))
        assertEquals(normalizeChannelName("Novy Channel"), normalizeChannelName("Novy  Channel  FHD"))
    }

    private fun parse(xml: String) = XmltvParser(nowMillis = { now }).parse(xml.byteInputStream())

    private companion object {
        // 1786000000000 — це 2026-08-06 UTC, тож передачі нижче лежать у горизонті за
        // замовчуванням; час навмисно в різних форматах, як у реальних файлах провайдерів.
        val SAMPLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="1plus1.ua">
                <display-name>1+1</display-name>
                <display-name>1+1 HD</display-name>
              </channel>
              <channel id="ictv.ua">
                <display-name>ICTV</display-name>
              </channel>
              <programme channel="1plus1.ua" start="20260806180000 +0000" stop="20260806190000 +0000">
                <title>Вечірні новини</title>
                <desc>ТСН</desc>
              </programme>
              <programme channel="ictv.ua" start="20260806190000" stop="20260806200000">
                <title>Факти</title>
              </programme>
              <programme channel="ictv.ua" start="сміття" stop="20260806200000">
                <title>Не має потрапити</title>
              </programme>
            </tv>
        """.trimIndent()
    }
}
