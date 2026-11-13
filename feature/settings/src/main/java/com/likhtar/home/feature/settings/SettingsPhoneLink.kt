package com.likhtar.home.feature.settings

import android.content.Context
import com.likhtar.home.core.data.fromLocalNetwork
import com.likhtar.home.core.data.httpLine
import android.util.Log
import com.likhtar.home.core.catalog.CatalogRepository
import com.likhtar.home.core.catalog.movable
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
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.model.StreamingService
import com.likhtar.home.core.smarthome.SmartHomeRepository
import com.likhtar.home.core.tv.TvGuideRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    private val catalog: CatalogRepository,
    private val channels: com.likhtar.home.core.tv.ChannelsPhoneLink,
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
        Toggle("trailer_up", R.string.settings_watch_trailer_up, { it.titleTrailerOnUp }, settings::setTitleTrailerOnUp),
        Toggle("title_under_logo", R.string.settings_watch_title_under_logo, { it.titleUnderLogo }, settings::setTitleUnderLogo),
        Toggle("smart_home", R.string.settings_smart_home_toggle, { it.smartHomeEnabled }, settings::setSmartHomeEnabled),
        Toggle("ha_voice", R.string.settings_ha_voice, { it.voiceCommandsEnabled }, settings::setVoiceCommandsEnabled),
    )

    private val _status = MutableStateFlow<PhoneSetupStatus>(PhoneSetupStatus.Waiting)
    val status: StateFlow<PhoneSetupStatus> = _status

    @Volatile private var base: String? = null
    @Volatile private var nonce: String = ""
    @Volatile private var nonceAt: Long = 0L

    /** Адреса для QR; `null` — приставка не в локальній мережі чи порти зайняті. */
    @Synchronized
    fun start(): String? {
        nonce = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        nonceAt = System.currentTimeMillis()
        _status.value = PhoneSetupStatus.Waiting
        val root = base ?: run {
            val ip = lanAddress() ?: return null
            val server = PORTS.firstNotNullOfOrNull { runCatching { ServerSocket(it) }.getOrNull() } ?: return null
            thread(name = "settings-link", isDaemon = true) {
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    if (!socket.fromLocalNetwork()) { runCatching { socket.close() }; continue }
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
        val request = input.httpLine()?.split(' ') ?: return
        val method = request.getOrNull(0)
        val path = request.getOrNull(1)?.substringBefore('?') ?: return
        var length = 0
        while (true) {
            val header = input.httpLine() ?: break
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        // Посилання з QR живе пів години: воно їде відкритим HTTP, і хто його підгледів, не має
        // змінювати налаштування приставки за тиждень.
        if (nonce.isNotEmpty() && System.currentTimeMillis() - nonceAt > NONCE_TTL_MS) nonce = ""
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
        // Xtream Codes: сервер, логін і пароль — це той самий плейлист (get.php) і програма (xmltv.php).
        val xtream = listOf("xtream", "xtream_user", "xtream_pass").map { f[it].orEmpty().trim() }
        if (xtream.any(String::isNotEmpty)) {
            val urls = xtreamUrls(xtream[0], xtream[1], xtream[2])
            if (urls == null) problems += str(R.string.settings_tv_xtream) else {
                settings.setTvPlaylistUrl(urls.first)
                if (f["epg"].isNullOrEmpty()) settings.setTvEpgUrl(urls.second)
                guide.refresh()
                saved += str(R.string.settings_tv_xtream)
            }
        }
        // Портал: нова адреса не забирає з собою логін і ключ старого сервера (як токени HA й AI).
        val portalUrl = f["portal"].orEmpty().trim()
        val portalLogin = f["portal_login"].orEmpty().trim()
        val portalKey = f["portal_key"].orEmpty().trim()
        if (portalUrl.isNotEmpty() || portalLogin.isNotEmpty() || portalKey.isNotEmpty()) {
            val old = com.likhtar.home.core.data.TvPortal.decode(current.tvPortal)
            val url = portalUrl.ifEmpty { old?.url.orEmpty() }
            val moved = old != null && portalUrl.isNotEmpty() && !sameServer(portalUrl, old.url)
            when {
                !url.startsWith("http") -> problems += str(R.string.settings_tv_portal)
                moved && (portalLogin.isEmpty() || portalKey.isEmpty()) -> problems += str(R.string.settings_tv_portal_need_login)
                old == null && (portalLogin.isEmpty() || portalKey.isEmpty()) -> problems += str(R.string.settings_tv_portal_need_login)
                else -> {
                    if (moved) settings.setTvPortal("")
                    settings.setTvPortal(url, portalLogin, portalKey)
                    saved += str(R.string.settings_tv_portal)
                }
            }
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
        f["jf"]?.takeIf { it.isNotEmpty() }?.let { address ->
            media.loginJellyfin(address, f["jf_user"].orEmpty(), f["jf_password"].orEmpty())
                .onSuccess { media.add(it); media.rescan(); saved += str(R.string.settings_media_add_jellyfin) }
                .onFailure { problems += str(R.string.settings_media_jellyfin_failed, it.message ?: it.javaClass.simpleName) }
        }
        val haUrl = f["ha_url"].orEmpty()
        val haToken = f["ha_token"].orEmpty()
        if (haUrl.isNotEmpty() || haToken.isNotEmpty()) {
            // Нова адреса без нового токена — не переносимо старий: інакше одне поле «адреса»
            // відсилало б збережений токен на чужий сервер.
            val url = haUrl.ifEmpty { current.smartHomeUrl }
            val token = haToken.ifEmpty { if (url == current.smartHomeUrl || sameServer(url, current.smartHomeUrl)) current.smartHomeToken else "" }
            settings.setSmartHomeCredentials(url, token)
            saved += "Home Assistant"
            if (token.isEmpty()) problems += str(R.string.settings_ha_need_token)
        }
        // AI: інший провайдер — адреса, модель і ключ з нуля; той самий — порожнє поле лишає як було.
        f["ai_provider"]?.let { id ->
            val same = id == current.aiProvider
            val url = f["ai_url"].orEmpty().ifEmpty { if (same) current.aiUrl else "" }
            val model = f["ai_model"].orEmpty().ifEmpty { if (same) current.aiModel else "" }
            // Той самий провайдер, але інша адреса — ключ теж з нуля: старий не має їхати на нову.
            val key = f["ai_key"].orEmpty().ifEmpty { if (same && (url == current.aiUrl || sameServer(url, current.aiUrl))) current.aiKey else "" }
            if (!same || url != current.aiUrl || model != current.aiModel || key != current.aiKey) {
                settings.setAi(id, url, model, key)
                saved += "AI"
                val provider = AiProviders.of(id)
                if (provider != null && provider.needsKey && key.isEmpty()) problems += str(R.string.settings_ai_need_key)
                if (provider != null && url.ifEmpty { provider.url }.isEmpty()) problems += str(R.string.settings_ai_need_url)
                if (provider != null && model.ifEmpty { provider.model }.isEmpty()) problems += str(R.string.settings_ai_need_model)
            }
        }
        f["clip"]?.takeIf { it.isNotEmpty() }?.let { text ->
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            withContext(Dispatchers.Main) { clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Likhtar", text)) }
            saved += str(R.string.settings_phone_clip_saved)
        }
        // Розділи й ряди: «layout» каже, що блок був на сторінці (зняті позначки не приходять).
        if (f["layout"] == "1") {
            f["section_order"]?.split(',')?.filter { it.isNotBlank() }?.let { order ->
                if (order != orderable(current).map { it.route }) { settings.setSectionOrder(order); saved += str(R.string.settings_phone_section_order) }
            }
            DOCK.forEach { section ->
                // У доці — позначено; прихований і позначений (чи навпаки) — перемикаємо.
                if ((f["dock_${section.route}"] == "on") == (section.route in current.hiddenDockSections)) {
                    settings.toggleDockSection(section.route); saved += context.getString(section.titleRes)
                }
            }
            StreamingService.withPage.forEach { svc ->
                val key = svc.key ?: return@forEach
                if ((f["svc_$key"] == "on") == (key in current.hiddenServices)) { settings.toggleHiddenService(key); saved += svc.title }
            }
            if (f["rows"] == "1") {
                val all = f["row_all"].orEmpty().split('\n')
                val order = f["row_order"].orEmpty().split(',').mapNotNull { it.toIntOrNull()?.let(all::getOrNull) }
                // Сховані ряди, яких сервер зараз не дав, лишаються схованими.
                val hidden = current.hiddenCinemaRows - all.toSet() + all.filterIndexed { i, _ -> f["row_$i"] != "on" }
                if (order != current.cinemaRowOrder || hidden != current.hiddenCinemaRows) {
                    settings.setCinemaRows(order, hidden); saved += str(R.string.settings_phone_cinema_rows)
                }
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
        val clip = "<h2>${str(R.string.settings_phone_clip)}</h2><p class=\"note\">${str(R.string.settings_phone_clip_hint)}</p>" +
            "<textarea name=\"clip\" rows=\"3\" autocapitalize=\"off\" spellcheck=\"false\"></textarea>"
        fun moves() = "<button type=\"button\" class=\"mv\" data-d=\"u\" aria-label=\"↑\">↑</button>" +
            "<button type=\"button\" class=\"mv\" data-d=\"d\" aria-label=\"↓\">↓</button>"
        fun check(name: String, on: Boolean) = "<input type=\"checkbox\" name=\"$name\"${if (on) " checked" else ""}>"
        val sections = orderable(s)
        val sectionList = "<input type=\"hidden\" name=\"section_order\" value=\"${sections.joinToString(",") { it.route }}\">" +
            "<ul class=\"ord\" data-out=\"section_order\">" + sections.joinToString("") { sec ->
                val box = if (sec in DOCK) check("dock_${sec.route}", sec.route !in s.hiddenDockSections) else "<span class=\"nb\"></span>"
                "<li data-k=\"${sec.route}\"><label>$box${str(sec.titleRes)}</label>${moves()}</li>"
            } + "</ul>"
        val services = StreamingService.withPage.joinToString("") { svc ->
            val key = svc.key.orEmpty()
            "<label class=\"sw\">${check("svc_$key", key !in s.hiddenServices)}${escape(svc.title)}</label>"
        }
        // Ряди — ті, що зараз дає сервер, у порядку людини; сервер мовчить — блоку немає.
        val rows = withTimeoutOrNull(ROWS_TIMEOUT_MS) { runCatching { catalog.cinemaSection() }.getOrNull() }.orEmpty()
            .filter { !it.tail && it.key != SLIDER_ROW && it.movable }
            .sortedBy { row -> s.cinemaRowOrder.indexOf(row.key).let { if (it < 0) Int.MAX_VALUE else it } }
        val rowList = if (rows.isEmpty()) "" else "<h2>${str(R.string.settings_phone_cinema_rows)}</h2><p class=\"note\">${str(R.string.settings_phone_rows_hint)}</p>" +
            "<input type=\"hidden\" name=\"rows\" value=\"1\">" +
            "<input type=\"hidden\" name=\"row_all\" value=\"${escape(rows.joinToString("\n") { it.key })}\">" +
            "<input type=\"hidden\" name=\"row_order\" value=\"${rows.indices.joinToString(",")}\">" +
            "<ul class=\"ord\" data-out=\"row_order\">" + rows.withIndex().joinToString("") { (i, row) ->
                "<li data-k=\"$i\"><label>${check("row_$i", row.key !in s.hiddenCinemaRows)}${escape(row.title)}</label>${moves()}</li>"
            } + "</ul>"
        val layout = "<input type=\"hidden\" name=\"layout\" value=\"1\">" +
            "<h2>${str(R.string.settings_phone_section_order)}</h2><p class=\"note\">${str(R.string.settings_phone_order_hint)}</p>" + sectionList +
            rowList + "<h2>${str(R.string.settings_phone_services)}</h2>" + services
        return html(
            str(R.string.settings_phone_title),
            "<h1>${str(R.string.settings_phone_title)}</h1><p>${str(R.string.settings_phone_intro)}</p>$notes" +
                "<form action=\"save\" method=\"post\">" +
                clip + layout +
                "<h2>${str(R.string.settings_section_tv)}</h2>" +
                (channels.link()?.let { "<a class=\"btn\" href=\"$it\">${str(R.string.settings_phone_channels)}</a>" } ?: "") +
                field("playlist", str(R.string.settings_tv_playlist), extra = keep(s.tvPlaylistUrl.isNotBlank()) + " inputmode=\"url\"") +
                field("epg", str(R.string.settings_epg_title), extra = keep(s.tvEpgUrl.isNotBlank()) + " inputmode=\"url\"") +
                field("xtream", str(R.string.settings_tv_xtream), extra = " inputmode=\"url\" placeholder=\"http://…:8080\"") +
                field("xtream_user", str(R.string.settings_tv_xtream_user)) +
                field("xtream_pass", str(R.string.settings_tv_xtream_pass), type = "password") +
                // Портал Stalker: адреса, логін і ключ з кабінету провайдера.
                field("portal", str(R.string.settings_tv_portal), extra = keep(s.tvPortal.isNotBlank()) + " inputmode=\"url\" placeholder=\"http://…/stalker_portal/c/\"") +
                field("portal_login", str(R.string.settings_tv_portal_login), extra = keep(s.tvPortal.isNotBlank())) +
                field("portal_key", str(R.string.settings_tv_portal_key), extra = keep(s.tvPortal.isNotBlank()), type = "password") +
                "<h2>${str(R.string.settings_media_group)}</h2>" +
                field("smb", str(R.string.settings_media_smb_address), extra = " placeholder=\"${str(R.string.settings_media_smb_address_hint)}\"") +
                field("smb_user", str(R.string.settings_media_smb_user)) +
                field("smb_password", str(R.string.settings_media_smb_password), type = "password") +
                field("jf", str(R.string.settings_media_jellyfin_address), extra = " placeholder=\"${str(R.string.settings_media_jellyfin_address_hint)}\" inputmode=\"url\"") +
                field("jf_user", str(R.string.settings_media_jellyfin_user)) +
                field("jf_password", str(R.string.settings_media_jellyfin_password), type = "password") +
                "<h2>Home Assistant</h2>" +
                "<a class=\"btn\" href=\"ha\">${str(R.string.settings_phone_ha_login)}</a>" +
                field("ha_url", str(R.string.settings_address), s.smartHomeUrl, " inputmode=\"url\"") +
                field("ha_token", str(R.string.settings_ha_token), extra = keep(s.smartHomeToken.isNotBlank()), type = "password") +
                personal +
                "<button>${str(R.string.settings_phone_save)}</button></form>",
        )
    }

    private fun html(title: String, body: String) = com.likhtar.home.core.data.phonePageHtml(title, body)

    /** Розділи, які людина ставить у свій порядок: без Головної й Налаштувань, лише ввімкнені. */
    private fun orderable(s: LikhtarSettings) = LikhtarSection.ordered(
        LikhtarSection.entries.filter {
            it != LikhtarSection.HOME && it != LikhtarSection.SETTINGS &&
                (it != LikhtarSection.SMART_HOME || s.smartHomeEnabled) &&
                (it != LikhtarSection.GAMES || s.gamesEnabled)
        },
        s.sectionOrder,
    )

    private fun str(id: Int, vararg args: Any) = escape(context.getString(id, *args))

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private data class Reply(val code: Int, val body: String, val location: String? = null)

    private companion object {
        const val TAG = "LikhtarPhoneSetup"
        const val MAX_BODY = 16_384
        const val NONCE_TTL_MS = 30 * 60_000L
        const val ROWS_TIMEOUT_MS = 5_000L
        /** Перший ряд сервера — пул слайдера, не ряд постерів (CinemaViewModel). */
        const val SLIDER_ROW = "Слайдер"
        /** Розділи, які можна прибрати з доку Головної, — ті самі, що в налаштуваннях на ТВ. */
        val DOCK = listOf(LikhtarSection.CINEMA, LikhtarSection.LIVE_TV, LikhtarSection.REALITY, LikhtarSection.SEARCH, LikhtarSection.APPS)
        val PORTS = 8781..8785
    }
}

/** Той самий сервер: схема, хост і порт збігаються. Інакше збережений секрет на нову адресу не їде. */
internal fun sameServer(a: String, b: String): Boolean {
    fun key(u: String) = runCatching { java.net.URI(u.trim()) }.getOrNull()
        ?.takeIf { it.host != null }?.let { "${it.scheme}://${it.host.lowercase()}:${it.port}" }
    return key(a) != null && key(a) == key(b)
}


/**
 * Xtream Codes → адреси плейлиста й програми: `get.php` віддає M3U з групами й логотипами, `xmltv.php` —
 * програму. Сервер можна вставити як завгодно — з `player_api.php`, `get.php` чи косою наприкінці.
 * `null` — бракує сервера, логіна чи пароля.
 */
internal fun xtreamUrls(server: String, user: String, pass: String): Pair<String, String>? {
    if (!server.startsWith("http") || user.isBlank() || pass.isBlank()) return null
    val base = server.substringBefore('?').trimEnd('/').let { b ->
        listOf("/player_api.php", "/get.php", "/xmltv.php").fold(b) { acc, tail -> acc.removeSuffix(tail) }
    }.trimEnd('/')
    val auth = "username=${java.net.URLEncoder.encode(user, "UTF-8")}&password=${java.net.URLEncoder.encode(pass, "UTF-8")}"
    return "$base/get.php?$auth&type=m3u_plus&output=m3u8" to "$base/xmltv.php?$auth"
}
