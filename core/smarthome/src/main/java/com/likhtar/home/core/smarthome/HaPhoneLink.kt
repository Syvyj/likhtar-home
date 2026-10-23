package com.likhtar.home.core.smarthome

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.likhtar.home.core.data.lanAddress
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/** Як іде підключення з телефону — для діалогу з QR. */
sealed interface HaLinkStatus {
    data object Waiting : HaLinkStatus
    /** Телефон відкрив сторінку — людина вже там. */
    data object Opened : HaLinkStatus
    data object Done : HaLinkStatus
    data class Failed(val message: String) : HaLinkStatus
}

/**
 * «Підключити з телефону»: приставка піднімає крихітний сервер у домашній мережі, QR веде
 * на його сторінку, а звідти — на вхід у Home Assistant.
 *
 * `client_id` — корінь цього сервера, `redirect_uri` — `/cb` на ньому ж: HA пускає такий
 * redirect без додаткових перевірок саме тому, що хост і порт збігаються з `client_id`.
 * `state` — випадковий на кожен показ QR і одноразовий: сусід по Wi‑Fi не підсуне свій код.
 *
 * ponytail: сервер живе, поки живе лаунчер, — після першого показу QR; порт один, і
 * прибирати його заради кількох байтів пам'яті немає сенсу.
 */
