package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRelevanceTest {
    @Test
    fun `exact avatar match outranks popular unrelated titles`() {
        val candidates = listOf(
            candidate(1, "Аватар: Шлях води", 0, 9_000.0, 50_000),
            candidate(2, "The Last Airbender Archive", 1, 8_000.0, 40_000),
            candidate(3, "Аватар", 12, 1.0, 3),
        )

        val sorted = SearchRelevance.sort("AVATAR", candidates)

        // Exact matching also considers original title, so the Ukrainian localized card wins.
        assertEquals(3, sorted.first().tmdbId)
    }

    @Test
    fun `word matches preserve TMDB order before popularity`() {
        val candidates = listOf(
            candidate(1, "Школа", 0, 1.0, 1),
            candidate(2, "Школа життя", 1, 1000.0, 1000),
        )

        assertEquals(listOf(1, 2), SearchRelevance.sort("школ", candidates).map { it.tmdbId })
    }

    private fun candidate(
        id: Int,
        title: String,
        index: Int,
        popularity: Double,
        votes: Int,
    ) = SearchCandidate(
        item = CatalogItem(
            tmdbId = id,
            kind = TitleKind.MOVIE,
            title = title,
            originalTitle = if (id == 3) "Avatar" else title,
            year = "2024",
        ),
        originalIndex = index,
        popularity = popularity,
        voteCount = votes,
    )
}
