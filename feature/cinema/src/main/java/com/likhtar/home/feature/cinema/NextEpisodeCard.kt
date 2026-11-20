package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.Episode
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.LocalHighContrast
import com.likhtar.home.core.designsystem.LocalReduceMotion
import com.likhtar.home.core.designsystem.requestAfterFrame
import kotlinx.coroutines.delay

/**
 * «Наступна серія» після серії YouTube. [countdown] — серія майже напевно дограла:
 * наступна вмикається сама через 10 с (рекомендація Apple HIG); інакше — лише на OK.
 *
 * YouTube сам наступну не вмикає: без `finish_on_ended` він відкривається повним
 * застосунком, і BACK веде на його головну, а не до нас. Тож він
 * закривається по кінці, а далі грати пропонуємо ми. OK — одразу, BACK — скасувати.
 */
@Composable
internal fun NextEpisodeCard(
    episode: Episode,
    countdown: Boolean,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Не 0 — серія з наступного сезону: «Наступний сезон» замість «Наступна серія». */
    newSeason: Int = 0,
) {
    val reduceMotion = LocalReduceMotion.current
    val highContrast = LocalHighContrast.current
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
    val cardShape = RoundedCornerShape(20.dp)
    val cardBg = if (highContrast) LikhtarColors.Panel else LikhtarColors.Surface.copy(alpha = 0.94f)
    val cardBorder = if (highContrast) Color.White.copy(alpha = 0.53f) else Color.White.copy(alpha = 0.15f)
    val progress by animateFloatAsState(
        targetValue = if (countdown) (left.toFloat() / CountdownSeconds).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 950),
        label = "nextEpisodeCountdown",
    )
    Column(
        modifier = modifier
            .width(620.dp)
            .clip(cardShape)
            .background(cardBg)
            .border(1.dp, cardBorder, cardShape),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = episode.stillPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(220.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                val label = if (newSeason > 0) stringResource(R.string.cinema_season_n, newSeason) else stringResource(R.string.cinema_next_episode)
                Text(
                    if (countdown) stringResource(R.string.cinema_next_countdown, label, left) else label,
                    style = LikhtarType.Label,
                    color = LikhtarColors.Glow,
                )
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
        if (countdown) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.13f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(LikhtarColors.Glow),
                )
            }
        }
    }
}

private const val CountdownSeconds = 10
