package com.likhtar.home.core.catalog

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * Що ми знаємо про українську доріжку тайтла.
 *
 * Три стани, а не два: `yes`, `no` і «не питали / сервер не знає». Останній — відсутність
 * запису, і саме тому [UaAudioRepository.of] віддає мапу, а не прапорець на кожен id:
 * «немає відповіді» й «немає озвучки» — різні речі, і мітку в першому випадку не малюють.
 */
data class UaAudio(
    val ua: Boolean,
    /** Сервіси, де ми САМІ бачили доріжку. Порожньо — підстава з іншого джерела. */
    val services: List<String> = emptyList(),
    /** Озвучення: «Дубляж», студії. Порожньо — назв не знаємо. */
    val voices: List<String> = emptyList(),
    /**
     * Сервіси, де тайтл просто Є. Про звук вони не кажуть НІЧОГО.
     *
     * Netflix, Prime, Apple TV сюди й потрапляють: ми не читаємо їхні доріжки, тому мітки
     * за ними не буде ніколи. Плутати ці два списки на екрані не можна — одне стверджує
     * про звук, друге лише про наявність.
     */
    val otherServices: List<String> = emptyList(),
    /**
     * Мови доріжок, які сервер ПРОЧИТАВ — не ті, які припускає.
     *
     * Порожньо означає «не читали», а не «доріжок немає»: сторінки читаються лише для
     * megogo й sweet.tv. Потрібне неігровому, де дозволена й російська доріжка: без
     * цього поля розділ не відрізнив би «є російською» від «не знаємо нічого».
     */
    val langs: List<String> = emptyList(),
    /** Найкраща якість наших живих джерел: 2160 | 1080 | 720; 0 — не знаємо. */
    val quality: Int = 0,
    /** HDR найкращого джерела: «Dolby Vision», «DV+HDR10+», «HDR». */
    val hdr: String = "",
) {
    /** Чи є серед прочитаних доріжок російська. */
    val hasRu: Boolean get() = RU in langs

    private companion object {
        const val RU = "ru"
    }
}

/**
 * Мітка «є українська» — пакетно, одним запитом на екран.
 *
 * Ручка `/api/ua?tmdb_ids=…` дешева (200 id ≈ 37 мс), тому питати по одному немає причин:
 * екран збирає свої id і питає раз. Відповіді лишаються в пам'яті процесу — за сеанс той
 * самий тайтл трапляється в кількох рядах, і другий запит по нього марний.
 *
 * `basis` із відповіді свідомо не читаємо: мітка одна, підстави на екрані не буває. Це
 * свідоме рішення, а не спрощення парсера.
 */
/**
 * Скільки живуть кеші каталогу в пам'яті процесу.
 *
 * Те саме число, що в розкладці «Реаліті»: за сеанс перегляду нічого не перезбирається на
 * кожен рух, але й не застигає до перезапуску застосунку.
 */
internal const val CATALOG_CACHE_MS = 30 * 60 * 1000L

/** Чи є в тайтлів українська доріжка — вузько, для екранів, яким більше нічого не треба. */
fun interface UaLookup {
    suspend fun of(ids: List<Int>): Map<Int, UaAudio>
}

@Singleton
class UaAudioRepository @Inject constructor(
    private val config: CatalogConfig,
) : UaLookup {

    private val known = mutableMapOf<Int, UaAudio?>()

    /** Коли кеш набрано. Нуль — ще порожній. */
    private var knownAt = 0L

    /**
     * Що відомо про ці id.
     *
     * У відповіді лише ті, про кого сервер щось знає: відсутній ключ означає «не знаємо»,
     * і на екрані це те саме, що й порожнє місце.
     */
    override suspend fun of(ids: List<Int>): Map<Int, UaAudio> {
        val missing = synchronized(known) {
            // Кеш на час життя процесу тут був окремою поломкою: приставка не
            // перезавантажується тижнями, а в кеші лежать і НЕГАТИВИ. Тобто озвучка,
            // що з'явилась учора, не з'явилась би в рядах доти, доки людина не
            // перезапустить лаунчер вручну.
            val now = System.currentTimeMillis()
            if (now - knownAt > CATALOG_CACHE_MS) {
                known.clear()
                knownAt = now
            }
            ids.distinct().filterNot { known.containsKey(it) }
        }
        if (missing.isNotEmpty()) fetch(missing)
        val ua = config.ukrainianAudience()
        return synchronized(known) {
            ids.mapNotNull { id -> known[id]?.let { id to if (ua) it else it.copy(ua = false) } }.toMap()
        }
    }

    /**
     * Порціями по [BATCH]: сервер відповідає лише про перші 200 id запиту, а решту клієнт
     * запам'ятовував як «не знаємо» — на сторінці «Фільми» (210 карток) нижні ряди стояли
     * приглушеними, хоча українська в них є.
     */
    private suspend fun fetch(ids: List<Int>) = coroutineScope {
        ids.chunked(BATCH).map { part -> async { fetchBatch(part) } }.awaitAll()
    }

    private suspend fun fetchBatch(ids: List<Int>) {
        val base = config.streamsUrl() ?: return
        val body = withContext(Dispatchers.IO) {
            runCatching { httpGet("$base/api/ua?tmdb_ids=${ids.joinToString(",")}", TIMEOUT_MS) }
                .onFailure { Log.w(TAG, "Мітка UA не приїхала", it) }
                .getOrNull()
        } ?: return
        val parsed = runCatching { parseUaAudio(body) }.getOrDefault(emptyMap())
        synchronized(known) {
            // Запам'ятовуємо й тих, про кого сервер промовчав: інакше кожен наступний екран
            // питав би про них знову, а відповідь та сама — «не знаємо».
            ids.forEach { id -> known[id] = parsed[id] }
        }
    }

    private companion object {
        const val TAG = "LikhtarUa"

        /** Стеля сервера на один запит `/api/ua`. */
        const val BATCH = 200

        /** Ручка віддає 200 id за 37 мс; довше чекати немає сенсу — мітка не критична. */
        const val TIMEOUT_MS = 4_000
    }
}

/**
 * Розбір відповіді `/api/ua`.
 *
 * Окремо від репозиторію — саме тут живе межа між «немає» і «не знаємо»: id без запису в
 * `results` у мапу не потрапляє, і мітки на екрані не буде. Функція чиста, тож ця межа
 * перевіряється тестом, а не запуском.
 */
internal fun parseUaAudio(body: String): Map<Int, UaAudio> {
    val results = JSONObject(body).optJSONObject("results") ?: return emptyMap()
    return results.keys().asSequence().mapNotNull { key ->
        val id = key.toIntOrNull() ?: return@mapNotNull null
        val raw = results.optJSONObject(key) ?: return@mapNotNull null
        // Збірка для магазину знає лише офіційне: українська — коли її підтверджує сервіс,
        // без студій озвучки, якості й HDR наших власних джерел (Distribution).
        val store = com.likhtar.home.core.model.Distribution.store
        val services = raw.strings("services")
        id to UaAudio(
            ua = raw.optString("ua") == "yes" && (!store || services.any(com.likhtar.home.core.model.StreamingService::isOfficial)),
            services = services,
            voices = if (store) emptyList() else raw.strings("voices"),
            otherServices = raw.strings("other_services"),
            langs = raw.strings("langs"),
            quality = if (store) 0 else raw.optInt("quality"),
            hdr = if (store) "" else raw.optString("hdr"),
        )
    }.toMap()
}

internal fun JSONObject.strings(field: String): List<String> {
    val array = optJSONArray(field) ?: return emptyList()
    return array.toStringList()
}
