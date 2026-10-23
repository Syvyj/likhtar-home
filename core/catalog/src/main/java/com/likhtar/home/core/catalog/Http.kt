package com.likhtar.home.core.catalog

import com.likhtar.home.core.data.http
import com.likhtar.home.core.data.text
import java.net.HttpURLConnection

/**
 * GET для клієнтів каталогу поверх спільного [http].
 *
 * [okOnly] розрізняє дві наявні семантики: більшість клієнтів на не-200 тихо віддають
 * `null` («сервера немає — розділу немає»), а TMDB код не перевіряє взагалі й покладається
 * на виняток `HttpURLConnection` — його ловить сам викликач.
 */
internal fun httpGet(
    url: String,
    timeoutMs: Int,
    readTimeoutMs: Int = timeoutMs,
    okOnly: Boolean = true,
): String? = http(url, timeoutMs, readTimeoutMs, headers = mapOf("Accept" to "application/json")) { c ->
    if (okOnly && c.responseCode != HttpURLConnection.HTTP_OK) null else c.text()
}

/** Відповідь [httpPostJson]: код і тіло; тіло лише для 2xx. */
internal class HttpReply(val code: Int, val body: String?) {
    val ok: Boolean get() = code in 200..299
}

/** Спільний JSON-POST. З'єднання закривається завжди — і тоді, коли сервер відмовив. */
internal fun httpPostJson(url: String, json: String, timeoutMs: Int, readTimeoutMs: Int = timeoutMs): HttpReply =
    http(url, timeoutMs, readTimeoutMs, "POST", mapOf("Content-Type" to "application/json"), json) { c ->
        val code = c.responseCode
        HttpReply(code, if (code in 200..299) c.text() else null)
    }

/** DELETE без тіла; повертає код відповіді. */
internal fun httpDelete(url: String, timeoutMs: Int): Int = http(url, timeoutMs, method = "DELETE") { it.responseCode }
