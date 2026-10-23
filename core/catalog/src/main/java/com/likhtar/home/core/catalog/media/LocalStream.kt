package com.likhtar.home.core.catalog.media

import android.util.Base64
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Файли SMB для плеєра: плеєри `smb://` здебільшого не відкривають, тож лаунчер віддає їх
 * по HTTP з Range (перемотування) — лише на 127.0.0.1, з випадковим ключем у шляху.
 *
 * Той самий підхід «ServerSocket і потік на з'єднання», що в PhoneRemote і HaPhoneLink.
 */
class LocalStream(private val access: (sourceId: String) -> SmbAccess?) {
    private val token = ByteArray(12).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    private var server: ServerSocket? = null

    /** Адреса файлу [path] джерела [sourceId] для плеєра. */
    @Synchronized
    fun urlFor(sourceId: String, path: String, name: String): String {
        val port = (server ?: start()).localPort
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8").replace("+", "%20") }
        // Шлях — base64url: плеєри розкодовують %2F у «/», і сервер бачив лише першу папку.
        // Ім'я в кінці — щоб плеєр бачив розширення (mkv/mp4) і назву.
        return "http://127.0.0.1:$port/$token/${enc(sourceId)}/${b64(path)}/${enc(name)}"
    }

    private fun start(): ServerSocket {
        val s = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        server = s
        thread(name = "likhtar-local-stream", isDaemon = true) {
            while (!s.isClosed) {
                val socket = runCatching { s.accept() }.getOrNull() ?: continue
                thread(name = "likhtar-local-stream-conn", isDaemon = true) { socket.use(::serve) }
            }
        }
        return s
    }

    private fun serve(socket: Socket) {
        val input = socket.getInputStream()
        val out = socket.getOutputStream()
        val request = input.line() ?: return
        var range: String? = null
        while (true) {
            val h = input.line() ?: break
            if (h.isEmpty()) break
            if (h.startsWith("range:", ignoreCase = true)) range = h.substringAfter(':').trim()
        }
        val (method, target) = request.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val parts = target.trimStart('/').split('/')
        if (parts.size < 3 || parts[0] != token || (method != "GET" && method != "HEAD")) return out.status(404)
        val sourceId = URLDecoder.decode(parts[1], "UTF-8")
        val path = runCatching { String(Base64.decode(parts[2], B64), Charsets.UTF_8) }.getOrNull() ?: return out.status(404)
        val smb = access(sourceId) ?: return out.status(404)
        try {
            smb.open(path).use { file ->
                val size = file.fileInformation.standardInformation.endOfFile
                var from = 0L
                var to = size - 1
                val partial = range?.removePrefix("bytes=")?.split('-')?.let { r ->
                    r[0].toLongOrNull()?.let { from = it }
                    r.getOrNull(1)?.toLongOrNull()?.let { to = minOf(it, size - 1) }
                    true
                } ?: false
                if (from >= size) return out.status(416, "Content-Range: bytes */$size\r\n")
                val length = to - from + 1
                val head = buildString {
                    append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                    append("Content-Type: ${mimeOf(path)}\r\nAccept-Ranges: bytes\r\nContent-Length: $length\r\n")
                    if (partial) append("Content-Range: bytes $from-$to/$size\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(head.toByteArray())
                if (method == "HEAD") return
                val buf = ByteArray(256 * 1024)
                var pos = from
                while (pos <= to) {
                    val want = minOf(buf.size.toLong(), to - pos + 1).toInt()
                    val n = file.read(buf, pos, 0, want)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    pos += n
                }
                out.flush()
            }
        } catch (e: Exception) {
            // Плеєр закриває з'єднання на кожній перемотці — це не помилка.
            if (e !is java.net.SocketException) Log.w("LikhtarMedia", "потік $path", e)
        }
    }

    private fun b64(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), B64)

    private companion object {
        const val B64 = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    }

    private fun OutputStream.status(code: Int, extra: String = "") {
        write("HTTP/1.1 $code X\r\n${extra}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
    }

    private fun InputStream.line(): String? {
        val sb = StringBuilder()
        while (true) {
            val c = read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
    }

    private fun mimeOf(path: String) = when (path.substringAfterLast('.').lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "ts", "m2ts", "mts" -> "video/mp2t"
        else -> "application/octet-stream"
    }
}
