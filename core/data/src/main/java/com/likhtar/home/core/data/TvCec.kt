package com.likhtar.home.core.data

import javax.inject.Inject
import javax.inject.Singleton

/** Пристрій у мережі HDMI-CEC: логічна адреса, тип (0 — ТВ, 5 — аудіосистема), назва, живлення (-1 — невідомо). */
data class CecDevice(val address: Int, val type: Int, val name: String, val power: Int)

/** Що знає приставка про HDMI-CEC: увімкнено, хто в мережі, чи гучність пульта йде через CEC. */
data class CecState(val enabled: Boolean, val devices: List<CecDevice>, val volumeControl: Boolean) {
    val hasAudioSystem: Boolean get() = devices.any { it.type == CEC_AUDIO_SYSTEM }
}

const val CEC_TV = 0
const val CEC_AUDIO_SYSTEM = 5

private val DEVICE = Regex("""^\s*CEC: logical_address: 0x([0-9A-Fa-f]+) device_type: (\d+) .*?display_name: (.*?) power_status: (-?\d+)""")

/**
 * Розбір `dumpsys hdmi_control` (lounge `PowerSetup`): пристрої з `mDeviceInfos` (сама приставка —
 * у `HdmiCecLocalDevice`, її пропускаємо) і два налаштування. `mIsCecAvailable` на цій прошивці
 * бреше `false`, хоч повідомлення ходять (ACK), тож на нього не дивимось.
 */
fun parseCec(dump: String): CecState {
    fun setting(name: String) = Regex("""$name \(int\): (\d+)""").find(dump)?.groupValues?.get(1) == "1"
    // Сама приставка: її адреси — у `HdmiCecLocalDevice` (mDeviceInfo); після One Touch Play вона
    // з'являється й у mDeviceInfos — з OSD-назвою «Телевізор», і список двоївся.
    val own = dump.substringBefore("mDeviceInfos:").lineSequence()
        .filter { "mDeviceInfo:" in it }.mapNotNull { DEVICE.find(it.substringAfter("mDeviceInfo:"))?.groupValues?.get(1)?.toInt(16) }.toSet()
    val network = dump.substringAfter("mDeviceInfos:", "").substringBefore("mCecController:")
    val devices = network.lineSequence().mapNotNull { DEVICE.find(it) }.map { m ->
        CecDevice(m.groupValues[1].toInt(16), m.groupValues[2].toInt(), m.groupValues[3].trim(), m.groupValues[4].toInt())
    }.filter { it.address !in own }.distinctBy { it.address }.toList()
    return CecState(enabled = setting("hdmi_cec_enabled"), devices = devices, volumeControl = setting("volume_control_enabled"))
}

/**
 * Телевізор через HDMI-CEC з приставки (джерела): увімкнути ТВ і перемкнути на приставку, гучність
 * пульта — на ТВ чи саундбар. Через ADB телевізора, ту саму чергу, що й пульт з телефона ([TvRemote]).
 * Перемикати звук на саундбар звідси не можна: `setsystemaudiomode` — лише для телевізорів.
 */
@Singleton
class TvCec @Inject constructor(private val adb: TvRemote) {
    /** `null` — ADB телевізора недоступний. */
    fun state(): CecState? = adb.output("dumpsys hdmi_control")?.let(::parseCec)

    /** Увімкнути телевізор і перемкнути його вхід на приставку (One Touch Play). */
    fun wakeTv(): Boolean = adb.output("cmd hdmi_control onetouchplay") != null

    /** Кнопки гучності пульта приставки — на ТВ чи аудіосистему через CEC. */
    fun setVolumeControl(on: Boolean): Boolean =
        adb.output("cmd hdmi_control cec_setting set volume_control_enabled ${if (on) 1 else 0}") != null
}
