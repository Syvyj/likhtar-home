package com.likhtar.home.core.catalog

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Серія, що вже вийшла: на неї вказує дзвіночок у шапці. */
data class AiredEpisode(
    val item: CatalogItem,
    val season: Int,
    val episode: Int,
    val name: String,
    /** ISO-дата виходу. */
    val airDate: String,
) {
    val key: String get() = "${item.tmdbId}:$season:$episode"
}

private val Context.newEpisodesStore: DataStore<Preferences> by preferencesDataStore(name = "likhtar_new_episodes")

/**
 * Дзвіночок «Нові серії»: серіали з «Продовжити» й закладок, у яких за останні [WINDOW_DAYS]
 * днів вийшла серія, ще не переглянута. Бачені (вікно відкривали) — без лічильника.
 */
@Singleton
class NewEpisodes @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdb: TmdbClient,
    private val watchlist: WatchlistRepository,
    private val progress: WatchProgressRepository,
) {
    val seen: Flow<Set<String>> = context.newEpisodesStore.data.map { it[SEEN].orEmpty() }

    @Volatile private var cache: Pair<Long, List<AiredEpisode>>? = null

    suspend fun load(force: Boolean = false): List<AiredEpisode> {
        cache?.takeIf { !force && System.currentTimeMillis() - it.first < CACHE_MS }?.let { return it.second }
        val watching = runCatching { progress.continueWatching(limit = 40, all = true, kind = "cinema") }
            .getOrDefault(emptyList()).filter { it.kind == TitleKind.TV }
        // Де людина вже є в серіалі: серія, що вийшла, але стоїть до «наступної», — переглянута.
        val reached = watching.associate { it.tmdbId to (it.season to it.nextEpisode) }
        val ids = (watching.map { it.tmdbId } + watchlist.items.first().filter { it.kind == TitleKind.TV }.map { it.tmdbId })
            .distinct().take(MAX_SHOWS)
        // SimpleDateFormat, а не java.time: minSdk 24.
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val now = System.currentTimeMillis()
        val today = format.format(java.util.Date(now))
        val since = format.format(java.util.Date(now - WINDOW_DAYS * 24 * 60 * 60 * 1000))
        val found = coroutineScope { ids.map { async { tmdb.lastAired(it) } }.map { it.await() } }
            .filterNotNull()
            .filter { it.airDate in since..today && !watched(it, reached[it.item.tmdbId]) }
            .sortedByDescending { it.airDate }
        cache = System.currentTimeMillis() to found
        return found
    }

    suspend fun markSeen(list: List<AiredEpisode>) {
        context.newEpisodesStore.edit { prefs ->
            // Лише поточні: старі серії випадають із вікна й зі сховища разом.
            prefs[SEEN] = list.map { it.key }.toSet()
        }
    }

    companion object {
        const val WINDOW_DAYS = 14L
        private const val MAX_SHOWS = 40
        private const val CACHE_MS = 2 * 60 * 60 * 1000L
        private val SEEN = stringSetPreferencesKey("seen")

        /** Серія за «наступною» в «Продовжити» або на ній із позначкою переглянутого — бачена. */
        internal fun watched(aired: AiredEpisode, next: Pair<Int, Int>?): Boolean {
            val (season, episode) = next ?: return false
            return aired.season < season || (aired.season == season && aired.episode < episode)
        }
    }
}
