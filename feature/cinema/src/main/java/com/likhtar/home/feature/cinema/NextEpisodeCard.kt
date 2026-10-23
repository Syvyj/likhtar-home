package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.Episode
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.requestAfterFrame
import kotlinx.coroutines.delay

/**
 * «Наступна серія» після серії YouTube. [countdown] — серія майже напевно дограла:
 * наступна вмикається сама через 5 с; інакше — лише на OK.
 *
 * YouTube сам наступну не вмикає: без `finish_on_ended` він відкривається повним
 * застосунком, і BACK веде на його головну, а не до нас. Тож він
 * закривається по кінці, а далі грати пропонуємо ми. OK — одразу, BACK — скасувати.
 */
@Composable
internal fun NextEpisodeCard(
    episode: Episode,
    countdown: Boolean,
    /** Не 0 — серія з наступного сезону: «Наступний сезон» замість «Наступна серія». */
    newSeason: Int = 0,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var left by remember(episode.number) { mutableIntStateOf(CountdownSeconds) }
    val playFocus = remember { FocusRequester() }
    BackHandler(onBack = onCancel)
    LaunchedEffect(episode.number) {
        playFocus.requestAfterFrame()
        if (!countdown) return@LaunchedEffect
        while (left > 0) {
            delay(1_000)
            left--
        }
        onPlay()
    }
    Row(
        modifier = modifier
            .width(620.dp)
            .clip(RoundedCornerShape(LikhtarDimens.CornerMedium))
            .background(LikhtarColors.CardBlack)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = episode.stillPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(LikhtarDimens.CornerSmall)),
        )
        Column(modifier = Modifier.weight(1f)) {
            val label = if (newSeason > 0) stringResource(R.string.cinema_season_n, newSeason) else stringResource(R.string.cinema_next_episode)
            Text(if (countdown) stringResource(R.string.cinema_next_countdown, label, left) else label, style = LikhtarType.Label, color = LikhtarColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(
                episode.title,
                style = LikhtarType.Body1,
                color = LikhtarColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionPill(label = stringResource(R.string.cinema_watch_plain), modifier = Modifier.focusRequester(playFocus), onClick = onPlay)
                ActionPill(label = stringResource(R.string.cinema_cancel), onClick = onCancel)
            }
        }
    }
}

private const val CountdownSeconds = 5
