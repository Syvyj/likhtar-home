package com.likhtar.home.core.data

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Сторінка для телефона за QR: крихітний HTTP у домашній мережі, шлях — випадковий на кожен
 * показ QR. [handle] отримує метод, шлях після `/<nonce>/` і поля форми; повертає HTML або
 * `null` (404).
 *
 * ponytail: SettingsPhoneLink і HaPhoneLink мають свої копії сервера; переводити їх сюди —
 * коли полізуть міняти щось у них самих.
 */
class PhoneServer(
    private val name: String,
    private val ports: IntRange,
    private val maxBody: Int = 512 * 1024,
    private val handle: (method: String, route: String, fields: Map<String, String>) -> String?,
) {
    @Volatile private var base: String? = null
    @Volatile private var nonce = ""

    /** Адреса для QR; `null` — приставка не в локальній мережі чи порти зайняті. */
    @Synchronized
    fun start(): String? {
        nonce = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val root = base ?: run {
            val ip = lanAddress() ?: return null
            val server = ports.firstNotNullOfOrNull { runCatching { ServerSocket(it) }.getOrNull() } ?: return null
            thread(name = name, isDaemon = true) {
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { runCatching { serve(socket) }.onFailure { Log.w(name, "запит телефону", it) } }
                }
            }
            "http://$ip:${server.localPort}/".also { base = it }
        }
        return "$root$nonce/"
    }

    private fun serve(socket: Socket): Unit = socket.use { s ->
        s.soTimeout = 15_000
        val input = s.getInputStream().buffered()
        val request = input.line()?.split(' ') ?: return
        val method = request.getOrNull(0).orEmpty()
        val path = request.getOrNull(1)?.substringBefore('?') ?: return
        var length = 0
        while (true) {
            val header = input.line() ?: break
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val prefix = "/$nonce/"
        val fields = if (method == "POST" && length in 1..maxBody) {
            val body = ByteArray(length)
            var read = 0
            while (read < length) { val n = input.read(body, read, length - read); if (n < 0) break; read += n }
            String(body, 0, read, Charsets.UTF_8).split('&').mapNotNull { pair ->
                pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8").trim() }
            }.toMap()
        } else {
            emptyMap()
        }
        val page = if (nonce.isNotEmpty() && path.startsWith(prefix)) handle(method, path.removePrefix(prefix), fields) else null
        val bytes = (page ?: "Not found").toByteArray(Charsets.UTF_8)
        s.getOutputStream().apply {
            write(
                ("HTTP/1.1 ${if (page != null) "200 OK" else "404 Not Found"}\r\nContent-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(),
            )
            write(bytes)
            flush()
        }
    }
}

fun escapeHtml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/**
 * HTML сторінки для телефона в кольорах Ліхтаря. Списки `.ord` з кнопками `.mv` пересуваються
 * ↑↓, а при відправці форми їхній порядок (`data-k` рядків) лягає в поле `data-out`.
 */
fun phonePageHtml(title: String, body: String, extraCss: String = "") = """<!doctype html><html lang="${Locale.getDefault().language}"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>$title</title>
<style>body{margin:0;padding:24px 16px 48px;font:16px/1.5 system-ui,sans-serif;background:#1B1814;color:#F2EDE4}
h1{font-size:22px;margin:0 0 8px}h2{font-size:15px;margin:28px 0 4px;color:#C9B48A;text-transform:uppercase;letter-spacing:.04em}
label{display:block;margin-top:12px;font-size:14px;color:#BFB6A8}input,select{display:block;margin-top:4px;width:100%;box-sizing:border-box;padding:14px;
font-size:17px;border-radius:12px;border:1px solid #6b5a3a;background:#27231E;color:#F2EDE4}
.sw{display:flex;align-items:center;gap:12px;padding:10px 0;font-size:16px;color:#F2EDE4;border-bottom:1px solid #3a3226}
.sw input{width:24px;height:24px;margin:0;flex:none;accent-color:#E8C27A}
button,.btn{display:block;text-align:center;text-decoration:none;margin-top:16px;width:100%;box-sizing:border-box;padding:14px;font-size:17px;
border:0;border-radius:12px;background:#FFFAF2;color:#1C1A17;font-weight:600}.btn{background:#3a3226;color:#F2EDE4}
.note{font-size:14px;color:#BFB6A8;margin:4px 0}.ok{color:#E8C27A}.err{color:#ff8a7a}
textarea{display:block;margin-top:8px;width:100%;box-sizing:border-box;padding:14px;font:17px system-ui,sans-serif;border-radius:12px;border:1px solid #6b5a3a;background:#27231E;color:#F2EDE4}
.ord{list-style:none;margin:8px 0 0;padding:0}.ord li{display:flex;align-items:center;gap:8px;padding:6px 0;border-bottom:1px solid #3a3226}
.ord label{flex:1;display:flex;align-items:center;gap:12px;margin:0;font-size:16px;color:#F2EDE4}
.ord input,.nb{width:24px;height:24px;margin:0;flex:none;accent-color:#E8C27A}
.ord .mv{width:44px;height:44px;margin:0;padding:0;flex:none;background:#3a3226;color:#F2EDE4;font-size:20px}$extraCss</style>
<script>addEventListener('DOMContentLoaded',()=>{
document.querySelectorAll('.ord').forEach(ul=>ul.addEventListener('click',e=>{const b=e.target.closest('.mv');if(!b)return;const li=b.closest('li');
if(b.dataset.d=='u'&&li.previousElementSibling)ul.insertBefore(li,li.previousElementSibling);
if(b.dataset.d=='d'&&li.nextElementSibling)ul.insertBefore(li.nextElementSibling,li)}));
const f=document.querySelector('form');if(f)f.addEventListener('submit',()=>document.querySelectorAll('.ord').forEach(ul=>{
f.elements[ul.dataset.out].value=[...ul.children].map(li=>li.dataset.k).join(',')}))})</script></head><body>$body</body></html>"""

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
