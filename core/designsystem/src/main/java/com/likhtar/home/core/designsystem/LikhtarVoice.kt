package com.likhtar.home.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * Голос Ліхтарика — на його сторінці й в асистенті (.voice прототипу): знак 60 px, «Ліхтарик» і стан; думка 33 px / 500 теплим білим. Поки думає — знак
 * розгоряється, а теплий відсвіт за ним наростає (єдиний рух сторінки).
 */
@Composable
fun LikhtarVoice(
    name: String,
    status: String,
    text: String,
    thinking: Boolean,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 16.5.sp,
    maxLines: Int = 6,
) {
    val lamp by animateFloatAsState(if (thinking) 1f else 0.35f, tween(if (thinking) 500 else 1200), label = "lamp")
    Box(modifier) {
        Box(
            Modifier
                .offset(x = (-65).dp, y = (-75).dp)
                .size(210.dp)
                .graphicsLayer { alpha = lamp }
                .background(Brush.radialGradient(0f to LikhtarColors.Spill.copy(alpha = 0.34f), 0.55f to LikhtarColors.Amber.copy(alpha = 0.1f), 1f to Color.Transparent)),
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LikhtarIcons.Lantern(size = 30.dp, onLight = thinking)
                Column {
                    Text(
                        name,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                        color = Color.White,
                    )
                    Text(
                        status,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 10.5.sp),
                        color = LikhtarColors.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 13.dp)) {
                Text(
                    text,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = textSize, lineHeight = textSize * 1.36f, letterSpacing = (-0.08).sp),
                    color = LikhtarColors.Paper,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (thinking) {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .size(7.dp, 15.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(LikhtarColors.Glow),
                )
            }
        }
    }
}

