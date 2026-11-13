package com.likhtar.home.core.catalog

import android.content.Intent
import android.net.Uri
import android.os.Parcelable

/**
 * Що саме віддати зовнішньому плеєру — чиста функція над [PlayRequest], без Android.
 *
 * Жила інлайном у `LikhtarApp` на ~120 рядків і без жодного тесту, хоча це найламкіше місце
 * лаунчера: ключі extras у кожного плеєра свої. Тепер рішення — тут і
 * під тестом, а [toIntent] лише розкладає план в Intent.
 *
 * [takesM3u] — чи бере цільовий плеєр плейлист сезону (m3u); питає PackageManager, тож
 * вирішує викликач. [companion] — extras «Паузи, що знає» для Just+.
 */
data class PlayerPlan(
    val data: String,
    val mime: String,
    val packageName: String,
    /** Серія й черга `video_list` замість плейлиста: плеєр m3u не бере (Just+, Just Player). */
    val asQueue: Boolean,
    /** Значення: String, Boolean, Int, Long, Array<String> або [UriList]. Порядок — як ставили. */
    val extras: Map<String, Any>,
) {
    /** Список адрес, що в Intent їде масивом `Uri` (Parcelable). */
    data class UriList(val uris: List<String>)

    fun toIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        val plan = this@PlayerPlan
        setDataAndType(Uri.parse(plan.data), plan.mime)
        // Програма ТБ-режиму — файл лаунчера: дозвіл на читання їде через ClipData (extras його не несуть).
        (plan.extras["likhtar.tv.guide"] as? String)?.let { guide ->
            clipData = android.content.ClipData.newRawUri("guide", Uri.parse(guide))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (plan.packageName.isNotBlank()) setPackage(plan.packageName)
        plan.extras.forEach { (key, value) ->
            when (value) {
                is String -> putExtra(key, value)
                is Boolean -> putExtra(key, value)
                is Int -> putExtra(key, value)
                is Long -> putExtra(key, value)
                is UriList -> putExtra(key, value.uris.map { Uri.parse(it) }.toTypedArray<Parcelable>())
                is Array<*> -> putExtra(key, value.map { it.toString() }.toTypedArray())
            }
        }
    }
}

