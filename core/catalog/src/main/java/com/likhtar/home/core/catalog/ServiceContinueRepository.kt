package com.likhtar.home.core.catalog

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * «Продовжити перегляд» із самого MEGOGO.
 *
 * Це єдиний ряд, якого ми не могли знати без акаунта: що людина дивиться в чужому
 * застосунку, видно лише зсередини її акаунта. Вхід робиться на компі (`tools/megogo_login.py`),
 * сесія лежить там же, а сюди приїжджає вже готовий список — через сервер, бо тільки в
 * нього є український вихід.
 */
@Singleton
class ServiceContinueRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: CatalogConfig,
) {

    @Volatile
    private var cached: List<ServiceResume> = emptyList()

    @Volatile
    private var cachedAt = 0L

    suspend fun megogo(): List<ServiceResume> {
        if (!megogoHere()) return emptyList()
        val now = System.currentTimeMillis()
        if (cached.isNotEmpty() && now - cachedAt < CACHE_MS) return cached
        val base = config.homeOnlyUrl() ?: return emptyList()
        val body = withContext(Dispatchers.IO) {
            runCatching { httpGet("$base/api/megogo-continue", TIMEOUT_MS) }
                .onFailure { Log.w(TAG, "MEGOGO: «Продовжити» не приїхало", it) }
                .getOrNull()
        } ?: return cached
        val items = runCatching { parseServiceContinue(body) }
            .onFailure { Log.w(TAG, "MEGOGO: «Продовжити» не розібралось", it) }
            .getOrDefault(emptyList())
        if (items.isNotEmpty()) {
            cached = items
            cachedAt = now
        }
        return items
    }

    /**
     * Рядок для Налаштувань: що сервер каже про сесію.
     *
     * Саме текстом помилки, а не порожнім списком: «сесія протухла» й «нічого не
     * дивився» — різні речі, і рядок, який мовчить про перше, гірший за відсутній.
     * Підключено — окремою ознакою (сервер віддав список), а не за текстом: текст мовою
     * інтерфейсу.
     */
    /** MEGOGO — український сервіс: у збірці для магазину лише для країни «Україна». */
    suspend fun megogoHere(): Boolean =
        !com.likhtar.home.core.model.Distribution.store || config.locale().country == "UA"

    suspend fun megogoState(): Pair<Boolean, String> {
        val base = config.homeOnlyUrl() ?: return false to context.getString(R.string.catalog_status_no_server)
        val body = withContext(Dispatchers.IO) {
            runCatching { httpGet("$base/api/megogo-continue", TIMEOUT_MS) }.getOrNull()
        } ?: return false to context.getString(R.string.catalog_server_no_reply)
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return false to context.getString(R.string.catalog_status_bad_reply)
        json.optString("error").takeIf { it.isNotBlank() }?.let { return false to it }
        val count = json.optInt("count")
        return true to if (count == 0) {
            context.getString(R.string.catalog_status_connected_empty)
        } else {
            context.getString(R.string.catalog_status_connected_count, count)
        }
    }

    private companion object {
        const val TAG = "LikhtarMegogo"
        const val TIMEOUT_MS = 12_000

        /** Десять хвилин: людина дивиться саме зараз, і ряд має за нею встигати. */
        const val CACHE_MS = 10 * 60 * 1000L
    }
}

/** Позиція з «Продовжити» сервісу: назва, кадр і чим її ввімкнути. */
data class ServiceResume(
    val title: String,
    val imageUrl: String?,
    val deeplink: String,
    val packageName: String,
    /** Логотип назви, коли сервер зіставив позицію з TMDB. */
    val logoPath: String? = null,
    /** Коли дивились, секунди; 0 — сервер ще не знає (перший прогін). */
    val updatedAt: Long = 0,
    /** Шоу, матч чи передача — місце в ряду реаліті, не в Кінозалі. */
    val show: Boolean = false,
    /** Підпис картки: «MEGOGO», «S01E03 · Netflix». */
    val label: String = "MEGOGO",
    /** Скільки переглянуто, якщо застосунок сказав. */
    val progress: Float? = null,
)

internal fun parseServiceContinue(body: String): List<ServiceResume> {
    val json = JSONObject(body)
    // Сервер каже про помилку текстом: «сесія протухла» — це не порожній ряд, і мовчки
    // перетворювати одне на друге не можна.
    if (json.optString("error").isNotBlank()) return emptyList()
    val array = json.optJSONArray("results") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val raw = array.optJSONObject(index) ?: return@mapNotNull null
        val deeplink = raw.optString("deeplink")
        val title = raw.optString("title")
        if (deeplink.isBlank() || title.isBlank()) return@mapNotNull null
        ServiceResume(
            title = title,
            imageUrl = raw.optString("poster").takeIf { it.isNotBlank() },
            deeplink = deeplink,
            packageName = com.likhtar.home.core.model.StreamingService.MEGOGO.packageName,
            logoPath = raw.optString("logo").takeIf { it.isNotBlank() },
            updatedAt = raw.optLong("updated"),
            show = raw.optBoolean("show"),
        )
    }
}
