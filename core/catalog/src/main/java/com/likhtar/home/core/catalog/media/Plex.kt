package com.likhtar.home.core.catalog.media

import com.likhtar.home.core.data.http
import com.likhtar.home.core.data.text
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/** Код входу plex.tv: людина вводить [code] на plex.tv/link, лаунчер чекає токен за [id]. */
data class PlexPin(val id: Long, val code: String)

/** Сервер з акаунта Plex: як зветься, за якою адресою й з яким токеном до нього ходити. */
data class PlexServer(val name: String, val address: String, val token: String)

/**
 * Plex через його HTTP API, без SDK. Як і Jellyfin, сервер сам знає TMDB id (`Guid` з
 * `tmdb://…` при `includeGuids=1`) і пам'ятає позицію (`viewOffset`) та перегляди
 * (`viewCount`).
 *
 * Вхід — кодом plex.tv/link, як у застосунках самого Plex: пароль на телевізорі не набирається.
 * Сервер у домашній мережі, якому дозволено пускати без входу (`allowedNetworks`), можна
 * додати й просто адресою — тоді токен порожній.
 *
 * Токен — у [MediaSource.password], адреса — у [MediaSource.address]; [MediaSource.user] не
 * потрібен.
 */
internal object Plex : MediaServer {
    private const val TIMEOUT_MS = 10_000
    private const val LIST_TIMEOUT_MS = 60_000
    private const val PLEX_TV = "https://plex.tv/api/v2"
    private const val LIBRARY = "com.plexapp.plugins.library"

    /** Новий код входу. */
    fun createPin(clientId: String): PlexPin {
        val text = http("$PLEX_TV/pins?strong=false", TIMEOUT_MS, method = "POST", headers = tvHeaders(clientId), body = "") { c ->
            if (c.responseCode !in 200..299) error("plex.tv відповів ${c.responseCode}")
            c.text()
        }
        val o = JSONObject(text)
        return PlexPin(o.getLong("id"), o.getString("code"))
    }

    /** Токен, якщо людина вже ввела код; `null` — ще ні. */
    fun pinToken(clientId: String, pin: PlexPin): String? {
        val text = http("$PLEX_TV/pins/${pin.id}", TIMEOUT_MS, headers = tvHeaders(clientId)) { c ->
            if (c.responseCode == 404) error("код застарів — спробуйте ще раз")
            if (c.responseCode != 200) null else c.text()
        } ?: return null
        return JSONObject(text).optString("authToken").takeIf { it.isNotBlank() && it != "null" }
    }

    /** Сервери акаунта: адреса в домашній мережі — першою, бо так файл іде напряму. */
    fun servers(clientId: String, token: String): List<PlexServer> {
        val text = http("$PLEX_TV/resources?includeHttps=1", TIMEOUT_MS, headers = tvHeaders(clientId) + ("X-Plex-Token" to token)) { c ->
            if (c.responseCode != 200) error("plex.tv відповів ${c.responseCode}")
            c.text()
        }
        return parseServers(text)
    }

    /** Сервер за адресою без входу: перевірити, що відповідає, і взяти його назву. */
    fun direct(address: String, token: String = ""): PlexServer {
        val base = MediaServer.normalize(address)
        val text = http("$base/", TIMEOUT_MS, headers = headers(token)) { c ->
            if (c.responseCode == 401) error("сервер просить входу — увійдіть через plex.tv")
            if (c.responseCode != 200) error("сервер відповів ${c.responseCode}")
            c.text()
        }
        val name = JSONObject(text).optJSONObject("MediaContainer")?.optString("friendlyName").orEmpty().ifBlank { "Plex" }
        return PlexServer(name, base, token)
    }

    override fun walk(source: MediaSource): List<LocalFile> {
        val sections = items(get(source, "/library/sections", LIST_TIMEOUT_MS) ?: error("сервер не відповідає"), "Directory")
        val out = mutableListOf<LocalFile>()
        for (section in sections) {
            val key = section.optString("key")
            when (section.optString("type")) {
                "movie" -> get(source, "/library/sections/$key/all?type=1&includeGuids=1", LIST_TIMEOUT_MS)
                    ?.let { out += parseMovies(it, source) }
                "show" -> {
                    val shows = get(source, "/library/sections/$key/all?type=2&includeGuids=1", LIST_TIMEOUT_MS) ?: continue
                    val episodes = get(source, "/library/sections/$key/all?type=4", LIST_TIMEOUT_MS) ?: continue
                    out += parseEpisodes(episodes, showTmdb(shows), source)
                }
            }
        }
        return out
    }

    /** [LocalFile.url] — шлях до частини файлу на сервері (`/library/parts/…`), без токена. */
    override fun streamUrl(source: MediaSource, file: LocalFile): String =
        MediaServer.normalize(source.address) + file.url + if (source.password.isBlank()) "" else "?X-Plex-Token=${enc(source.password)}"

    override fun resume(source: MediaSource): List<ServerResume> =
        get(source, "/library/onDeck")?.let(::parseResume).orEmpty()

