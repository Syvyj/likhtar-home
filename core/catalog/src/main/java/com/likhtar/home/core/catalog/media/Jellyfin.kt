package com.likhtar.home.core.catalog.media

import com.likhtar.home.core.data.http
import com.likhtar.home.core.data.text
import java.net.URLEncoder
import org.json.JSONObject

/** Вхід на сервер Jellyfin: як він себе називає, чий це вхід і токен. */
data class JellyfinLogin(val serverName: String, val userId: String, val token: String)

/**
 * Jellyfin через його REST API, без SDK. На відміну від DLNA, сервер сам знає, що за фільм у
 * файлі: TMDB id лежить у `ProviderIds`, тож назву з імені файлу вгадувати не треба.
 *
 * Токен — у [MediaSource.password] (шифрується разом зі списком джерел), id користувача — у
 * [MediaSource.user], адреса сервера — у [MediaSource.address].
 */
internal object Jellyfin : MediaServer {
    private const val TIMEOUT_MS = 10_000
    private const val LIST_TIMEOUT_MS = 60_000
    private const val TICKS_PER_MS = 10_000L

    private fun normalize(address: String) = MediaServer.normalize(address)

    /** Вхід логіном і паролем. Невдача — виняток із текстом для рядка в налаштуваннях. */
    fun login(address: String, user: String, password: String, deviceId: String): JellyfinLogin {
        val base = normalize(address)
        val body = JSONObject().put("Username", user).put("Pw", password).toString()
        val reply = http(
            "$base/Users/AuthenticateByName", TIMEOUT_MS, method = "POST",
            headers = mapOf("Content-Type" to "application/json", "Authorization" to auth(deviceId, null)),
            body = body,
        ) { c -> c.responseCode to (if (c.responseCode in 200..299) c.text() else null) }
        val (code, text) = reply
        if (code == 401) error("невірний логін або пароль")
        if (text == null) error("сервер відповів $code")
        val o = JSONObject(text)
        val token = o.optString("AccessToken")
        val userId = o.optJSONObject("User")?.optString("Id").orEmpty()
        if (token.isBlank() || userId.isBlank()) error("сервер не дав токена")
        val serverName = o.optString("ServerName").ifBlank { "Jellyfin" }
        return JellyfinLogin(serverName, userId, token)
    }

    override fun walk(source: MediaSource): List<LocalFile> {
        val url = "${normalize(source.address)}/Users/${enc(source.user)}/Items" +
            "?Recursive=true&IncludeItemTypes=Movie,Series,Episode&Fields=ProviderIds,MediaSources&EnableImages=false"
        val text = http(url, TIMEOUT_MS, LIST_TIMEOUT_MS, headers = mapOf("Accept" to "application/json", "Authorization" to auth(source.id, source.password))) { c ->
            if (c.responseCode == 401) error("вхід застарів — додайте сервер знову")
            if (c.responseCode != 200) error("сервер відповів ${c.responseCode}")
            c.text()
        }
        return parseItems(text, source)
    }

    override fun resume(source: MediaSource): List<ServerResume> {
        val text = get(source, "/Users/${enc(source.user)}/Items/Resume?IncludeItemTypes=Movie,Episode&EnableImages=false") ?: return emptyList()
        return parseResume(text)
    }

    override fun position(source: MediaSource, itemId: String): Long? {
        val text = get(source, "/Users/${enc(source.user)}/Items/${enc(itemId)}") ?: return null
        return JSONObject(text).optJSONObject("UserData")?.optLong("PlaybackPositionTicks")?.div(TICKS_PER_MS) ?: 0
    }

    override fun reportStopped(source: MediaSource, itemId: String, positionMs: Long) {
        val body = JSONObject().put("ItemId", itemId).put("PositionTicks", positionMs * TICKS_PER_MS).toString()
        post(source, "/Sessions/Playing/Stopped", body)
    }

    override fun setPlayed(source: MediaSource, itemId: String, played: Boolean) {
        val path = "/Users/${enc(source.user)}/PlayedItems/${enc(itemId)}"
        if (played) post(source, path, null) else send(source, path, "DELETE", null)
    }

    /** `static=true` — без перекодування, плеєр отримує оригінал. */
    override fun streamUrl(source: MediaSource, file: LocalFile): String =
        "${normalize(source.address)}/Videos/${enc(file.path)}/stream?static=true&api_key=${enc(source.password)}"

    internal fun parseResume(text: String): List<ServerResume> {
        val items = JSONObject(text).optJSONArray("Items") ?: return emptyList()
        return (0 until items.length()).mapNotNull(items::optJSONObject).map { o ->
            val data = o.optJSONObject("UserData")
            ServerResume(
                itemId = o.optString("Id"),
                positionMs = (data?.optLong("PlaybackPositionTicks") ?: 0) / TICKS_PER_MS,
                runtimeMs = o.optLong("RunTimeTicks") / TICKS_PER_MS,
                playedAt = data?.optString("LastPlayedDate")?.let(::epochMs) ?: 0,
            )
        }.filter { it.positionMs > 0 }
    }

    internal fun parseItems(text: String, source: MediaSource): List<LocalFile> {
        val items = JSONObject(text).optJSONArray("Items") ?: return emptyList()
        val all = (0 until items.length()).mapNotNull(items::optJSONObject)
        val seriesTmdb = all.filter { it.optString("Type") == "Series" }
            .associate { it.optString("Id") to tmdbOf(it) }
        return all.mapNotNull { o ->
            val type = o.optString("Type")
            val size = o.optJSONArray("MediaSources")?.optJSONObject(0)?.optLong("Size") ?: 0
            when (type) {
                "Movie" -> LocalFile(
                    sourceId = source.id, path = o.optString("Id"), name = o.optString("Name"),
                    size = size, sourceName = source.name, tmdbId = tmdbOf(o), tv = false,
                    played = o.optJSONObject("UserData")?.optBoolean("Played") == true,
                )
                "Episode" -> LocalFile(
                    sourceId = source.id, path = o.optString("Id"),
                    name = "${o.optString("SeriesName")} — ${o.optString("Name")}",
                    size = size, season = o.optInt("ParentIndexNumber"), episode = o.optInt("IndexNumber"),
                    sourceName = source.name, tmdbId = seriesTmdb[o.optString("SeriesId")] ?: 0, tv = true,
                )
                else -> null
            }
        }
    }

    private fun get(source: MediaSource, path: String): String? =
        http(normalize(source.address) + path, TIMEOUT_MS, headers = mapOf("Accept" to "application/json", "Authorization" to auth(source.id, source.password))) { c ->
            if (c.responseCode == 200) c.text() else null
        }

    private fun post(source: MediaSource, path: String, json: String?) = send(source, path, "POST", json)

    private fun send(source: MediaSource, path: String, method: String, json: String?) {
        val headers = buildMap {
            put("Authorization", auth(source.id, source.password))
            if (json != null) put("Content-Type", "application/json")
        }
        val code = http(normalize(source.address) + path, TIMEOUT_MS, method = method, headers = headers, body = json) { it.responseCode }
        if (code !in 200..299) error("сервер відповів $code")
    }

    private fun epochMs(iso: String): Long? = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()

    private fun tmdbOf(o: JSONObject): Int = o.optJSONObject("ProviderIds")?.optString("Tmdb")?.toIntOrNull() ?: 0

    private fun auth(deviceId: String, token: String?): String =
        "MediaBrowser Client=\"Likhtar Home\", Device=\"Android TV\", DeviceId=\"likhtar-$deviceId\", Version=\"1\"" +
            (token?.let { ", Token=\"$it\"" } ?: "")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
