package com.likhtar.home.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HDMI-CEC приставки: розбір `dumpsys hdmi_control` (зразок з приставки). */
class TvCecTest {
    private val dump = """
        mIsCecAvailable: false
        CEC settings:
          hdmi_cec_enabled (int): 1 (default: 1) [modifiable]
          volume_control_enabled (int): 0 (default: 0) [modifiable]
        HDMI CEC Network
          HdmiCecLocalDevice #4:
            mDeviceInfo: CEC: logical_address: 0x04 device_type: 4 cec_version: 6 vendor_id: 32640 display_name: Телевізор power_status: 0 physical_address: 0x1000 port_id: 0
          mDeviceInfos:
            CEC: logical_address: 0x00 device_type: 0 cec_version: 5 vendor_id: 16777215 display_name: TV power_status: -1 physical_address: 0xFFFF port_id: -1
              Device features: record_tv_screen: ?
            CEC: logical_address: 0x05 device_type: 5 cec_version: 5 vendor_id: 16777215 display_name: AudioSystem power_status: 1 physical_address: 0xFFFF port_id: -1
        mCecController:
    """.trimIndent()

    @Test fun `пристрої мережі без самої приставки`() {
        val s = parseCec(dump)
        assertEquals(listOf(CecDevice(0, 0, "TV", -1), CecDevice(5, 5, "AudioSystem", 1)), s.devices)
    }

    @Test fun `сама приставка в mDeviceInfos (після One Touch Play) — не пристрій мережі`() {
        val withSelf = dump.replace(
            "mDeviceInfos:\n",
            "mDeviceInfos:\n    CEC: logical_address: 0x04 device_type: 4 cec_version: 6 vendor_id: 32640 display_name: Телевізор power_status: 0 physical_address: 0x1000 port_id: 0\n",
        )
        assertTrue(withSelf != dump) // заміна справді влучила
        assertEquals(listOf(0, 5), parseCec(withSelf).devices.map { it.address })
    }

    @Test fun `CEC увімкнено, гучність пульта — ні`() {
        val s = parseCec(dump)
        assertTrue(s.enabled)
        assertFalse(s.volumeControl)
        assertTrue(s.hasAudioSystem)
    }

    @Test fun `гучність через CEC увімкнена`() =
        assertTrue(parseCec(dump.replace("volume_control_enabled (int): 0", "volume_control_enabled (int): 1")).volumeControl)

    @Test fun `CEC вимкнено чи порожній вивід`() {
        assertFalse(parseCec(dump.replace("hdmi_cec_enabled (int): 1", "hdmi_cec_enabled (int): 0")).enabled)
        assertFalse(parseCec("").enabled)
        assertTrue(parseCec("").devices.isEmpty())
    }
}
