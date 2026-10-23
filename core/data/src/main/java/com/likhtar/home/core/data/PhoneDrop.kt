package com.likhtar.home.core.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import android.os.Environment
import android.os.StatFs
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Що відбувається з передачею з телефона — для вікна з QR. */
sealed interface DropState {
    /** QR на екрані, телефон ще не заходив. */
    data object Waiting : DropState
    /** Сторінка відкрита на телефоні. */
    data object Connected : DropState
    data class Receiving(val name: String, val done: Long, val total: Long) : DropState
    /** [uri] — файл у «Завантаженнях» (content://), його й віддаємо плеєру чи встановлювачу. */
    data class Received(val uri: Uri, val name: String, val mime: String, val count: Int) : DropState
    data class Link(val url: String) : DropState
    data class Failed(val message: String) : DropState
}

/**
 * «Передати на приставку»: файл чи посилання з телефона без жодного застосунку.
 *
 * Той самий підхід, що «З телефону» в Ліхтарику: приставка піднімає маленький HTTP-сервер у
 * домашній мережі, QR веде на `http://<ip>:<порт>/<токен>/`. Телефон шле файл сирим тілом
 * POST (XHR із прогресом) — на диск шматками, повз оперативну пам'ять: фільм на 4 ГБ
 * пройде й на приставці з гігабайтом. Перед прийомом — перевірка місця.
 *
 * Файли лягають у загальні «Завантаження» → `Download/Likhtar` (MediaStore, без дозволів на
 * Android 10+) і лишаються там: їх видно будь-якому файловому менеджеру й плеєру
 * Ключ в адресі сталий, сервер піднімається
 * разом із лаунчером — закладка на телефоні працює й після перезапуску.
 *
 * ponytail: прийом по одному файлу; кілька — телефон шле по черзі.
 */
