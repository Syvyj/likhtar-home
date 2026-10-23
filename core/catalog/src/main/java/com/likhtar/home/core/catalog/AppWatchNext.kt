package com.likhtar.home.core.catalog

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * «Продовжити перегляд» інших застосунків — системна таблиця Android TV `watch_next_program`.
 *
 * Netflix, YouTube, Sweet та інші самі пишуть туди, на чому зупинились; стоковий домашній
 * екран будує з неї свій ряд. Ми — так само: без цього серіал, увімкнений у Netflix, не
 * було видно ніде. Доступ — дозвіл `READ_TV_LISTINGS`, без нього
 * провайдер віддає лише наші власні рядки, тобто порожньо.
 *
 * MEGOGO звідси не беремо: його «Продовжити» сервер читає з акаунта — повніше й з постерами.
 */
@Singleton
class AppWatchNext @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun programs(): List<ServiceResume> = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "WatchNext: немає дозволу READ_TV_LISTINGS")
            return@withContext emptyList()
        }
        val rows = runCatching {
            context.contentResolver.query(URI, COLUMNS, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val pkg = c.getString(0).orEmpty()
                        val intent = c.getString(5).orEmpty()
                        if (pkg.isBlank() || pkg in SKIP || intent.isBlank() || pkg == context.packageName) continue
                        val title = c.getString(1).orEmpty()
                        if (title.isBlank()) continue
                        val season = c.getString(3).orEmpty()
                        val episode = c.getString(4).orEmpty()
                        val position = c.getLong(7)
                        val duration = c.getLong(8)
                        add(
                            ServiceResume(
                                title = title,
                                imageUrl = (c.getString(9) ?: c.getString(6))?.ifBlank { null },
                                deeplink = intent,
                                packageName = pkg,
                                updatedAt = c.getLong(10) / 1000,
                                label = listOfNotNull(
                                    if (season.isNotBlank() && episode.isNotBlank()) "S${season.padStart(2, '0')}E${episode.padStart(2, '0')}" else null,
                                    appLabel(pkg),
                                ).joinToString(" · "),
                                progress = if (duration > 0 && position > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.onFailure { Log.w(TAG, "WatchNext не прочитався", it) }.getOrDefault(emptyList())
        Log.i(TAG, "WatchNext: ${rows.size} — " + rows.groupingBy { it.packageName }.eachCount())
        // Один рядок на назву: застосунок пише окремий запис на серію.
        rows.sortedByDescending { it.updatedAt }.distinctBy { it.packageName to it.title }
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private companion object {
        const val TAG = "LikhtarWatchNext"
        const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
        val URI: Uri = Uri.parse("content://android.media.tv/watch_next_program")
        val SKIP = com.likhtar.home.core.model.StreamingService.MEGOGO.packages.toSet()

        // Рядками, а не з TvContract: імена стабільні від API 26.
        val COLUMNS = arrayOf(
            "package_name", "title", "episode_title", "season_display_number", "episode_display_number",
            "intent_uri", "poster_art_uri", "last_playback_position_millis", "duration_millis",
            "thumbnail_uri", "last_engagement_time_utc_millis",
        )
    }
}
