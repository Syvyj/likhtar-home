package com.likhtar.home.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Тимчасове повідомлення внизу екрана — одне на всі сторінки: «Не вдалося відкрити
 * плеєр», «Позначку знято». Сторінка тайтла таких не показувала зовсім — причина лягала в
 * стан і там лишалась, і з дивана «натиснув — нічого» читалось як «завис». Зникає сама за
 * [NoticeMillis]; [onShown] прибирає його зі стану, щоб те саме повідомлення могло прийти знову.
 */
@Composable
fun Notice(message: String?, modifier: Modifier = Modifier, onShown: () -> Unit = {}) {
    if (message.isNullOrBlank()) return
    LaunchedEffect(message) {
        delay(NoticeMillis)
        onShown()
    }
    Text(
        text = message,
        style = LikhtarType.Body2,
        color = LikhtarColors.TextPrimary,
        modifier = modifier
            .padding(bottom = 18.dp)
            .clip(RoundedCornerShape(LikhtarDimens.CornerMedium))
            .background(LikhtarColors.BackgroundDeep.copy(alpha = 0.88f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private const val NoticeMillis = 5_000L

/**
 * Порожній стан на весь екран: що сталось, що робити, і дія. Досі сторінка тайтла
 * писала «Невідомий фільм» рядком у куті — і все.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(LikhtarDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Text(text = title, style = LikhtarType.HeroTitle, color = LikhtarColors.TextPrimary, textAlign = TextAlign.Center)
        message?.let {
            Text(
                text = it,
                style = LikhtarType.Body1,
                color = LikhtarColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
        if (actionLabel != null) {
            LikhtarButton(
                label = actionLabel,
                onClick = onAction,
                primary = true,
                modifier = Modifier.padding(top = 6.dp).focusRequester(rememberInitialFocusRequester()),
            )
        }
    }
}
