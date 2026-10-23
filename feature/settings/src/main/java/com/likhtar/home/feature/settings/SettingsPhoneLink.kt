package com.likhtar.home.feature.settings

import android.content.Context
import android.util.Log
import com.likhtar.home.core.catalog.media.MediaLibrary
import com.likhtar.home.core.catalog.media.MediaSource
import com.likhtar.home.core.catalog.media.SmbAddress
import com.likhtar.home.core.data.AiProviders
import com.likhtar.home.core.data.GeocodingRepository
import com.likhtar.home.core.data.LikhtarSettings
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.data.locale
import com.likhtar.home.core.data.lanAddress
import com.likhtar.home.core.model.Distribution
import com.likhtar.home.core.smarthome.SmartHomeRepository
import com.likhtar.home.core.tv.TvGuideRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Що відбувається з «Налаштувати з телефона» — для діалогу з QR. */
sealed interface PhoneSetupStatus {
    data object Waiting : PhoneSetupStatus
    data object Opened : PhoneSetupStatus
    /** Збережено — назви того, що змінилось. */
    data class Saved(val what: List<String>) : PhoneSetupStatus
}

/**
 * «Налаштувати з телефона»: довгі адреси, ключі й паролі набирати пультом — мука.
 * Приставка піднімає крихітний сервер у домашній мережі, QR веде на форму, телефон шле поля
 * POST-ом (не в адресі), а сюди вони лягають тими самими сховищами, що й у налаштуваннях.
 *
 * Шлях — випадковий на кожен показ QR, тож сусід по Wi‑Fi навмання не зайде. Збережені секрети
 * (токени, паролі, адреса плейлиста з токеном провайдера) на сторінку не повертаються: порожнє
 * поле — «лишити як є». Решта налаштувань — перемикачами й списками; сервера Ліхтаря й ключа TMDB
 * тут немає: це не налаштування для людей.
 *
 * ponytail: той самий мінімальний HTTP, що в HaPhoneLink; спільний сервер — коли телефонних
 * сторінок стане більше трьох.
 */