@Singleton
class HaPhoneLink @Inject constructor(
    private val auth: HaAuth,
    @ApplicationContext private val context: Context,
) {
    private val _status = MutableStateFlow<HaLinkStatus>(HaLinkStatus.Waiting)
    val status: StateFlow<HaLinkStatus> = _status

    @Volatile private var base: String? = null
    @Volatile private var nonce: String = ""
    @Volatile private var prefill: String = ""
    @Volatile private var pendingUrl: String? = null

    /** Адреса для QR; `null` — приставка не в локальній мережі чи порт зайнятий. */
    @Synchronized
    fun start(currentUrl: String): String? {
        nonce = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefill = currentUrl.ifBlank { DEFAULT_HA }
        if (currentUrl.isBlank()) discover()
        pendingUrl = null
        _status.value = HaLinkStatus.Waiting
        val root = base ?: run {
            val ip = lanAddress() ?: return null
            val server = PORTS.firstNotNullOfOrNull { runCatching { ServerSocket(it) }.getOrNull() } ?: return null
            thread(name = "ha-link", isDaemon = true) {
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { runCatching { serve(socket) }.onFailure { Log.w(TAG, "запит телефону", it) } }
                }
            }
            "http://$ip:${server.localPort}/".also { base = it }
        }
        return root + nonce + "/"
    }

    /**
     * Home Assistant сам оголошує себе в мережі (mDNS `_home-assistant._tcp`): знайдена
     * адреса стає у форму замість навмання `homeassistant.local`, який на частині роутерів
     * не резолвиться. HA у Docker без мережі хоста себе не оголошує — тоді лишається як є.
     */
    private fun discover() {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                @Suppress("DEPRECATION") // resolveService замінили лише в API 34
                runCatching { nsd.resolveService(info, Resolved()) }
            }
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        }
        if (runCatching { nsd.discoverServices(HA_SERVICE, NsdManager.PROTOCOL_DNS_SD, listener) }.isFailure) return
        // Хвилини досить, поки людина наводить телефон на QR.
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { nsd.stopServiceDiscovery(listener) } }, 60_000)
    }

    private inner class Resolved : NsdManager.ResolveListener {
        override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit
        override fun onServiceResolved(info: NsdServiceInfo) {
            val url = info.attributes["internal_url"]?.toString(Charsets.UTF_8)?.takeIf { it.startsWith("http") }
                ?: info.host?.hostAddress?.takeIf { ':' !in it }?.let { "http://$it:${info.port}" }
                ?: return
            if (prefill == DEFAULT_HA) prefill = url.trimEnd('/')
        }
    }

    private fun serve(socket: Socket): Unit = socket.use { s ->
        s.soTimeout = 15_000
        val input = s.getInputStream().buffered()
        val target = input.line()?.split(' ')?.getOrNull(1) ?: return
        while (input.line()?.isNotEmpty() == true) Unit
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').mapNotNull { pair ->
            pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> k to URLDecoder.decode(v, "UTF-8") }
        }.toMap()
        val root = base ?: return
        val reply = when {
            // Сторінка `client_id`: HA показує людині, кому вона дає доступ.
            path == "/" -> Reply(
                200,
                html(
                    context.getString(R.string.smarthome_brand),
                    "<div class=\"h-app\"><b class=\"p-name\">${str(R.string.smarthome_brand)}</b> — " +
                        "${str(R.string.smarthome_link_tagline)}.</div>",
                ),
            )
            path == "/$nonce/" -> {
                _status.value = HaLinkStatus.Opened
                Reply(200, formPage())
            }
            path == "/$nonce/go" -> {
                val url = query["ha"].orEmpty().trim().trimEnd('/').removeSuffix("/api")
                if (!url.startsWith("http://") && !url.startsWith("https://") || runCatching { URI(url).host }.getOrNull().isNullOrBlank()) {
                    Reply(200, formPage(context.getString(R.string.smarthome_link_bad_url)))
                } else {
                    pendingUrl = url
                    val authorize = "$url/auth/authorize?response_type=code" +
                        "&client_id=${enc(root)}&redirect_uri=${enc(root + "cb")}&state=${enc(nonce)}"
                    Reply(302, "", location = authorize)
                }
            }
            path == "/cb" -> callback(query, root)
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

    private fun callback(query: Map<String, String>, root: String): Reply {
        val url = pendingUrl
        val code = query["code"]
        if (query["state"] != nonce || url == null || code.isNullOrBlank()) {
            return Reply(
                200,
                html(
                    context.getString(R.string.smarthome_link_failed_title),
                    "<p>${str(R.string.smarthome_link_expired)} ${str(R.string.smarthome_link_retry)}</p>",
                ),
            )
        }
        // Одноразово: повторний перехід тим самим посиланням уже нічого не дасть.
        nonce = ""
        pendingUrl = null
        val result = runBlocking { auth.exchange(url, root, code) }
        return result.fold(
            onSuccess = {
                _status.value = HaLinkStatus.Done
                Reply(
                    200,
                    html(
                        context.getString(R.string.smarthome_link_done_title),
                        "<h1>${str(R.string.smarthome_link_done_title)} ✓</h1><p>${str(R.string.smarthome_link_done)}</p>",
                    ),
                )
            },
            onFailure = {
                val message = it.message ?: context.getString(R.string.smarthome_link_code_rejected)
                _status.value = HaLinkStatus.Failed(message)
                Reply(
                    200,
                    html(
                        context.getString(R.string.smarthome_link_failed_title),
                        "<p>${escape(message)}</p><p>${str(R.string.smarthome_link_retry)}</p>",
                    ),
                )
            },
        )
    }

    private fun formPage(error: String? = null) = html(
        context.getString(R.string.smarthome_link_title),
        "<h1>${str(R.string.smarthome_brand)} → Home Assistant</h1>" +
            "<p>${str(R.string.smarthome_link_intro)}</p>" +
            (error?.let { "<p class=\"err\">${escape(it)}</p>" } ?: "") +
            "<form action=\"go\" method=\"get\"><input name=\"ha\" value=\"${escape(prefill)}\" " +
            "inputmode=\"url\" autocapitalize=\"off\" autocorrect=\"off\"><button>${str(R.string.smarthome_link_sign_in)}</button></form>",
    )

    private fun html(title: String, body: String) = """<!doctype html><html lang="${Locale.getDefault().language}"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${escape(title)}</title>
<style>body{margin:0;padding:24px 16px;font:16px/1.5 system-ui,sans-serif;background:#1B1814;color:#F2EDE4}
h1{font-size:22px;margin:0 0 12px}input{width:100%;box-sizing:border-box;padding:14px;font-size:17px;border-radius:12px;
border:1px solid #6b5a3a;background:#27231E;color:#F2EDE4}button{margin-top:12px;width:100%;padding:14px;font-size:17px;
border:0;border-radius:12px;background:#FFFAF2;color:#1C1A17;font-weight:600}.err{color:#ff8a7a}</style></head>
<body>$body</body></html>"""

    /** Текст для сторінки на телефоні — мовою лаунчера, екранований для HTML. */
    private fun str(id: Int) = escape(context.getString(id))

    private data class Reply(val code: Int, val body: String, val location: String? = null)

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private companion object {
        const val TAG = "LikhtarHaLink"
        const val DEFAULT_HA = "http://homeassistant.local:8123"
        const val HA_SERVICE = "_home-assistant._tcp"
        val PORTS = 8771..8775
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
