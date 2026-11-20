package com.likhtar.home.feature.search

import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.ContinueItem
import com.likhtar.home.core.catalog.ShowState
import com.likhtar.home.core.catalog.TrackedShow

/** Що стоїть героєм угорі Ліхтарика: те, що змінилось для людини сьогодні. */
sealed interface HeroPick {
    data class NewEpisode(val show: TrackedShow) : HeroPick
    data class Continue(val item: ContinueItem) : HeroPick
    data class Advice(val item: CatalogItem, val why: String) : HeroPick
}

/**
 * Нова серія мого серіалу → інакше «Продовжити» → інакше порада дня. Порада стала на добу:
 * той самий тайтл увесь день, наступного — інший. Без запиту до моделі.
 */
fun pickHero(
    shows: List<TrackedShow>,
    cont: List<ContinueItem>,
    advice: List<Pair<CatalogItem, String>>,
    today: String,
): HeroPick? {
    shows.filter { it.state == ShowState.NEW }.maxByOrNull { it.date }?.let { return HeroPick.NewEpisode(it) }
    cont.firstOrNull()?.let { return HeroPick.Continue(it) }
    if (advice.isEmpty()) return null
    val (item, why) = advice[Math.floorMod(today.hashCode(), advice.size)]
    return HeroPick.Advice(item, why)
}

/**
 * «Подивились?» — лише якщо після запуску з героя Ліхтар справді ховався за іншим застосунком
 * (сервісом, плеєром). Повернення зі сторінки тайтла без перегляду — не привід питати.
 */
fun shouldAskWatched(startedAt: Long, stoppedAt: Long): Boolean = startedAt > 0 && stoppedAt > startedAt
