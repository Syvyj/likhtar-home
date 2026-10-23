package com.likhtar.home.core.data

import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Один HTTP-запит на весь лаунчер: до нього ~20 місць тримали власну
 * копію `openConnection` — хтось забував `disconnect`, хтось логував адресу з ключем.
 *
 * Відкриває з'єднання, ставить таймаути й заголовки, пише [body], віддає з'єднання [read]
 * і закриває його завжди. Що робити з відповіддю (текст, файл, код, потік рядків) —
 * вирішує [read]. `inline` — щоб [read] міг емітити у Flow.
 *
 * Помилка з адресою в тексті (`HttpURLConnection` на 4xx/5xx кладе туди ПОВНУ адресу, тобто
 * й `api_key=` TMDB) — без query: викликачі логують винятки цілком.
 *
 * Кожен збій лягає в logcat одним рядком (`LikhtarHttp`) ще ТУТ: ~70 викликачів ковтають
 * виняток через `runCatching { … }.getOrNull()`, і без цього збій мережі виглядав як
 * «нічого не відбувається». Адреса в логу — без query.
 */
inline fun <T> http(
    url: String,
    timeoutMs: Int,
    readTimeoutMs: Int = timeoutMs,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
    read: (HttpURLConnection) -> T,
): T {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = timeoutMs
        readTimeout = readTimeoutMs
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
        if (body != null) doOutput = true
    }
    try {
        if (body != null) connection.outputStream.use { it.write(body.toByteArray()) }
        return read(connection)
    } catch (e: IOException) {
        val clean = withoutQuery(e, url)
        Log.w("LikhtarHttp", "$method ${url.substringBefore('?')}: ${clean.javaClass.simpleName} ${clean.message}")
        throw clean
    } finally {
        connection.disconnect()
    }
}

/** Тіло відповіді текстом. На 4xx/5xx `HttpURLConnection` тут кидає. */
fun HttpURLConnection.text(): String = inputStream.bufferedReader().use { it.readText() }

@PublishedApi
internal fun withoutQuery(e: IOException, url: String): IOException {
    val message = e.message ?: return e
    if (!url.contains('?') || !message.contains(url)) return e
    val clean = message.replace(url, url.substringBefore('?'))
    return if (e is FileNotFoundException) FileNotFoundException(clean) else IOException(clean)
}