fun PlayRequest.playerPlan(takesM3u: Boolean, companion: Map<String, String>): PlayerPlan {
    val asQueue = !takesM3u && single.isNotBlank()
    val extras = LinkedHashMap<String, Any>()
    // «Пауза, що знає»: наш Just+ бере звідси, куди питати й для якого профілю.
    extras.putAll(companion)
    title?.item?.let { extras["likhtar.kind"] = if (it.kind == TitleKind.TV) "tv" else "movie" }
    // ТБ-режим Just+ (tv/TvChannels у плеєрі): ↑↓ і «канал ±» перемикають ці канали.
    tv?.let { lineup ->
        extras["likhtar.tv.urls"] = lineup.urls.toTypedArray()
        extras["likhtar.tv.names"] = lineup.names.toTypedArray()
        extras["likhtar.tv.now"] = lineup.now.toTypedArray()
        extras["likhtar.tv.index"] = lineup.index
        if (lineup.offset > 0) extras["likhtar.tv.offset"] = lineup.offset
        if (lineup.guide.isNotBlank()) extras["likhtar.tv.guide"] = lineup.guide
        // Канали порталу (stalker://): обліковка, щоб «Ліхтар ТБ» сам брав адресу потоку в порталі.
        if (lineup.portal.isNotBlank()) extras["likhtar.tv.portal"] = lineup.portal
    }
    val (data, mime) = when {
        asQueue -> single to "video/*"
        // Випуск порталу — HLS під mime плейлиста. Just+ такого не claim'ить, і запуск падав
        // з «Не вдалося відкрити плеєр»: потік той самий, лише як відео.
        !takesM3u -> uri to "video/*"
        else -> uri to mime
    }
    if (asQueue && queue.size > 1) {
        extras["video_list"] = PlayerPlan.UriList(queue.map { it.url })
        // «Шьоґун · S01E01 · Анджін · 1080p · WEB-DL · Торент»: джерело — у назві, інакше
        // після переходу на іншу серію не видно, з чого вона грає.
        val show = title?.item?.title.orEmpty()
        extras["video_list.name"] = queue.map { e ->
            listOf(show, cleanStreamLabel(e.name)).filter(String::isNotBlank).joinToString(" · ")
        }.toTypedArray()
        TmdbImages.poster(title?.item?.posterPath)?.let { poster ->
            extras["video_list.thumbnail"] = queue.map { poster }.toTypedArray()
        }
        extras["video_list.season"] = queue.map { (title?.season ?: 0).toString() }.toTypedArray()
        extras["video_list.episode"] = queue.map { it.episode.toString() }.toTypedArray()
        title?.item?.tmdbId?.takeIf { it > 0 }?.let { id ->
            extras["video_list.id"] = queue.map { id.toString() }.toTypedArray()
        }
    }
    // Ключі позиції різних плеєрів: MX/Just+ — position, VLC — extra_position,
    // ViMu — startfrom (без нього ViMu починав з нуля й питав «продовжити?»).
    if (resumeMs > 0L) {
        extras["position"] = resumeMs.toInt()
        extras["extra_position"] = resumeMs
        extras["startfrom"] = resumeMs.toInt()
    }
    // Назва в плеєрі — замість імені файлу роздачі чи «index.m3u8». `title` читають MX,
    // Just Player і VLC, `forcename` — ViMu.
    if (title == null && displayName.isNotBlank()) {
        extras["title"] = displayName
        extras["forcename"] = displayName
    }
    title?.let { t ->
        val name = if (t.season > 0 && t.episode > 0) "${t.item.title} · S${t.season}E${t.episode}" else t.item.title
        extras["title"] = name
        extras["forcename"] = name
        // Just+ (і MX-сумісні): постер у шапці, а TMDB id із сезоном і серією — ключ, за яким
        // Just+ сам шукає заставки й титри для пропуску.
        TmdbImages.poster(t.item.posterPath)?.let { extras["thumbnail"] = it }
        // Шапка панелі паузи Just+: що це, рік, оцінка й опис (інші плеєри ключі ігнорують).
        extras["likhtar.name"] = t.item.title
        if (t.item.overview.isNotBlank()) extras["likhtar.overview"] = t.item.overview
        if (t.item.year.isNotBlank()) extras["likhtar.year"] = t.item.year
        if (t.item.rating > 0) extras["likhtar.rating"] = String.format(java.util.Locale.ROOT, "%.1f", t.item.rating)
        if (t.item.tmdbId > 0) extras["id"] = t.item.tmdbId.toString()
        if (t.season > 0 && t.episode > 0) {
            extras["season"] = t.season
            extras["episode"] = t.episode
        }
    }
    // Озвучки й джерела серії — у панель «Якість» Just+: для черги — кожної серії окремо,
    // бо в кожної свої адреси.
    if (asQueue && queue.size > 1 && queueVariants.size == queue.size) {
        queueVariants.forEachIndexed { i, v ->
            if (v.size > 1) {
                extras["video_list.quality_levels.$i"] = v.map { it.first }.toTypedArray()
                extras["video_list.quality_urls.$i"] = v.map { it.second }.toTypedArray()
            }
        }
    } else if (variants.size > 1) {
        if (asQueue && queue.size > 1) {
            val i = queue.indexOfFirst { it.url == single }
            if (i >= 0) {
                extras["video_list.quality_levels.$i"] = variants.map { it.first }.toTypedArray()
                extras["video_list.quality_urls.$i"] = variants.map { it.second }.toTypedArray()
            }
        } else {
            extras["quality_levels"] = variants.map { it.first }.toTypedArray()
            extras["quality_urls"] = variants.map { it.second }.toTypedArray()
        }
    }
    // Випуск порталу: назва шоу й випуску замість адреси потоку.
    episode?.let { ep ->
        val name = listOf(ep.title, ep.episodeTitle).filter { it.isNotBlank() }.joinToString(" · ")
        if (name.isNotBlank()) {
            extras["title"] = name
            extras["forcename"] = name
        }
    }
    // Позицію й тривалість на виході — за контрактом MX (Just+ без цього мовчить).
    extras["return_result"] = true
    if (subs.isNotEmpty()) {
        extras["subs"] = PlayerPlan.UriList(subs.map { it.second })
        extras["subs.name"] = subs.map { it.first }.toTypedArray()
    }
    return PlayerPlan(data, mime, packageName, asQueue, extras)
}
