package com.likhtar.home.feature.cinema

import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.data.AppsRepository

/**
 * Картка ряду «Трейлери» — ролик в офіційному YouTube TV: той самий запуск, що в трейлера на
 * сторінці тайтла (`/watch` без CLEAR_TASK, `finish_on_ended`), тож один BACK — назад на плитку.
 * `false` — це не трейлер, хай відкриває звичайна логіка.
 */
internal fun AppsRepository.openTrailer(item: CatalogItem): Boolean =
    item.trailerKey.isNotBlank() && launchIntentUri("https://www.youtube.com/watch?v=${item.trailerKey}", null)
