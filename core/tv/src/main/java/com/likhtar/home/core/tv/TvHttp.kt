package com.likhtar.home.core.tv

import com.likhtar.home.core.data.text
import com.likhtar.home.core.data.http
import android.util.Log
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GET для клієнтів порталів — спільний, бо його копія вже жила в кожному.
 *
 * Таймаут довгий навмисно: сервер сам ходить через проксі й ретраїть, тобто відповідає
 * не миттєво. Коротке очікування тут дало б «сервер не відповів» на цілком робочому
 * запиті — те саме, що ми вже ловили на Телепорталі.
 */
@Singleton
class TvHttp @Inject constructor() {

    suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http(url, TIMEOUT_MS, headers = mapOf("Accept" to "application/json")) { c ->
                if (c.responseCode != HttpURLConnection.HTTP_OK) null else c.text()
            }
        }.onFailure { Log.w(TAG, "Джерело недоступне: $url", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "LikhtarTvHttp"
        const val TIMEOUT_MS = 20_000
    }
}
