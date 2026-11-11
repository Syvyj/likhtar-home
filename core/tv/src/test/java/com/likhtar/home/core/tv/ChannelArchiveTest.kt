package com.likhtar.home.core.tv

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * «Є архів» — лише там, де плеєр збудує адресу (ті самі вектори, що в коментарі
 * `TvChannels.hasArchiveAt` форку Just+).
 */
class ChannelArchiveTest {
    @Test
    fun `flussonic — mpegts, ts і m3u8`() {
        assertNotNull(ChannelArchive.of("flussonic", 5, "", "http://h/lb0/NTN/mpegts?token=x"))
        assertNotNull(ChannelArchive.of("flussonic-hls", 3, "", "http://h/ch/index.m3u8"))
        assertNotNull(ChannelArchive.of("fs", 3, "", "http://h/ch/video.ts"))
    }

    @Test
    fun `flussonic без відомого потоку — архіву немає`() {
        assertNull(ChannelArchive.of("flussonic", 3, "", "http://h/ch/stream"))
    }

    @Test
    fun `шаблон catchup-source і shift`() {
        assertNotNull(ChannelArchive.of("append", 3, "?utc={utc}&dur={duration}", "http://h/ch/stream"))
        assertNotNull(ChannelArchive.of("shift", 3, "", "http://h/ch/stream"))
    }

    @Test
    fun `append і xc без шаблону — архіву немає`() {
        assertNull(ChannelArchive.of("append", 3, "", "http://h/ch/stream"))
        assertNull(ChannelArchive.of("xc", 3, "", "http://h/ch/stream"))
    }

    @Test
    fun `без днів архіву немає`() {
        assertNull(ChannelArchive.of("flussonic", 0, "", "http://h/ch/mpegts"))
    }
}
