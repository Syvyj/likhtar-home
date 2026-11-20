package com.likhtar.home.feature.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.ShowState
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.catalog.TrackedShow
import com.likhtar.home.core.designsystem.CatalogPosterCard
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarType
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * «Мої серіали»: постер і під ним плашка статусу (макет «Новий Ліхтарик», екран 2).
 *
 * Статус рахує трекер сам; довге OK — спільна панель позначок ([RowMarks]): там «Кинути серіал»
 * і «Переглянуто до SxEy». Кинуті тут не малюються.
 */
@Composable
fun MyShowsRail(
    shows: List<TrackedShow>,
    onOpen: (TrackedShow) -> Unit,
    onLongClick: (TrackedShow) -> Unit,
    modifier: Modifier = Modifier,
    /** Лише кинуті — для згорнутого розділу «Кинуті», звідки серіал можна повернути. */
    onlyDropped: Boolean = false,
) {
    val visible = shows.filter { (it.state == ShowState.DROPPED) == onlyDropped }
    LikhtarRail(modifier = modifier, gap = 12.dp) {
        items(visible, key = { it.item.id }) { show ->
            // Статус і прогрес — НАД постером: під постером лишається тільки підпис у фокусі,
            // і статус не відривається від свого постера порожньою смугою.
            Column(modifier = Modifier.width(PosterW).padding(vertical = 10.dp)) {
                // Місце під статус — однакове в усіх: серіал без статусу інакше підскакував вище за ряд.
                Box(Modifier.height(StatusH).padding(bottom = 5.dp), contentAlignment = Alignment.BottomStart) { StatusChip(show, Modifier) }
                if (show.state == ShowState.WATCHING && show.total > 0) {
                    Box(Modifier.padding(bottom = 7.dp).fillMaxWidth().height(2.dp).background(LikhtarColors.Border)) {
                        Box(
                            Modifier.fillMaxWidth((show.watched.toFloat() / show.total).coerceIn(0f, 1f)).height(2.dp)
                                .background(LikhtarColors.Amber),
                        )
                    }
                } else {
                    Spacer(Modifier.height(9.dp))
                }
                CatalogPosterCard(
                    title = show.item.title,
                    year = show.item.year,
                    posterUrl = TmdbImages.poster(show.item.posterPath),
                    width = PosterW,
                    height = PosterH,
                    onClick = { onOpen(show) },
                    onLongClick = { onLongClick(show) },
                )
            }
        }
    }
}

@Composable
private fun StatusChip(show: TrackedShow, modifier: Modifier) {
    val text = when (show.state) {
        ShowState.NEW -> stringResource(R.string.tracker_new, show.season, show.episode)
        ShowState.WATCHING -> stringResource(R.string.tracker_watching, show.watched, show.total)
        ShowState.WAITING_EPISODE -> stringResource(R.string.tracker_waiting_episode, shortDay(show.date))
        ShowState.WAITING_SEASON -> stringResource(R.string.tracker_waiting_season)
        ShowState.DONE -> stringResource(R.string.tracker_done)
        ShowState.DROPPED -> stringResource(R.string.tracker_dropped)
        ShowState.NONE -> return
    }
    // Той самий контурний стиль, що плашки на картках: «Нова серія» — бурштином, решта — світлі.
    com.likhtar.home.core.designsystem.OutlineBadge(text = text, amber = show.state == ShowState.NEW, filled = true, modifier = modifier)
}

/** «пт» — день тижня мовою інтерфейсу; ISO-дата без години. */
internal fun shortDay(iso: String): String = runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!
    SimpleDateFormat("EE", Locale.getDefault()).format(date).lowercase().trimEnd('.')
}.getOrDefault("")

private val PosterW = 112.dp
private val StatusH = 21.dp
private val PosterH = 150.dp
