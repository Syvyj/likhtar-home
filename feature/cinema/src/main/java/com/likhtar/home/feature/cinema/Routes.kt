package com.likhtar.home.feature.cinema

import kotlinx.serialization.Serializable

/*
 * Маршрути екранів Кінозалу й Реаліті — типобезпечні.
 *
 * Доти це були рядки-шаблони з ручним кодуванням аргументів: ключ добірки йшов у шлях без
 * кодування, і назва з «/» ламала маршрут; кожна ViewModel сама розбирала свій рядок. Тепер
 * аргументи кодує й розбирає навігація, а помилку в маршруті ловить компілятор.
 */

/** Сторінка тайтла TMDB: [kind] — `TitleKind.name`. */
@Serializable
data class TitleRoute(val kind: String, val id: Int)

/** Добірка, стрімінг, франшиза: [kind] — `CatalogListingKind.name`. */
@Serializable
data class CatalogListRoute(val kind: String, val key: String)

/** Сторінка шоу Реаліті за id позиції. */
@Serializable
data class RealityShowRoute(val id: String)

/** «Відкрити всі» ряду Реаліті або плитка бренду (назва з префіксом). */
@Serializable
data class RealityListRoute(val title: String)

/** Усі стрімінги й телеканали сіткою. */
@Serializable
data object StreamingsRoute
