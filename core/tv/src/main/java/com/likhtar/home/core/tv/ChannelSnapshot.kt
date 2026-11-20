package com.likhtar.home.core.tv

import org.json.JSONArray
import org.json.JSONObject

/**
 * D2: свої канали ТБ Ефіру ([channels]) і які з них — із плейлиста ([playlistUrls]) для знімка на
 * диску. Розбір плейлиста й портал — ~3 с на холодному старті; перший кадр бере знімок,
 * свіже приходить другим проходом.
 */
data class OwnChannels(val channels: List<ChannelLink>, val playlistUrls: Set<String>)

fun encodeOwnChannels(own: OwnChannels): String = JSONObject()
    .put("playlist", JSONArray(own.playlistUrls.toList()))
    .put("channels", JSONArray().apply {
        own.channels.forEach { c ->
            put(
                JSONObject().put("name", c.name).put("pkg", c.packageName).put("url", c.url).put("cat", c.category)
                    .put("epg", c.epgId).put("image", c.image).put("logo", c.logo).put("mark", c.mark).put("key", c.key)
                    .put("variants", JSONArray().apply {
                        c.variants.forEach { v ->
                            put(JSONObject().put("title", v.title).put("year", v.year).put("description", v.description).put("meta", v.meta).put("app", v.app).put("url", v.url))
                        }
                    })
                    .apply { c.archive?.let { a -> put("archive", JSONObject().put("type", a.type).put("days", a.days).put("source", a.source)) } },
            )
        }
    })
    .toString()

fun decodeOwnChannels(raw: String): OwnChannels = runCatching {
    val root = JSONObject(raw)
    val playlist = root.optJSONArray("playlist") ?: JSONArray()
    val array = root.optJSONArray("channels") ?: JSONArray()
    OwnChannels(
        channels = (0 until array.length()).mapNotNull { i ->
            val c = array.optJSONObject(i) ?: return@mapNotNull null
            val variants = c.optJSONArray("variants") ?: JSONArray()
            ChannelLink(
                name = c.optString("name"), packageName = c.optString("pkg"), url = c.optString("url").ifBlank { return@mapNotNull null },
                category = c.optString("cat"), epgId = c.optString("epg"), image = c.optString("image"), logo = c.optString("logo"),
                mark = c.optString("mark"), key = c.optString("key"),
                variants = (0 until variants.length()).mapNotNull { j ->
                    val v = variants.optJSONObject(j) ?: return@mapNotNull null
                    ChannelVariant(v.optString("title"), v.optString("year"), v.optString("description"), v.optString("meta"), v.optString("app"), v.optString("url"))
                },
                archive = c.optJSONObject("archive")?.let { a -> ChannelArchive(a.optString("type"), a.optInt("days"), a.optString("source")) },
            )
        },
        playlistUrls = (0 until playlist.length()).map(playlist::optString).toSet(),
    )
}.getOrDefault(OwnChannels(emptyList(), emptySet()))
