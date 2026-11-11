package com.likhtar.home.episodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.R
import com.likhtar.home.core.catalog.AiredEpisode
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.NewEpisodes
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NewEpisodesState(val list: List<AiredEpisode> = emptyList(), val unseen: Int = 0)

@HiltViewModel
class NewEpisodesViewModel @Inject constructor(private val source: NewEpisodes) : ViewModel() {
    private val list = MutableStateFlow<List<AiredEpisode>>(emptyList())

    val state: StateFlow<NewEpisodesState> = combine(list, source.seen) { l, seen ->
        NewEpisodesState(l, l.count { it.key !in seen })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NewEpisodesState())

    init {
        // Приставка живе тижнями без перезапуску: перечитуємо раз на дві години (кеш там само).
        viewModelScope.launch {
            while (true) {
                runCatching { list.value = source.load() }
                delay(REFRESH_MS)
            }
        }
    }

    fun markSeen() {
        viewModelScope.launch { source.markSeen(list.value) }
    }

    private companion object {
        const val REFRESH_MS = 2 * 60 * 60 * 1000L + 60_000L
    }
}

/** Вікно дзвіночка: нові серії, OK — сторінка серіалу. */
@Composable
fun NewEpisodesDialog(
    list: List<AiredEpisode>,
    onOpen: (CatalogItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LikhtarDialog(onDismiss = onDismiss, width = 560.dp, side = true, armed = true) {
        Text(stringResource(R.string.new_episodes_title), style = LikhtarType.PageTitle, color = LikhtarColors.TextPrimary)
        Text(stringResource(R.string.new_episodes_hint), style = LikhtarType.Body1, color = LikhtarColors.TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 520.dp)) {
            items(list, key = { it.key }) { ep ->
                FocusableSurface(
                    onClick = { onOpen(ep.item) },
                    modifier = Modifier.fillMaxWidth().then(if (ep == list.first()) Modifier.focusRequester(first) else Modifier),
                    shape = RoundedCornerShape(12.dp),
                    background = LikhtarColors.Surface,
                    focusedBackground = LikhtarColors.GlassActive,
                    focusedScale = 1.02f,
                ) { focused ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(8.dp)) {
                        AsyncImage(
                            model = TmdbImages.posterThumb(ep.item.posterPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(44.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)),
                        )
                        Column {
                            val ink = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextPrimary
                            Text(ep.item.title, style = LikhtarType.TitleLead, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(String.format(java.util.Locale.US, "S%02dE%02d", ep.season, ep.episode), ep.name, ep.airDate.drop(5).split('-').reversed().joinToString("."))
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = LikhtarType.Body1,
                                color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
