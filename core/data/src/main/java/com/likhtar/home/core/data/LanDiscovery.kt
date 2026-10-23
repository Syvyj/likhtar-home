package com.likhtar.home.core.data

import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Пошук машин у домашній мережі обходом своєї /24 — для нашого сервера, а в домашній збірці
 * ще й для TorrServer.
 *
 * Чому обхід, а не Bonjour: Android не розв'язує mDNS-імена штатним ресолвером, а оголошення
 * з Docker-контейнера з bridge-мережею за його межі не виходить (заміряно 03.09.2026). Обхід
 * із коротким таймаутом не покладається ні на Bonjour, ні на те, що в VLAN нічого не зламано.
 */
@Singleton
class LanDiscovery @Inject constructor() {

    /**
     * Адреси, за якими у цій мережі відповів наш сервер (`/api/profiles`).
     *
     * Android не розв'язує mDNS-імена штатним ресолвером (заміряно —
     * `Mac-mini-Admin.local` з приставки це «unknown host»), тож покластися на ім'я замість
     * цифр не вийшло.
     */
    /**
     * 254 блокуючі проби разом забирали весь пул IO (64 потоки) на ~2 с — решта лаунчера в цей
     * час не могла ні картинки завантажити, ні запит зробити. 24
     * одночасно: скан довшає до ~7 с, але він і так на вимогу, з Налаштувань.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val probes = Dispatchers.IO.limitedParallelism(24)

    suspend fun scanServer(port: Int = SERVER_PORT): List<String> = withContext(Dispatchers.IO) {
        val prefix = localPrefix() ?: return@withContext emptyList()
        coroutineScope {
            (1..254).map { last ->
                async(probes) {
                    val base = "http://$prefix.$last:$port"
                    if (probe("$base/api/profiles")) base else null
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun probe(url: String): Boolean = runCatching {
        http(url, PROBE_MS) { it.responseCode == HttpURLConnection.HTTP_OK }
    }.getOrDefault(false)

    /** Хости підмережі, для яких [check] дав не `null`, — його ж відповіді. */
    suspend fun scan(check: suspend (host: String) -> String?): List<String> = withContext(Dispatchers.IO) {
        val prefix = localPrefix() ?: return@withContext emptyList()
        coroutineScope {
            (1..254).map { last -> async(probes) { check("$prefix.$last") } }.awaitAll().filterNotNull()
        }
    }

    /** Перші три октети власної адреси — обходити маємо ту саму підмережу, що й приставка. */
    private fun localPrefix(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
            ?.substringBeforeLast('.')
    }.getOrNull()

    private companion object {
        /** Порт агрегатора: усі запити `/api` йдуть через нього. */
        const val SERVER_PORT = 18118

        /** Обхід іде паралельно всією підмережею, тому таймаут короткий. */
        const val PROBE_MS = 700
    }
}