    override fun position(source: MediaSource, itemId: String): Long? {
        val text = get(source, "/library/metadata/${enc(itemId)}") ?: return null
        return items(text, "Metadata").firstOrNull()?.optLong("viewOffset") ?: 0
    }

    override fun reportStopped(source: MediaSource, itemId: String, positionMs: Long) =
        send(source, "/:/progress?key=${enc(itemId)}&identifier=$LIBRARY&time=$positionMs&state=stopped")

    override fun setPlayed(source: MediaSource, itemId: String, played: Boolean) =
        send(source, "/:/${if (played) "scrobble" else "unscrobble"}?key=${enc(itemId)}&identifier=$LIBRARY")

    internal fun parseServers(text: String): List<PlexServer> {
        val all = JSONArray(text)
        return (0 until all.length()).mapNotNull(all::optJSONObject)
            .filter { "server" in it.optString("provides") }
            .mapNotNull { r ->
                val connections = r.optJSONArray("connections") ?: return@mapNotNull null
                val list = (0 until connections.length()).mapNotNull(connections::optJSONObject).filterNot { it.optBoolean("relay") }
                val best = list.firstOrNull { it.optBoolean("local") } ?: list.firstOrNull() ?: return@mapNotNull null
                PlexServer(r.optString("name").ifBlank { "Plex" }, best.optString("uri"), r.optString("accessToken"))
            }
    }

    internal fun parseMovies(text: String, source: MediaSource): List<LocalFile> = items(text, "Metadata").mapNotNull { m ->
        val part = partOf(m) ?: return@mapNotNull null
        LocalFile(
            sourceId = source.id, path = m.optString("ratingKey"), name = m.optString("title"),
            url = part.optString("key"), size = part.optLong("size"), sourceName = source.name,
            tmdbId = tmdbOf(m), tv = false, played = m.optInt("viewCount") > 0,
        )
    }

    internal fun showTmdb(text: String): Map<String, Int> =
        items(text, "Metadata").associate { it.optString("ratingKey") to tmdbOf(it) }

    internal fun parseEpisodes(text: String, shows: Map<String, Int>, source: MediaSource): List<LocalFile> =
        items(text, "Metadata").mapNotNull { m ->
            val part = partOf(m) ?: return@mapNotNull null
            LocalFile(
                sourceId = source.id, path = m.optString("ratingKey"),
                name = "${m.optString("grandparentTitle")} — ${m.optString("title")}",
                url = part.optString("key"), size = part.optLong("size"),
                season = m.optInt("parentIndex"), episode = m.optInt("index"), sourceName = source.name,
                tmdbId = shows[m.optString("grandparentRatingKey")] ?: 0, tv = true,
            )
        }

    /** «На черзі» Plex — це й недодивлене, і наступні серії; продовжуємо лише перше. */
    internal fun parseResume(text: String): List<ServerResume> = items(text, "Metadata")
        .map { ServerResume(it.optString("ratingKey"), it.optLong("viewOffset"), it.optLong("duration"), it.optLong("lastViewedAt") * 1000) }
        .filter { it.positionMs > 0 }

    private fun items(text: String, field: String): List<JSONObject> {
        val array = JSONObject(text).optJSONObject("MediaContainer")?.optJSONArray(field) ?: return emptyList()
        return (0 until array.length()).mapNotNull(array::optJSONObject)
    }

    private fun partOf(m: JSONObject): JSONObject? =
        m.optJSONArray("Media")?.optJSONObject(0)?.optJSONArray("Part")?.optJSONObject(0)

    private fun tmdbOf(m: JSONObject): Int {
        val guids = m.optJSONArray("Guid") ?: return 0
        return (0 until guids.length()).mapNotNull { guids.optJSONObject(it)?.optString("id") }
            .firstOrNull { it.startsWith("tmdb://") }?.removePrefix("tmdb://")?.toIntOrNull() ?: 0
    }

    private fun get(source: MediaSource, path: String, readTimeoutMs: Int = TIMEOUT_MS): String? =
        http(MediaServer.normalize(source.address) + path, TIMEOUT_MS, readTimeoutMs, headers = headers(source.password)) { c ->
            if (c.responseCode == 401) error("вхід застарів — додайте сервер знову")
            if (c.responseCode == 200) c.text() else null
        }

    private fun send(source: MediaSource, path: String) {
        val code = http(MediaServer.normalize(source.address) + path, TIMEOUT_MS, headers = headers(source.password)) { it.responseCode }
        if (code !in 200..299) error("сервер відповів $code")
    }

    private fun headers(token: String): Map<String, String> = buildMap {
        put("Accept", "application/json")
        if (token.isNotBlank()) put("X-Plex-Token", token)
    }

    private fun tvHeaders(clientId: String) = mapOf(
        "Accept" to "application/json",
        "X-Plex-Product" to "Likhtar Home",
        "X-Plex-Client-Identifier" to "likhtar-$clientId",
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