@Singleton
class SettingsPhoneLink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val media: MediaLibrary,
    private val smartHome: SmartHomeRepository,
    private val guide: TvGuideRepository,
    private val geocoding: GeocodingRepository,
) {
    /** Перемикач на сторінці телефона: те саме поле, що в налаштуваннях на ТВ. */
    private class Toggle(
        val key: String,
        val label: Int,
        val get: (LikhtarSettings) -> Boolean,
        val set: suspend (Boolean) -> Unit,
        val shown: (LikhtarSettings) -> Boolean = { true },
    )

    private val toggles = listOf(
        Toggle("weather", R.string.settings_home_show_weather, { it.showWeather }, settings::setShowWeather),
        Toggle("torch", R.string.settings_home_torch, { it.homeShowTorch }, settings::setHomeShowTorch),
        Toggle("voice", R.string.settings_home_voice, { it.voiceSearchOnTv }, settings::setVoiceSearchOnTv),
        Toggle("apps_pinned", R.string.settings_phone_apps_pinned, { it.homeAppsPinned }, settings::setHomeAppsPinned),
        Toggle("attribution", R.string.settings_wallpaper_attribution, { it.showWallpaperAttribution }, settings::setWallpaperAttribution),
        Toggle("without_ua", R.string.settings_watch_without_ua, { it.showWithoutUaAudio }, settings::setShowWithoutUaAudio, { it.locale.language == "uk" }),
        Toggle("hide_watched", R.string.settings_watch_hide_watched, { it.hideWatched }, settings::setHideWatched),
        Toggle("only_my_apps", R.string.settings_watch_only_my_apps, { it.onlyMyApps }, settings::setOnlyMyApps),
        Toggle("anime", R.string.settings_watch_anime, { it.showAnime }, settings::setShowAnime),
        Toggle("live_stills", R.string.settings_watch_live_stills, { it.titleLiveStills }, settings::setTitleLiveStills),
        Toggle("smart_home", R.string.settings_smart_home_toggle, { it.smartHomeEnabled }, settings::setSmartHomeEnabled),
        Toggle("ha_voice", R.string.settings_ha_voice, { it.voiceCommandsEnabled }, settings::setVoiceCommandsEnabled),
    )

    private val _status = MutableStateFlow<PhoneSetupStatus>(PhoneSetupStatus.Waiting)
    val status: StateFlow<PhoneSetupStatus> = _status

    @Volatile private var base: String? = null
    @Volatile private var nonce: String = ""

    /** Адреса для QR; `null` — приставка не в локальній мережі чи порти зайняті. */
    @Synchronized
    fun start(): String? {
        nonce = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        _status.value = PhoneSetupStatus.Waiting
        val root = base ?: run {
            val ip = lanAddress() ?: return null
            val server = PORTS.firstNotNullOfOrNull { runCatching { ServerSocket(it) }.getOrNull() } ?: return null
            thread(name = "settings-link", isDaemon = true) {
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { runCatching { serve(socket) }.onFailure { Log.w(TAG, "запит телефону", it) } }
                }
            }
            "http://$ip:${server.localPort}/".also { base = it }
        }
        return root + nonce + "/"
    }

    private fun serve(socket: Socket): Unit = socket.use { s ->
        s.soTimeout = 15_000
        val input = s.getInputStream().buffered()
        val request = input.line()?.split(' ') ?: return
        val method = request.getOrNull(0)
        val path = request.getOrNull(1)?.substringBefore('?') ?: return
        var length = 0
        while (true) {
            val header = input.line() ?: break
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val live = nonce.isNotEmpty() && path.startsWith("/$nonce/")
        val reply = when {
            !live -> Reply(404, "Not found")
            path == "/$nonce/" && method == "GET" -> {
                _status.value = PhoneSetupStatus.Opened
                Reply(200, runBlocking { formPage() })
            }
            path == "/$nonce/save" && method == "POST" && length in 1..MAX_BODY -> {
                val body = ByteArray(length).also { var read = 0; while (read < length) { val n = input.read(it, read, length - read); if (n < 0) break; read += n } }
                val fields = String(body, Charsets.UTF_8).split('&').mapNotNull { pair ->
                    pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> k to URLDecoder.decode(v, "UTF-8").trim() }
                }.toMap()
                val (saved, problems) = runBlocking { apply(fields) }
                if (saved.isNotEmpty()) _status.value = PhoneSetupStatus.Saved(saved)
                Reply(200, runBlocking { formPage(saved = saved, problems = problems) })
            }
            // Вхід у Home Assistant — та сама сторінка, що з налаштувань Смарт дому.
            path == "/$nonce/ha" -> runBlocking { smartHome.startPhoneLink() }
                ?.let { Reply(302, "", location = it) } ?: Reply(404, "Not found")
            else -> Reply(404, "Not found")
        }
        val bytes = reply.body.toByteArray(Charsets.UTF_8)
        val status = when (reply.code) { 200 -> "200 OK"; 302 -> "302 Found"; else -> "404 Not Found" }
        s.getOutputStream().apply {
            write(
                ("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                    (reply.location?.let { "Location: $it\r\n" } ?: "") +
                    "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(),
            )
            write(bytes)
            flush()
        }
    }

    /** Порожнє поле — «не чіпати». Повертає, що збережено, і що не вийшло. */
    private suspend fun apply(f: Map<String, String>): Pair<List<String>, List<String>> {
        val saved = mutableListOf<String>()
        val problems = mutableListOf<String>()
        val current = settings.settings.first()
        f["playlist"]?.takeIf { it.isNotEmpty() }?.let {
            settings.setTvPlaylistUrl(it); saved += str(R.string.settings_tv_playlist)
        }
        f["epg"]?.takeIf { it.isNotEmpty() }?.let {
            settings.setTvEpgUrl(it); guide.refresh(); saved += str(R.string.settings_epg_title)
        }
        f["smb"]?.takeIf { it.isNotEmpty() }?.let { raw ->
            val address = SmbAddress.parse(raw)
            if (address == null) {
                problems += str(R.string.settings_media_smb_failed, raw)
            } else {
                val source = MediaSource(
                    media.newId(), MediaSource.Kind.SMB,
                    name = address.path.substringAfterLast('/').ifBlank { address.share },
                    address = "smb://${address.host}/${address.share}/${address.path}".trimEnd('/'),
                    user = f["smb_user"].orEmpty(), password = f["smb_password"].orEmpty(),
                )
                val error = media.check(source)
                if (error != null) problems += str(R.string.settings_media_smb_failed, error)
                else { media.add(source); media.rescan(); saved += str(R.string.settings_media_add_smb) }
            }
        }
        val haUrl = f["ha_url"].orEmpty()
        val haToken = f["ha_token"].orEmpty()
        if (haUrl.isNotEmpty() || haToken.isNotEmpty()) {
            settings.setSmartHomeCredentials(haUrl.ifEmpty { current.smartHomeUrl }, haToken.ifEmpty { current.smartHomeToken })
            saved += "Home Assistant"
        }
        // AI: інший провайдер — адреса, модель і ключ з нуля; той самий — порожнє поле лишає як було.
        f["ai_provider"]?.let { id ->
            val same = id == current.aiProvider
            val url = f["ai_url"].orEmpty().ifEmpty { if (same) current.aiUrl else "" }
            val model = f["ai_model"].orEmpty().ifEmpty { if (same) current.aiModel else "" }
            val key = f["ai_key"].orEmpty().ifEmpty { if (same) current.aiKey else "" }
            if (!same || url != current.aiUrl || model != current.aiModel || key != current.aiKey) {
                settings.setAi(id, url, model, key)
                saved += "AI"
                val provider = AiProviders.of(id)
                if (provider != null && provider.needsKey && key.isEmpty()) problems += str(R.string.settings_ai_need_key)
                if (provider != null && url.ifEmpty { provider.url }.isEmpty()) problems += str(R.string.settings_ai_need_url)
                if (provider != null && model.ifEmpty { provider.model }.isEmpty()) problems += str(R.string.settings_ai_need_model)
            }
        }
        // Перемикачі приходять лише позначеними; «toggles» каже, що блок справді був на сторінці.
        if (f["toggles"] == "1") {
            toggles.filter { it.shown(current) }.forEach { t ->
                val on = f[t.key] == "on"
                if (on != t.get(current)) { t.set(on); saved += str(t.label) }
            }
        }
        f["city"]?.takeIf { it.isNotEmpty() && it != current.weatherCity }?.let { city ->
            val place = runCatching { geocoding.search(city) }.getOrDefault(emptyList()).firstOrNull()
            if (place == null) problems += str(R.string.settings_msg_nothing_found, city)
            else { settings.setWeatherPlace(place.name, place.latitude, place.longitude); saved += str(R.string.settings_home_city) }
        }
        fun pick(name: String, now: Int, label: Int, set: suspend (Int) -> Unit) = f[name]?.toIntOrNull()?.takeIf { it != now }?.let { suspend { set(it); saved += str(label) } }
        listOfNotNull(
            pick("ambient", current.ambientTimeoutSeconds, R.string.settings_home_ambient, settings::setAmbientTimeoutSeconds),
            pick("wp_interval", current.wallpaperIntervalMinutes, R.string.settings_wallpaper_interval, settings::setWallpaperIntervalMinutes),
            pick("wp_dim", current.wallpaperDimPercent, R.string.settings_wallpaper_dim, settings::setWallpaperDimPercent),
        ).forEach { it() }
        return saved to problems
    }

    private suspend fun formPage(saved: List<String> = emptyList(), problems: List<String> = emptyList()): String {
        val s = settings.settings.first()
        /** Збережений секрет не показуємо — лише що він є. */
        fun keep(set: Boolean) = if (set) " placeholder=\"${str(R.string.settings_phone_kept)}\"" else ""
        // label — уже екранований рядок ресурсу (str).
        fun field(name: String, label: String, value: String = "", extra: String = "", type: String = "text") =
            "<label>$label<input name=\"$name\" type=\"$type\" value=\"${escape(value)}\"$extra " +
                "autocapitalize=\"off\" autocorrect=\"off\" spellcheck=\"false\"></label>"
        val notes = saved.joinToString("") { "<p class=\"ok\">✓ ${escape(it)}</p>" } +
            problems.joinToString("") { "<p class=\"err\">${escape(it)}</p>" }
        fun select(name: String, label: String, now: Int, options: List<Pair<Int, String>>) =
            "<label>$label<select name=\"$name\">" + options.joinToString("") { (v, t) ->
                "<option value=\"$v\"${if (v == now) " selected" else ""}>${escape(t)}</option>"
            } + "</select></label>"
        val switches = "<input type=\"hidden\" name=\"toggles\" value=\"1\">" + toggles.filter { it.shown(s) }.joinToString("") { t ->
            "<label class=\"sw\"><input type=\"checkbox\" name=\"${t.key}\"${if (t.get(s)) " checked" else ""}>${str(t.label)}</label>"
        }
        val home = "<h2>${str(R.string.settings_section_home)}</h2>" +
            field("city", str(R.string.settings_home_city), s.weatherCity) +
            select("ambient", str(R.string.settings_home_ambient), s.ambientTimeoutSeconds,
                listOf(30, 60, 180, 300, 0).map { it to if (it == 0) context.getString(R.string.settings_never) else if (it < 60) context.getString(R.string.settings_seconds, it) else context.getString(R.string.settings_minutes, it / 60) }) +
            select("wp_interval", str(R.string.settings_wallpaper_interval), s.wallpaperIntervalMinutes,
                listOf(5, 15, 30, 60).map { it to context.getString(R.string.settings_minutes, it) }) +
            select("wp_dim", str(R.string.settings_wallpaper_dim), s.wallpaperDimPercent,
                listOf(0, 15, 30, 45, 60).map { it to "$it %" })
        // Свій AI — лише публічна збірка (див. ConnectionsSection).
        val ai = if (!Distribution.store) "" else "<h2>AI</h2><p class=\"note\">${str(R.string.settings_ai_phone_note)}</p>" +
            "<label>${str(R.string.settings_ai_provider)}<select name=\"ai_provider\">" +
            (listOf("" to str(R.string.settings_ai_none)) + AiProviders.all.map { it.id to escape(it.title) }).joinToString("") { (id, title) ->
                "<option value=\"$id\"${if (id == s.aiProvider) " selected" else ""}>$title</option>"
            } + "</select></label>" +
            field("ai_model", str(R.string.settings_ai_model), s.aiModel, " placeholder=\"${str(R.string.settings_ai_model_hint)}\"") +
            field("ai_url", str(R.string.settings_ai_url), s.aiUrl, " placeholder=\"http://192.168.1.20:11434/v1\" inputmode=\"url\"") +
            field("ai_key", str(R.string.settings_ai_key), extra = keep(s.aiKey.isNotBlank()), type = "password")
        val personal = ai + home + "<h2>${str(R.string.settings_phone_switches)}</h2>" + switches
        return html(
            str(R.string.settings_phone_title),
            "<h1>${str(R.string.settings_phone_title)}</h1><p>${str(R.string.settings_phone_intro)}</p>$notes" +
                "<form action=\"save\" method=\"post\">" +
                "<h2>${str(R.string.settings_section_tv)}</h2>" +
                field("playlist", str(R.string.settings_tv_playlist), extra = keep(s.tvPlaylistUrl.isNotBlank()) + " inputmode=\"url\"") +
                field("epg", str(R.string.settings_epg_title), extra = keep(s.tvEpgUrl.isNotBlank()) + " inputmode=\"url\"") +
                "<h2>${str(R.string.settings_media_group)}</h2>" +
                field("smb", str(R.string.settings_media_smb_address), extra = " placeholder=\"${str(R.string.settings_media_smb_address_hint)}\"") +
                field("smb_user", str(R.string.settings_media_smb_user)) +
                field("smb_password", str(R.string.settings_media_smb_password), type = "password") +
                "<h2>Home Assistant</h2>" +
                "<a class=\"btn\" href=\"ha\">${str(R.string.settings_phone_ha_login)}</a>" +
                field("ha_url", str(R.string.settings_address), s.smartHomeUrl, " inputmode=\"url\"") +
                field("ha_token", str(R.string.settings_ha_token), extra = keep(s.smartHomeToken.isNotBlank()), type = "password") +
                personal +
                "<button>${str(R.string.settings_phone_save)}</button></form>",
        )
    }

    private fun html(title: String, body: String) = """<!doctype html><html lang="${Locale.getDefault().language}"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>$title</title>
<style>body{margin:0;padding:24px 16px 48px;font:16px/1.5 system-ui,sans-serif;background:#1B1814;color:#F2EDE4}
h1{font-size:22px;margin:0 0 8px}h2{font-size:15px;margin:28px 0 4px;color:#C9B48A;text-transform:uppercase;letter-spacing:.04em}
label{display:block;margin-top:12px;font-size:14px;color:#BFB6A8}input,select{display:block;margin-top:4px;width:100%;box-sizing:border-box;padding:14px;
font-size:17px;border-radius:12px;border:1px solid #6b5a3a;background:#27231E;color:#F2EDE4}
.sw{display:flex;align-items:center;gap:12px;padding:10px 0;font-size:16px;color:#F2EDE4;border-bottom:1px solid #3a3226}
.sw input{width:24px;height:24px;margin:0;flex:none;accent-color:#E8C27A}
button,.btn{display:block;text-align:center;text-decoration:none;margin-top:16px;width:100%;box-sizing:border-box;padding:14px;font-size:17px;
border:0;border-radius:12px;background:#FFFAF2;color:#1C1A17;font-weight:600}.btn{background:#3a3226;color:#F2EDE4}
.note{font-size:14px;color:#BFB6A8;margin:4px 0}.ok{color:#E8C27A}.err{color:#ff8a7a}</style></head><body>$body</body></html>"""

    private fun str(id: Int, vararg args: Any) = escape(context.getString(id, *args))

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private data class Reply(val code: Int, val body: String, val location: String? = null)

    private companion object {
        const val TAG = "LikhtarPhoneSetup"
        const val MAX_BODY = 16_384
        val PORTS = 8781..8785
    }
}

/** Рядок HTTP-заголовка до CRLF; `null` — з'єднання закрилось. */
private fun InputStream.line(): String? {
    val out = ByteArrayOutputStream()
    while (true) {
        val b = read()
        if (b < 0) return if (out.size() == 0) null else out.toString(Charsets.ISO_8859_1.name())
        if (b == '\n'.code) return out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
        if (out.size() > 8192) return null
        out.write(b)
    }
}
