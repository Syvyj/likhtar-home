package com.likhtar.home.core.catalog

import com.likhtar.home.core.data.http
import com.likhtar.home.core.data.text
import org.json.JSONObject

/**
 * Де починаються титри — з theintrodb.org (без ключа, за TMDB id; IDEAS.md, «Пропуск інтро»).
 *
 * Лише для порогу «додивився»: серіали з довгими титрами й анонсом наступної серії висіли в
 * «Продовжити» з порогом 92 % до кінця часів. Відповідь кешуємо на процес — одна серія питається
 * раз. Не відповіло — `null`, і поріг лишається 92 %.
 */
object IntroDb {
    private const val API = "https://api.theintrodb.org/v2/media"
    private const val TIMEOUT_MS = 4_000
    private val cache = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 300
    }

    /** Початок титрів у мс; `null` — невідомо. Блокує: кликати з IO. */
    fun creditsStartMs(tmdbId: Int, kind: TitleKind, season: Int, episode: Int): Long? {
        if (tmdbId <= 0) return null
        val tv = kind == TitleKind.TV && season > 0 && episode > 0
        val key = if (tv) "tv:$tmdbId:$season:$episode" else "m:$tmdbId"
        synchronized(cache) { cache[key] }?.let { return it.takeIf { v -> v > 0 } }
        val url = if (tv) "$API?tmdb_id=$tmdbId&season=$season&episode=$episode" else "$API?tmdb_id=$tmdbId"
        val start = runCatching {
            http(url, TIMEOUT_MS) { c -> if (c.responseCode != 200) null else parseCreditsStart(c.text()) }
        }.getOrNull()
        synchronized(cache) { cache[key] = start ?: 0L }
        return start
    }

    /** Перший `credits[].start_ms`; немає — `null`. */
    internal fun parseCreditsStart(json: String): Long? =
        JSONObject(json).optJSONArray("credits")?.optJSONObject(0)?.let { c ->
            if (c.isNull("start_ms")) null else c.optLong("start_ms").takeIf { it > 0 }
        }
}

/**
 * Поріг «додивився»: 92 % тривалості, а якщо титри починаються раніше — їхній початок. Титри
 * раніше половини — це не титри (помилкова мітка чи інша версія файлу), такі не беремо.
 */
internal fun finishLineMs(durationMs: Long, creditsStartMs: Long?): Long {
    val usual = (durationMs * FINISHED_FRACTION).toLong()
    val credits = creditsStartMs?.takeIf { it in (durationMs / 2)..durationMs } ?: return usual
    return minOf(usual, credits)
}
