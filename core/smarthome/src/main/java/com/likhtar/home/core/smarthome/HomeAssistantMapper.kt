package com.likhtar.home.core.smarthome

import org.json.JSONArray
import org.json.JSONObject

/**
 * Перетворює `GET /api/states` у список пристроїв лаунчера.
 *
 * Виділено окремо від мережі, щоб можна було перевіряти розбір відповіді юніт-тестами,
 * не піднімаючи Home Assistant.
 */
object HomeAssistantMapper {

    /** Домени, які лаунчер показує. Решта (`automation`, `person`, `update`…) — шум на TV. */
    private val SUPPORTED_DOMAINS = mapOf(
        "light" to SmartDeviceKind.LIGHT,
        "switch" to SmartDeviceKind.SWITCH,
        "scene" to SmartDeviceKind.SCENE,
        "climate" to SmartDeviceKind.CLIMATE,
        "cover" to SmartDeviceKind.COVER,
        "fan" to SmartDeviceKind.SWITCH,
        "sensor" to SmartDeviceKind.SENSOR,
        "binary_sensor" to SmartDeviceKind.SENSOR,
    )

    /** Стани, які HA вважає «недоступно»: показувати їх як вимкнені було б брехнею. */
    private val UNAVAILABLE_STATES = setOf("unavailable", "unknown")

    /**
     * Сенсори, які має сенс бачити з дивана. Решта — струм, напруга, сигнал Wi-Fi, ресурс
     * фільтра, схід сонця — діагностика: у HA її позначено не всюди (Xiaomi, принтер), тож
     * беремо білий список класів, а не чорний.
     */
    private val SENSOR_CLASSES = setOf(
        "temperature", "humidity", "pm25", "pm10", "carbon_dioxide",
        "door", "window", "opening", "moisture", "smoke", "gas",
    )

    /** Кімната, прихованість і пристрій сутності — з [META_TEMPLATE]. */
    data class Meta(val room: String?, val hidden: Boolean, val device: String?, val roomOrder: Int = 999)

    /**
     * Шаблон HA, що одним REST-запитом віддає для кожної сутності кімнату, «приховано» й
     * назву пристрою: у `/api/states` їх немає, а реєстр — лише через WebSocket.
     */
    const val META_TEMPLATE =
        "{%- set ns = namespace(o=[]) -%}{%- set names = areas() | map('area_name') | list -%}" +
            "{%- for s in states if s.domain in ['light','switch','fan','scene','climate','cover','sensor','binary_sensor'] -%}" +
            "{%- set ns.o = ns.o + [[s.entity_id, area_name(s.entity_id), is_hidden_entity(s.entity_id), " +
            "device_attr(s.entity_id, 'name_by_user') or device_attr(s.entity_id, 'name'), " +
            "names.index(area_name(s.entity_id)) if area_name(s.entity_id) else 999]] -%}" +
            "{%- endfor -%}{{ ns.o | tojson }}"

    fun parseMeta(body: String): Map<String, Meta> {
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyMap()
        return (0 until array.length()).mapNotNull { i ->
            val row = array.optJSONArray(i) ?: return@mapNotNull null
            row.optString(0) to Meta(
                room = row.optString(1).takeUnless { row.isNull(1) || it.isBlank() },
                hidden = row.optBoolean(2),
                device = row.optString(3).takeUnless { row.isNull(3) || it.isBlank() },
                // Порядок кімнат — як у HA (Налаштування → Зони), а не за абеткою.
                roomOrder = row.optInt(4, 999),
            )
        }.toMap()
    }

