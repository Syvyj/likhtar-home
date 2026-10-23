package com.likhtar.home.feature.cinema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.media.LocalFile
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.ProtoButton

/**
 * Кілька своїх файлів однієї назви — серії або версії фільму: вибір збоку, тайтл лишається
 * видно. Фокус — на [start] (наступна серія), а не на першій.
 */
@Composable
internal fun LocalFilesDialog(files: List<LocalFile>, start: Int, onPick: (LocalFile) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LikhtarDialog(onDismiss = onDismiss, side = true, width = 380.dp, armed = true) {
        Text(stringResource(R.string.cinema_play_local), style = LikhtarType.Label, color = LikhtarColors.TextPrimary)
        LazyColumn(modifier = Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Той самий файл у двох джерелах (NAS і комп'ютер) — інакше два однакові рядки.
            val manySources = files.map { it.sourceId }.distinct().size > 1
            itemsIndexed(files, key = { _, f -> f.sourceId + f.path }) { i, file ->
                val source = file.sourceName.takeIf { manySources && it.isNotBlank() }
                val label = when {
                    file.episode > 0 -> stringResource(R.string.cinema_local_episode, file.season, file.episode) + (source?.let { " · $it" } ?: "")
                    // Ім'я файлу довге й однакове в обох джерелах — джерело першим, щоб не обрізалось.
                    source != null -> "$source · ${file.name.substringBeforeLast('.')}"
                    else -> file.name.substringBeforeLast('.')
                }
                ProtoButton(
                    label = label,
                    onClick = { onPick(file) },
                    modifier = Modifier.fillMaxWidth().then(if (i == start) Modifier.focusRequester(focus) else Modifier),
                )
            }
        }
    }
}
