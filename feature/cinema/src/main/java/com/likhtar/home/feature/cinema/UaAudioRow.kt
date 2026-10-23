package com.likhtar.home.feature.cinema

import com.likhtar.home.core.catalog.CatalogItem

// UaAudioRow.kt — правило допуску по озвучці для рядів КІНОЗАЛУ.
//
// Жило в RealityRows, поки ряди Реаліті будував клієнт. Тепер їх будує сервер (він же й
// стереже це правило в SQL), а тут лишається той самий закон для Кінозалу, де ряди
// й далі приходять із TMDB.

/**
 * Ряд збирається за ОДНИМ правилом: у нього входить лише те, що можна ввімкнути.
 *
 * Досі підтверджена озвучка була першим ключем сортування, а не умовою допуску. Через це
 * ряд відкривався перевіреною позицією 2019 року, за нею йшов учорашній реліз, а далі —
 * усе інше, чого подивитись нема чим. Це «тонни контенту, де нема як
 * подивитись» — точний опис наслідку.
 *
 * Тепер підтверджені йдуть від найсвіжішого до старого — і більше нічого. Непідтверджені
 * добирають ряд лише тоді, коли ввімкнено «Показувати без озвучки» І підтверджених менше
 * за [MIN_ROW]: ряд, у якому й так є що дивитись, розбавляти нема потреби. Вони йдуть
 * після підтверджених і мають на картці свою мітку — див. `CatalogBannerCard(uaAudio=)`.
 */
internal fun uaAudioRow(
    items: List<CatalogItem>,
    confirmedUaIds: Set<Int>,
    freshness: (CatalogItem) -> String,
    fillWithUnconfirmed: Boolean,
    /**
     * Українська доріжка проти чужої — ДРУГИЙ ключ, після дати.
     *
     * У Кінозалі чужих доріжок у ряду немає взагалі, тож там це завжди `true` і нічого
     * не міняє. У Реаліті дозволена й російська, і тоді правило таке: спершу свіже, а
     * між двома однаково свіжими — українське.
     */
    preferUkr: (CatalogItem) -> Boolean = { true },
): List<CatalogItem> {
    val (confirmed, rest) = items.partition { it.tmdbId in confirmedUaIds }
    val head = confirmed.sortedWith(
        compareByDescending(freshness).thenByDescending(preferUkr),
    )
    if (!fillWithUnconfirmed || head.size >= MIN_ROW) return head
    return head + rest.sortedByDescending(freshness)
}

/**
 * Скільки підтверджених позицій робить ряд самодостатнім.
 *
 * Видимих карток у ряду десять ([RAIL_ITEMS]), тож шість — це більше половини екрана ряду:
 * далі доповнювати нема сенсу, глядач і так має з чого вибрати.
 */
internal const val MIN_ROW = 6
