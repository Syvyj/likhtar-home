package com.likhtar.home.core.catalog

/**
 * Універсальний зовнішній плеєр — застосунок, який відкриває будь-який фільм за посиланням
 * на TMDB, а не за внутрішнім id сервісу.
 *
 * Каталог не знає, які саме це застосунки: набір постачає збірка через Hilt
 * (`@Provides @IntoSet`). У стор-збірці набір порожній, тому в її APK немає ні цих пакетів,
 * ні їхніх назв — саме заради цього тут узагалі є абстракція, а не список у резолвері.
 */
data class ExternalPlayer(
    /** Показується в кнопці: «Відкрити в …». */
    val label: String,
    /** Кандидати в порядку переваги — беремо перший встановлений. */
    val packages: List<String>,
    /** Шаблон посилання: `{path}` — `movie`/`tv`, `{id}` — TMDB id. */
    val uriTemplate: String,
) {
    fun uriFor(tmdbPath: String, tmdbId: Int): String =
        uriTemplate.replace("{path}", tmdbPath).replace("{id}", tmdbId.toString())
}
