package com.likhtar.home.feature.cinema

import com.likhtar.home.core.catalog.CatalogItem

// UaAudioRow.kt — правило допуску по озвучці для рядів КІНОЗАЛУ.
//
// Жило в RealityRows, поки ряди Реаліті будував клієнт. Тепер їх будує сервер (він же й
// стереже це правило в SQL), а тут лишається той самий закон для Кінозалу, де ряди
// й далі приходять із TMDB.

/**
 * Ряд — це каталог у його власному порядку; українська озвучка — лише мітка на картці.
 *
 * Досі непідтверджене випадало з ряду, а коли підтверджених було 6+, решта не
 * показувалась навіть у «Відкрити всі»: «Фантастика» за сто років кіно — 7 карток. Тепер
 * так: нічого не відсіюємо й не пересортовуємо, просто показуємо, де є
 * переклад (`CatalogBannerCard(uaAudio=)`). Лише коли людина сама вимкнула «Показувати без
 * озвучки», лишаємо підтверджене — від найсвіжішого.
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
    if (fillWithUnconfirmed) return items
    return items.filter { it.tmdbId in confirmedUaIds }
        .sortedWith(compareByDescending(freshness).thenByDescending(preferUkr))
}
