package com.likhtar.home.feature.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarIcons
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.ProtoCardCorner
import com.likhtar.home.core.designsystem.cardSlot
import com.likhtar.home.core.designsystem.focusGlow

/**
 * Головна плитка Кінозалу — вхід у Ліхтарик. Розміром
 * як кадри «Продовжити»: ліхтар світить бурштином, поруч — що нового саме для мене (нові серії,
 * цього тижня) і що там є (пошук, календар, закладки).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LikhtarikTile(
    bookmarks: Int,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    onExitUp: (() -> Unit)?,
    focus: FocusRequester? = null,
) {
    val exitUp = if (onExitUp == null) Modifier else Modifier.onKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) { onExitUp(); true } else false
    }
    Column(Modifier.width(CatalogBannerWidth).cardSlot()) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(CatalogBannerWidth * 9f / 16f)
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .then(exitUp)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .focusGlow(ProtoCardCorner),
            shape = RoundedCornerShape(ProtoCardCorner),
            background = LikhtarColors.ShadeWarm,
            focusedBackground = LikhtarColors.ShadeWarm,
            borderColor = LikhtarColors.Faint,
            focusedScale = LikhtarDimens.CardFocusScale,
        ) { focused ->
            // Світло ліхтаря: тепла пляма зліва зверху, у фокусі розгоряється.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0f to LikhtarColors.Spill.copy(alpha = if (focused) 0.55f else 0.32f),
                        0.55f to LikhtarColors.Amber.copy(alpha = if (focused) 0.16f else 0.08f),
                        1f to Color.Transparent,
                        center = Offset(70f, 60f),
                        radius = 420f,
                    ),
                ).then(if (focused) Modifier.border(1.5.dp, LikhtarColors.Glow, RoundedCornerShape(ProtoCardCorner)) else Modifier),
            )
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LikhtarIcons.Lantern(size = 30.dp)
                    // Як назва фільму на картці: Oswald, великими, розріджено, з тінню.
                    Text(
                        stringResource(R.string.cinema_likhtarik).uppercase(),
                        style = TextStyle(
                            fontFamily = LikhtarType.Poster,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 24.sp,
                            letterSpacing = 1.5.sp,
                            shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 8f),
                        ),
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                // Що є в Ліхтарику — бейджами, а не цифрою.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Pill(stringResource(R.string.tracker_my_shows), accent = true)
                    Pill(stringResource(R.string.cinema_likhtarik_calendar), accent = true)
                    Pill(stringResource(R.string.cinema_what_to_watch))
                    Pill(stringResource(R.string.cinema_likhtarik_search))
                    Pill(if (bookmarks > 0) stringResource(R.string.cinema_likhtarik_bookmarks_n, bookmarks) else stringResource(R.string.cinema_bookmarks))
                    Pill(stringResource(R.string.cinema_likhtarik_history))
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, accent: Boolean = false) {
    Text(
        text,
        style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 8.sp),
        color = if (accent) LikhtarColors.Glow else LikhtarColors.Paper,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (accent) LikhtarColors.Amber.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
