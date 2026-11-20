package com.likhtar.home.core.data.cloud

import android.content.Context
import android.util.Log
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.data.locale
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Каталог хмарних ігор на екрані: зведені ігри й коли кожен сервіс оновлювався востаннє. */
data class CloudCatalogState(
    val games: List<CloudGame> = emptyList(),
    /** Час знімка кожного сервісу (мс); немає ключа — знімка ще не було. */
    val updated: Map<CloudService, Long> = emptyMap(),
    /** Сервіси, що не відповіли під час останнього оновлення: їхні ігри — з попереднього знімка. */
    val failed: Set<CloudService> = emptySet(),
    val refreshing: Boolean = false,
    val loaded: Boolean = false,
)

/**
 * Каталог хмарних ігор, який приставка збирає сама: без сервера, і в публічній збірці теж.
 *
 * Кожен сервіс — окремий файл-знімок у `files/cloud/`: зламався один API — його ігри лишаються
 * з учорашнього знімка, решта оновлюється (анти-ціль «каталог тихо старіє» — через [failed]
 * екран це покаже). Оновлення — раз на добу; хто розділ «Ігри» не вмикає, нічого не тягне.
 */
@Singleton
class CloudCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val dir get() = File(context.filesDir, "cloud")
    private val mutex = Mutex()
    private val current = MutableStateFlow(CloudCatalogState())
    val state: StateFlow<CloudCatalogState> = current.asStateFlow()

    // Своя область: оновлення не обривається, коли людина пішла з «Ігор» посеред завантаження.
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** [refreshIfStale] у фоні, без очікування: з екрана «Ігор» і зі старту лаунчера. */
    fun refreshInBackground(delayMs: Long = 0L) {
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            runCatching { refreshIfStale() }.onFailure { Log.w(TAG, "оновлення: ${it.message}") }
        }
    }

    /** Прочитати знімки з диска (без мережі). Вдруге — нічого не робить. */
    suspend fun load() = mutex.withLock { if (!current.value.loaded) publish(failed = emptySet()) }

    /** Оновити сервіси, чиї знімки старші за добу (або всі — [force]). Лише з увімкненими «Іграми». */
    suspend fun refreshIfStale(force: Boolean = false) {
        val s = settings.settings.first()
        if (!s.gamesEnabled) return
        val market = xboxMarket(s.locale.country)
        mutex.withLock {
            if (!current.value.loaded) publish(failed = emptySet())
            val now = System.currentTimeMillis()
            val stale = CloudService.entries.filter { force || isStale(file(it).lastModified(), now) }
            if (stale.isEmpty()) return
            current.update { it.copy(refreshing = true) }
            val failed = HashSet<CloudService>()
            withContext(Dispatchers.IO) {
                for (service in stale) {
                    val started = System.currentTimeMillis()
                    val fresh = runCatching {
                        when (service) {
                            CloudService.BOOSTEROID -> fetchBoosteroid()
                            CloudService.GEFORCE_NOW -> fetchGeForceNow()
                            CloudService.XBOX -> fetchXbox(market)
                        }
                    }.onFailure { Log.w(TAG, "$service: ${it.javaClass.simpleName} ${it.message}") }.getOrNull()
                    val previous = read(service).size
                    if (fresh != null && acceptSnapshot(previous, fresh.size)) {
                        dir.mkdirs()
                        val tmp = File(dir, "${service.name}.tmp")
                        tmp.writeText(writeSourceGames(fresh))
                        tmp.renameTo(file(service))
                        Log.i(TAG, "$service: ${fresh.size} ігор за ${System.currentTimeMillis() - started} мс")
                    } else {
                        failed += service
                        Log.w(TAG, "$service: знімок не прийнято (було $previous, прийшло ${fresh?.size}) — лишається попередній")
                    }
                }
            }
            publish(failed)
        }
    }

    private suspend fun publish(failed: Set<CloudService>) {
        val started = System.currentTimeMillis()
        val (games, updated) = withContext(Dispatchers.Default) {
            val sources = CloudService.entries.associateWith { read(it) }
            mergeCloud(sources.getValue(CloudService.BOOSTEROID), sources.getValue(CloudService.GEFORCE_NOW), sources.getValue(CloudService.XBOX)) to
                CloudService.entries.mapNotNull { s -> file(s).takeIf { it.exists() }?.let { s to it.lastModified() } }.toMap()
        }
        Log.i(TAG, "каталог: ${games.size} ігор, зведено за ${System.currentTimeMillis() - started} мс")
        current.value = CloudCatalogState(games, updated, failed, refreshing = false, loaded = true)
    }

    private fun file(service: CloudService) = File(dir, "${service.name}.json")

    private fun read(service: CloudService): List<SourceGame> =
        runCatching { file(service).takeIf { it.exists() }?.readText()?.let(::readSourceGames) }.getOrNull().orEmpty()

    private companion object {
        const val TAG = "LikhtarCloud"
    }
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** Знімка немає (0) або він старший за добу. */
internal fun isStale(savedAt: Long, now: Long): Boolean = savedAt <= 0L || now - savedAt > DAY_MS

/** Порожньо чи менше половини вчорашнього — збій API, а не правда. */
internal fun acceptSnapshot(previous: Int, fresh: Int): Boolean = fresh > 0 && fresh * 2 >= previous

/** Ринки з Xbox Cloud; де хмари немає (Україна) — польський каталог. */
private val XCLOUD_MARKETS = setOf(
    "AR", "AT", "AU", "BE", "BR", "CA", "CH", "CO", "CZ", "DE", "DK", "ES", "FI", "FR", "GB", "HU", "IE", "IT", "JP",
    "KR", "MX", "NL", "NO", "NZ", "PL", "PT", "SE", "SK", "US",
)

internal fun xboxMarket(country: String): String = country.uppercase().takeIf { it in XCLOUD_MARKETS } ?: "PL"