    fun parseStates(
        body: String,
        meta: Map<String, Meta> = emptyMap(),
        limit: Int = 60,
        texts: SmartHomeTexts,
    ): List<SmartDevice> {
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()

        val items = (0 until array.length()).mapNotNull(array::optJSONObject)
        // «Тепле світло» й «Холодне світло» — канали групи «Люстра в спальні»: показуємо групу.
        // Лише групи світла/вимикачів/вентиляторів: `entity_id` є й у сцен та старих
        // `group.*`, і тоді все, чим керує сцена «Вечір», зникало б з екрана.
        val members = items.flatMap { item ->
            if (item.optString("entity_id").substringBefore('.') !in GROUP_DOMAINS) return@flatMap emptyList()
            val ids = item.optJSONObject("attributes")?.optJSONArray("entity_id") ?: return@flatMap emptyList()
            (0 until ids.length()).map(ids::optString)
        }.toSet()
        val byId = items.associateBy { it.optString("entity_id") }
        // Принтер (Bambu й подібні: `sensor.<x>_print_status`) — одна картка замість десятка
        // сенсорів; його температури, прогрес і час живуть усередині неї.
        val printers = byId.keys.mapNotNull { PRINT_STATUS.matchEntire(it)?.groupValues?.get(1) }
        return items
            .filter { it.optString("entity_id") !in members }
            .filterNot { item -> printers.any { item.optString("entity_id").startsWith("sensor.${it}_") } }
            .mapNotNull { toDevice(it, meta, texts) }
            .plus(printers.mapNotNull { printer(it, byId, meta, texts) })
            // Кімнатами в порядку HA (без кімнати — у кінці); у кімнаті спершу те, чим керують.
            .sortedWith(
                compareBy<SmartDevice>({ meta[it.entityId]?.roomOrder ?: 999 }, { it.room?.lowercase() })
                    .thenBy { !it.isControllable }
                    .thenBy { it.name.lowercase() },
            )
            .take(limit)
    }

    private fun toDevice(json: JSONObject, metas: Map<String, Meta>, texts: SmartHomeTexts): SmartDevice? {
        val entityId = json.optString("entity_id").takeIf { it.isNotBlank() } ?: return null
        val kind = SUPPORTED_DOMAINS[entityId.substringBefore('.')] ?: return null
        val state = json.optString("state")
        if (state in UNAVAILABLE_STATES) return null

        val attributes = json.optJSONObject("attributes")
        val name = attributes?.optString("friendly_name")?.takeIf { it.isNotBlank() }
            ?: entityId.substringAfter('.').replace('_', ' ')
        val meta = metas[entityId]
        // Приховане HA сам не показує: це вимикачі, які обгорнуто в світло (switch_as_x).
        if (meta?.hidden == true) return null
        // «[тех] реле кухня» — службове.
        if (name.startsWith("[")) return null
        // Ресурс фільтра очисника — лише коли його час міняти; тоді це застереження.
        if (kind == SmartDeviceKind.SENSOR && "filter_life" in entityId) {
            val left = state.toDoubleOrNull() ?: return null
            if (left > FILTER_WARN_PERCENT) return null
            return SmartDevice(
                entityId = entityId, name = name, kind = kind, state = state, isOn = false,
                room = meta?.room, detail = texts.get(R.string.smarthome_filter_replace, left.toInt()), warning = true,
            )
        }
        if (kind == SmartDeviceKind.SENSOR && attributes?.optString("device_class") !in SENSOR_CLASSES) return null
        // «Очисник у спальні: звук», «Підсвітка Overcharge protection» — налаштування
        // пристрою, а не сам пристрій: назва = пристрій + уточнення. Лише для вимикачів:
        // сенсори вже відібрано вище за класом, а «Датчик кухня Температура» — показник.
        val device = meta?.device
        if (kind == SmartDeviceKind.SWITCH && device != null && name.length > device.length &&
            name.startsWith(device)
        ) return null

        return SmartDevice(
            entityId = entityId,
            name = name,
            kind = kind,
            state = state,
            isOn = state.equals("on", ignoreCase = true) || state.equals("open", ignoreCase = true),
            unit = attributes?.optString("unit_of_measurement")?.takeIf { it.isNotBlank() },
            room = meta?.room,
            icon = attributes?.optString("icon")?.takeIf { it.isNotBlank() },
        )
    }

    private val GROUP_DOMAINS = setOf("light", "switch", "fan")
    private val PRINT_STATUS = Regex("""sensor\.(.+)_print_status""")
    private const val FILTER_WARN_PERCENT = 10.0

