package com.likhtar.home.feature.search

import android.content.Context
import android.util.Log
import com.likhtar.home.core.data.lanAddress
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONArray
import org.json.JSONObject

/** Картка, як її бачить телефон; [target] — що з нею робити на ТВ (ResumeCard чи CatalogItem). */
data class PhoneEntry(
    val id: String,
    val title: String,
    val note: String,
    val image: String?,
    val bookmarked: Boolean?,
    val target: Any,
)

data class PhoneState(val query: String = "", val thought: String = "", val sections: List<Pair<String, List<PhoneEntry>>> = emptyList())

sealed interface PhoneCommand {
    /** Телефон відкрив сторінку — QR більше не потрібен. */
    data object Hello : PhoneCommand
    /** Текст у полі на ТВ без пошуку. */
    data class Type(val text: String) : PhoneCommand
    data class Query(val text: String) : PhoneCommand
    data class Open(val entry: PhoneEntry) : PhoneCommand
    data class Bookmark(val entry: PhoneEntry) : PhoneCommand
}

/**
 * «З телефону»: сторінка Ліхтарика в браузері телефону, без жодного застосунку.
 *
 * Приставка сама піднімає крихітний HTTP-сервер у домашній мережі; QR веде на
 * `http://<ip приставки>:<порт>/<токен>/`. Токен випадковий на кожен запуск лаунчера —
 * без нього сервер відповідає 404, тож сусід по Wi‑Fi навмання не зайде.
 *
 * ponytail: потік на з'єднання й повний перечит стану кожні 1,5 с — телефон один-два,
 * більше не треба.
 */
@Singleton
class PhoneRemote @Inject constructor(@ApplicationContext private val context: Context) {
    private val token = ByteArray(6).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    @Volatile var state: PhoneState = PhoneState()

    private val events = MutableSharedFlow<PhoneCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<PhoneCommand> = events

    @Volatile private var address: String? = null

    /** Адреса для QR; `null` — приставка не в локальній мережі чи порт не відкрився. */
    @Synchronized
    fun start(): String? {
        address?.let { return it }
        val ip = lanAddress() ?: return null
        val server = PORTS.firstNotNullOfOrNull { port -> runCatching { ServerSocket(port) }.getOrNull() } ?: return null
        thread(name = "phone-remote", isDaemon = true) {
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { serve(socket) }.onFailure { Log.w(TAG, "запит телефону", it) } }
            }
        }
        return "http://$ip:${server.localPort}/$token/".also {
            address = it
            Log.i(TAG, "слухаю $it")
        }
    }

    private fun serve(socket: Socket): Unit = socket.use { s ->
        s.soTimeout = 10_000
        val input = s.getInputStream().buffered()
        val request = input.line() ?: return
        val (method, path) = request.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        var length = 0
        while (true) {
            val header = input.line() ?: break
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val body = if (length in 1..MAX_BODY) String(input.readNBytesCompat(length), Charsets.UTF_8) else ""
        val prefix = "/$token/"
        val (code, type, payload) = when {
            !path.startsWith(prefix) -> Triple(404, "text/plain", "Not found")
            method == "GET" && path == prefix -> {
                events.tryEmit(PhoneCommand.Hello)
                Triple(200, "text/html", page())
            }
            method == "GET" && path.startsWith(prefix + "state") -> Triple(200, "application/json", stateJson())
            method == "POST" && path.startsWith(prefix + "cmd") -> {
                command(body)
                Triple(200, "application/json", stateJson())
            }
            else -> Triple(404, "text/plain", "Not found")
        }
        val bytes = payload.toByteArray(Charsets.UTF_8)
        s.getOutputStream().apply {
            write(
                ("HTTP/1.1 $code ${if (code == 200) "OK" else "Not Found"}\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                    "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(),
            )
            write(bytes)
            flush()
        }
    }

    private fun command(body: String) {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return
        val entry = { id: String -> state.sections.asSequence().flatMap { it.second }.firstOrNull { it.id == id } }
        val cmd = when (json.optString("type")) {
            "type" -> PhoneCommand.Type(json.optString("text").take(MAX_QUERY))
            "query" -> PhoneCommand.Query(json.optString("text").take(MAX_QUERY))
            "open" -> entry(json.optString("id"))?.let(PhoneCommand::Open)
            "bookmark" -> entry(json.optString("id"))?.let(PhoneCommand::Bookmark)
            else -> null
        } ?: return
        events.tryEmit(cmd)
    }

    private fun stateJson(): String {
        val current = state
        return JSONObject()
            .put("query", current.query)
            .put("thought", current.thought)
            .put(
                "sections",
                JSONArray(
                    current.sections.filter { it.second.isNotEmpty() }.map { (title, items) ->
                        JSONObject().put("title", title).put(
                            "items",
                            JSONArray(
                                items.map { e ->
                                    JSONObject().put("id", e.id).put("title", e.title).put("note", e.note)
                                        .put("image", e.image.orEmpty())
                                        .put("bookmarked", e.bookmarked ?: JSONObject.NULL)
                                },
                            ),
                        )
                    },
                ),
            ).toString()
    }

    private fun page(): String = context.assets.open("phone_remote.html").bufferedReader().readText()

    private companion object {
        const val TAG = "PhoneRemote"
        val PORTS = 8765..8770
        const val MAX_BODY = 16_384
        const val MAX_QUERY = 200
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

private fun InputStream.readNBytesCompat(n: Int): ByteArray {
    val buf = ByteArray(n)
    var read = 0
    while (read < n) {
        val r = read(buf, read, n - read)
        if (r < 0) break
        read += r
    }
    return buf.copyOf(read)
}
