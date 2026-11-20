package com.likhtar.home.feature.cinema

import com.likhtar.home.core.catalog.PictureTier
import com.likhtar.home.core.catalog.VoiceGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PickerRowsTest {
    private fun vg(voice: String, quality: Int, hdr: String = "") = VoiceGroup(voice, quality, hdr, 1, listOf("r"))

    @Test fun `ключі унікальні навіть для однакових озвучок`() {
        val same = vg("Дубляж", 1080)
        val keys = pickerRows(listOf(same, same.copy())).filterIsInstance<PickerItem.Voice>().map { it.key }
        assertEquals(2, keys.toSet().size)
    }

    @Test fun `верхній рядок міняється, коли над 1080p з'являється 4K тієї ж озвучки`() {
        val before = pickerRows(listOf(vg("Дубляж", 1080)))
        val after = pickerRows(listOf(vg("Дубляж", 1080), vg("Дубляж", 2160, "DV")))
        assertEquals(PickerItem.Header(PictureTier.DV_4K), after.first())
        assertNotEquals(before.topVoiceKey(), after.topVoiceKey())
    }
}