    private fun printer(
        prefix: String,
        byId: Map<String, JSONObject>,
        metas: Map<String, Meta>,
        texts: SmartHomeTexts,
    ): SmartDevice? {
        val statusId = "sensor.${prefix}_print_status"
        fun value(suffix: String) = byId["sensor.${prefix}_$suffix"]?.optString("state")
            ?.takeUnless { it in UNAVAILABLE_STATES || it.isBlank() }
        val status = value("print_status") ?: return null
        val online = byId["binary_sensor.${prefix}_online"]?.optString("state")
        val meta = metas[statusId]
        val printing = status in setOf("running", "pause", "prepare", "slicing")
        val progress = value("print_progress")?.toFloatOrNull()
        val text = texts.get(
            when {
                online == "off" -> R.string.smarthome_printer_offline
                status == "running" -> R.string.smarthome_printer_printing
                status == "pause" -> R.string.smarthome_printer_paused
                status == "prepare" || status == "slicing" -> R.string.smarthome_printer_preparing
                status == "finish" -> R.string.smarthome_printer_finished
                status == "failed" -> R.string.smarthome_printer_failed
                else -> R.string.smarthome_printer_idle
            },
        )
        val detail = if (!printing) text else listOfNotNull(
            text,
            progress?.let { "${it.toInt()} %" },
            remaining(byId["sensor.${prefix}_remaining_time"], texts)?.let { texts.get(R.string.smarthome_remaining, it) },
        ).joinToString(" · ")
        val temps = if (!printing) null else listOfNotNull(
            value("nozzle_temperature")?.toDoubleOrNull()?.let { texts.get(R.string.smarthome_nozzle, it.toInt()) },
            value("bed_temperature")?.toDoubleOrNull()?.let { texts.get(R.string.smarthome_bed, it.toInt()) },
        ).joinToString(" · ").ifBlank { null }
        return SmartDevice(
            entityId = statusId,
            name = meta?.device ?: texts.get(R.string.smarthome_printer),
            kind = SmartDeviceKind.PRINTER,
            state = status,
            isOn = printing,
            room = meta?.room,
            detail = detail,
            extra = temps,
            progress = if (printing) progress?.div(100f) else null,
            warning = status == "failed",
        )
    }

    /** «1.25 h» → «1 год 15 хв»; Bambu віддає години, інші — хвилини. */
    internal fun remaining(json: JSONObject?, texts: SmartHomeTexts): String? {
        val raw = json?.optString("state")?.toDoubleOrNull() ?: return null
        val unit = json.optJSONObject("attributes")?.optString("unit_of_measurement")
        val minutes = (if (unit == "h") raw * 60 else if (unit == "s") raw / 60 else raw).toInt()
        if (minutes <= 0) return null
        return listOfNotNull(
            (minutes / 60).takeIf { it > 0 }?.let { texts.get(R.string.smarthome_hours, it) },
            (minutes % 60).takeIf { it > 0 }?.let { texts.get(R.string.smarthome_minutes, it) },
        ).joinToString(" ")
    }

    /**
     * Яку службу HA викликати для перемикання.
     * Сцена не має «вимкнути», тому для неї це завжди `turn_on`.
     */
    /** Послуга HA, що ставить [on] прямо — повтор нічого не перемикає назад. */
    fun powerService(device: SmartDevice, on: Boolean): Pair<String, String>? = when (device.kind) {
        SmartDeviceKind.SENSOR, SmartDeviceKind.PRINTER -> null
        SmartDeviceKind.SCENE -> if (on) "scene" to "turn_on" else null
        SmartDeviceKind.COVER -> "cover" to if (on) "open_cover" else "close_cover"
        SmartDeviceKind.CLIMATE -> "climate" to if (on) "turn_on" else "turn_off"
        else -> device.domain to if (on) "turn_on" else "turn_off"
    }

    fun toggleService(device: SmartDevice): Pair<String, String>? = when (device.kind) {
        SmartDeviceKind.SENSOR, SmartDeviceKind.PRINTER -> null
        SmartDeviceKind.SCENE -> "scene" to "turn_on"
        SmartDeviceKind.CLIMATE -> "climate" to if (device.isOn) "turn_off" else "turn_on"
        SmartDeviceKind.COVER -> "cover" to if (device.isOn) "close_cover" else "open_cover"
        else -> device.domain to if (device.isOn) "turn_off" else "turn_on"
    }
}
