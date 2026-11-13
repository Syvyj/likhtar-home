package com.likhtar.home.core.tv

import org.json.JSONArray
import org.json.JSONObject

/**
 * Той самий розклад на іншу хвилину: поточною стає передача, що йде о [now], решта — далі.
 * `null` — усе в ньому вже скінчилось. Потрібно старій програмі, яку показуємо, доки XMLTV
 * розбирається: без цього канал, де передача змінилась, зникав зі списку на хвилину.
 */
fun ChannelSchedule.rolledTo(now: Long): ChannelSchedule? {
    val all = listOfNotNull(live?.let { EpgProgramme(it.title, it.startMillis, it.endMillis, it.description, it.image) }) + upcoming
    // Кінець 0 — провайдер не знає, коли скінчиться (TVP, YouTube): іде, доки не почнеться наступна.
    val on = all.lastOrNull { it.startMillis <= now && (it.endMillis > now || (it.endMillis == 0L && next(all, it, now))) }
    val next = all.filter { it.startMillis > now }
    if (on == null && next.isEmpty()) return null
    return copy(
        live = on?.let { NowOnAir(title = it.title, description = it.description, image = it.image, startMillis = it.startMillis, endMillis = it.endMillis) },
        upcoming = next,
    )
}

private fun next(all: List<EpgProgramme>, p: EpgProgramme, now: Long) = all.none { it.startMillis > p.startMillis && it.startMillis <= now }

/**
 * Остання розібрана програма плейлиста — на диск: розбір XMLTV на приставці триває хвилину, і
 * після холодного старту всі канали плейлиста цю хвилину стояли «Прямим ефіром». Архів (минуле)
 * не зберігаємо — він приїде з повним розбором.
 */
internal fun encodeGuideSnapshot(source: String, schedules: Map<String, ChannelSchedule>): String {
    val channels = JSONObject()
    schedules.forEach { (url, s) ->
        val rows = JSONArray()
        (listOfNotNull(s.live?.let { EpgProgramme(it.title, it.startMillis, it.endMillis, it.description) }) + s.upcoming).forEach { p ->
            rows.put(JSONArray().put(p.title).put(p.startMillis).put(p.endMillis).put(p.description.take(SNAPSHOT_TEXT)))
        }
        if (rows.length() > 0) channels.put(url, rows)
    }
    return JSONObject().put("source", source).put("channels", channels).toString()
}

/** Знімок для [source] на хвилину [now]; чужий (інша адреса програми) чи зіпсований — порожньо. */
internal fun decodeGuideSnapshot(text: String, source: String, now: Long): Map<String, ChannelSchedule> = runCatching {
    val root = JSONObject(text)
    if (root.optString("source") != source) return emptyMap()
    val channels = root.getJSONObject("channels")
    buildMap {
        channels.keys().forEach { url ->
            val rows = channels.getJSONArray(url)
            val list = (0 until rows.length()).map { i ->
                rows.getJSONArray(i).let { EpgProgramme(it.getString(0), it.getLong(1), it.getLong(2), it.optString(3)) }
            }
            ChannelSchedule(live = null, upcoming = list).rolledTo(now)?.let { put(url, it) }
        }
    }
}.getOrDefault(emptyMap())

private const val SNAPSHOT_TEXT = 400
