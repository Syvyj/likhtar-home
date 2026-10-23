package com.likhtar.home.core.data

import java.util.Locale

/**
 * Мова й країна контенту — одна точка правди для TMDB і нашого сервера.
 *
 * [chosen] — вибране людиною (майстер першого запуску); `false` — узяте з системи, і тоді
 * на сервер країну не шлемо: він лишається на своїй (прапорець `-country`), як досі.
 */
data class ContentLocale(val language: String, val country: String, val chosen: Boolean) {
    /** Мова для TMDB: «uk-UA», «pl-PL», «en-US». */
    val tmdbLanguage: String get() = TMDB_TAGS[language] ?: language

    companion object {
        /** Мови інтерфейсу й контенту на старті: решта систем — англійською. */
        val SUPPORTED = listOf("uk", "pl", "en")
        private val TMDB_TAGS = mapOf("uk" to "uk-UA", "pl" to "pl-PL", "en" to "en-US")

        fun of(language: String, country: String, system: Locale = Locale.getDefault()): ContentLocale {
            val lang = language.ifBlank { system.language.takeIf { it in SUPPORTED } ?: "en" }
            val region = country.ifBlank { system.country.ifBlank { "UA" } }.uppercase()
            return ContentLocale(lang, region, chosen = country.isNotBlank())
        }
    }
}

val LikhtarSettings.locale: ContentLocale get() = ContentLocale.of(contentLanguage, contentCountry)