@Singleton
class PhoneDrop @Inject constructor(@ApplicationContext private val context: Context) {
    /** Сталий між запусками: інакше закладка на телефоні вела б у 404 після кожного оновлення. */
    private val token: String by lazy {
        val prefs = context.getSharedPreferences("likhtar_drop", Context.MODE_PRIVATE)
        prefs.getString("token", null) ?: ByteArray(8).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }.also { prefs.edit().putString("token", it).apply() }
    }
    private val busy = AtomicBoolean(false)
    private var received = 0

    private val _state = MutableStateFlow<DropState>(DropState.Waiting)
    val state: StateFlow<DropState> = _state.asStateFlow()

    /** Прийшов файл чи посилання — вікно має з'явитись, навіть якщо його вже закрили. */
    private val _arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val arrivals: SharedFlow<Unit> = _arrivals.asSharedFlow()

    @Volatile private var address: String? = null

    /** До Android 10 MediaStore не пише в «Завантаження» без дозволу — тоді власна тека лаунчера. */
    private val legacyDir: File by lazy {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "downloads")
        File(base, "Likhtar").apply { mkdirs() }
    }

    /** Адреса для QR; `null` — приставка не в локальній мережі чи порт не відкрився. */
    @Synchronized
    fun start(): String? {
        if (_state.value is DropState.Failed) _state.value = DropState.Waiting
        address?.let { return it }
        val ip = lanAddress() ?: return null
        val server = PORTS.firstNotNullOfOrNull { runCatching { ServerSocket(it) }.getOrNull() } ?: return null
        thread(name = "phone-drop", isDaemon = true) {
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { socket.use(::serve) }.onFailure { Log.w(TAG, "запит", it) } }
            }
        }
        return "http://$ip:${server.localPort}/$token/".also { address = it }
    }

    /** Діалог закрили — лічильник з нуля, наступний показ чекає телефон заново. */
    fun reset() {
        received = 0
        if (_state.value !is DropState.Receiving) _state.value = DropState.Waiting
    }

    /** Чи піднімали сервер — без нього файлам нізвідки прийти, і стежити нема за чим. */
    val started: Boolean get() = address != null

    private fun serve(socket: Socket) {
        socket.soTimeout = 60_000
        val input = socket.getInputStream().buffered(256 * 1024)
        val out = socket.getOutputStream()
        val request = input.line() ?: return
        var length = -1L
        while (true) {
            val h = input.line() ?: break
            if (h.isEmpty()) break
            if (h.startsWith("content-length:", ignoreCase = true)) length = h.substringAfter(':').trim().toLongOrNull() ?: -1
        }
        val (method, target) = request.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val prefix = "/$token/"
        if (!target.startsWith(prefix)) return out.reply(404, "text/plain", "Not found")
        val route = target.removePrefix(prefix).substringBefore('?')
        val query = target.substringAfter('?', "").split('&').associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        when {
            method == "GET" && route == "" -> {
                if (_state.value == DropState.Waiting) _state.value = DropState.Connected
                out.reply(200, "text/html", context.assets.open("phone_drop.html").bufferedReader().readText())
            }
            method == "GET" && route == "space" -> out.reply(200, "application/json", """{"free":${free()}}""")
            method == "POST" && route == "link" && length in 1..4096 -> {
                val url = String(input.readExactly(length.toInt()), Charsets.UTF_8).trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    _state.value = DropState.Link(url)
                    _arrivals.tryEmit(Unit)
                    out.reply(200, "application/json", "{}")
                } else {
                    out.reply(400, "application/json", """{"error":"Це не посилання"}""")
                }
            }
            method == "POST" && route == "upload" -> upload(input, out, query["name"].orEmpty(), length)
            else -> out.reply(404, "text/plain", "Not found")
        }
    }

    private fun upload(input: InputStream, out: OutputStream, rawName: String, length: Long) {
        if (length <= 0) return out.reply(411, "application/json", """{"error":"Невідомий розмір"}""")
        if (length + MARGIN > free()) return out.reply(507, "application/json", """{"error":"Не вистачає місця на приставці"}""")
        if (!busy.compareAndSet(false, true)) return out.reply(409, "application/json", """{"error":"Приставка вже приймає файл"}""")
        val name = safeName(rawName)
        val mime = mimeOf(name)
        val target = runCatching { create(name, mime) }.getOrElse {
            busy.set(false)
            Log.w(TAG, "створити $name", it)
            return out.reply(500, "application/json", """{"error":"Не вдалося створити файл"}""")
        }
        try {
            var done = 0L
            var shown = 0L
            (context.contentResolver.openOutputStream(target.uri) ?: error("немає потоку")).buffered(256 * 1024).use { sink ->
                val buf = ByteArray(256 * 1024)
                while (done < length) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), length - done).toInt())
                    if (n < 0) break
                    sink.write(buf, 0, n)
                    done += n
                    // Стан — не на кожен шматок: кожне оновлення перемальовує вікно на ТВ.
                    if (done - shown > length / 100 || done == length) {
                        shown = done
                        _state.value = DropState.Receiving(target.name, done, length)
                    }
                }
            }
            if (done < length) {
                discard(target)
                _state.value = DropState.Failed("«${target.name}» прийшов не весь")
                return out.reply(400, "application/json", """{"error":"Передачу перервано"}""")
            }
            publish(target)
            received++
            _state.value = DropState.Received(target.uri, target.name, mime, received)
            _arrivals.tryEmit(Unit)
            out.reply(200, "application/json", "{}")
        } catch (e: Exception) {
            discard(target)
            _state.value = DropState.Failed("«${target.name}» не прийнято: ${e.message ?: "з'єднання обірвалось"}")
            throw e
        } finally {
            busy.set(false)
        }
    }

    private class Target(val uri: Uri, val name: String, val file: File? = null)

    /** Новий файл у «Завантаження/Likhtar»: однакове ім'я MediaStore сам перейменує («name (1).jpg»). */
    private fun create(name: String, mime: String): Target {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                // Поки пишемо, інші застосунки файлу не бачать — плеєр не схопить половину.
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore відмовив")
            val actual = context.contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: name
            return Target(uri, actual)
        }
        val file = unique(name)
        file.createNewFile()
        return Target(FileProvider.getUriForFile(context, "${context.packageName}.files", file), file.name, file)
    }

    private fun publish(target: Target) {
        if (target.file != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        context.contentResolver.update(target.uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }

    private fun discard(target: Target) {
        runCatching { if (target.file != null) target.file.delete() else context.contentResolver.delete(target.uri, null, null) }
    }

    private fun free(): Long = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(0)

    private fun safeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').replace(Regex("""[\u0000-\u001f:*?"<>|]"""), "_").trim().take(120)
        return base.ifBlank { "file" }
    }

    private fun unique(name: String): File {
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var f = File(legacyDir, name)
        var i = 2
        while (f.exists()) f = File(legacyDir, "$stem (${i++})$ext")
        return f
    }

    private fun OutputStream.reply(code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        write("HTTP/1.1 $code X\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        write(bytes)
        flush()
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

    private fun InputStream.readExactly(n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(out, off, n - off)
            if (r < 0) break
            off += r
        }
        return out.copyOf(off)
    }

    companion object {
        private const val TAG = "PhoneDrop"
        private val PORTS = 8776..8780
        /** Лишаємо системі 200 МБ: заповнена під зав'язку приставка гальмує й не оновлюється. */
        private const val MARGIN = 200L * 1024 * 1024
        /** Тека в «Завантаженнях»: Download/Likhtar. */
        const val FOLDER = "Likhtar"

        fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "ts", "m2ts" -> "video/mp2t"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "apk" -> "application/vnd.android.package-archive"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
