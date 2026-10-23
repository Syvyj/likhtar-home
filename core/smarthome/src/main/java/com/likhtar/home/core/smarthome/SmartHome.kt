package com.likhtar.home.core.smarthome

import androidx.annotation.StringRes

/**
 * Пристрій або сутність Home Assistant у вигляді, потрібному лаунчеру.
 *
 * HA має десятки доменів; лаунчер бере лише ті, які має сенс показувати й натискати з пульта.
 */
data class SmartDevice(
    val entityId: String,
    val name: String,
    val kind: SmartDeviceKind,
    /** Сирий стан HA: `on`, `off`, `23.5`, `home`… */
    val state: String,
    val isOn: Boolean,
    /** Одиниця виміру для сенсорів, якщо є. */
    val unit: String? = null,
    /** Кімната (area) у HA; null — не призначено. */
    val room: String? = null,
    /** Готовий підпис замість стану: «Друкує · 42 % · ще 1 год», «0 % — час міняти фільтр». */
    val detail: String? = null,
    /** Другорядний рядок (температури принтера). */
    val extra: String? = null,
    /** 0..1 — смужка прогресу (друк). */
    val progress: Float? = null,
    /** Потребує уваги: фільтр вичерпано, друк упав. */
    val warning: Boolean = false,
    /** Значок, який людина дала пристрою в HA (`mdi:ceiling-light`); null — не задано. */
    val icon: String? = null,
) {
    val domain: String get() = entityId.substringBefore('.')

    /**
     * Що показати на картці: готовий підпис або значення сенсора. `null` — показати
     * «Сцена» / «Увімкнено» / «Вимкнено» з ресурсів ([R.string.smarthome_scene] тощо).
     */
    val readableState: String?
        get() = detail ?: when (kind) {
            SmartDeviceKind.SENSOR -> listOfNotNull(state, unit).joinToString(" ")
            else -> null
        }

    val isControllable: Boolean get() = kind != SmartDeviceKind.SENSOR && kind != SmartDeviceKind.PRINTER
}

enum class SmartDeviceKind { LIGHT, SWITCH, SCENE, CLIMATE, COVER, SENSOR, PRINTER }

/** Стан підключення до Home Assistant для екрана та налаштувань. */
sealed interface SmartHomeStatus {
    /** Адреса або токен не задані. */
    data object NotConfigured : SmartHomeStatus

    data object Connecting : SmartHomeStatus

    data class Connected(val deviceCount: Int) : SmartHomeStatus {
        /**
         * «1 пристрій», «3 пристрої», «12 пристроїв» — лише українською.
         * Для UI — `pluralStringResource(R.plurals.smarthome_devices, deviceCount, deviceCount)`.
         */
        val label: String
            get() = "$deviceCount " + when {
                deviceCount % 100 in 11..14 -> "пристроїв"
                deviceCount % 10 == 1 -> "пристрій"
                deviceCount % 10 in 2..4 -> "пристрої"
                else -> "пристроїв"
            }
    }

    data class Error(val message: String) : SmartHomeStatus
}

/** Тексти з ресурсів модуля (`Context.getString`); у юніт-тестах — без Context. */
fun interface SmartHomeTexts {
    fun get(@StringRes id: Int, vararg args: Any): String
}

data class SmartHomeState(
    val status: SmartHomeStatus = SmartHomeStatus.NotConfigured,
    val devices: List<SmartDevice> = emptyList(),
)

/** Куди підключатися. Токен — це секрет користувача, тому лаунчер його лише читає. */
data class SmartHomeConfig(
    val baseUrl: String,
    val token: String,
) {
    val isComplete: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()

    /** Прибирає зайвий слеш і `/api`, який користувачі часто копіюють разом з адресою. */
    fun normalizedBaseUrl(): String = baseUrl.trim()
        .removeSuffix("/")
        .removeSuffix("/api")
        .removeSuffix("/")
}
