package com.likhtar.home.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * Легкий скелетон каталогу за Apple HIG: одразу показує структуру Hero + рядів,
 * поки вантажиться TMDB, без важких шейдерів і без порожнього екрана.
 */
@Composable
fun CatalogSkeleton(modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val pulse = if (reduceMotion) {
        0.4f
    } else {
        val transition = rememberInfiniteTransition(label = "catalogSkeleton")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "skeletonPulse",
        )
        value
    }
    val blockColor = lerp(LikhtarColors.SkeletonBase, LikhtarColors.SkeletonHighlight, pulse)
    val accentBlock = lerp(LikhtarColors.SkeletonAccentBase, LikhtarColors.SkeletonAccentHighlight, pulse)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LikhtarDimens.ScreenPadding, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Hero placeholder
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 34.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .width(110.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentBlock),
            )
            Box(
                Modifier
                    .width(340.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(blockColor),
            )
            Box(
                Modifier
                    .width(420.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(blockColor),
            )
            Box(
                Modifier
                    .width(310.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(blockColor),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(136.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                    .background(accentBlock),
            )
        }

        // First shelf placeholder (wide 16:9 cards)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .width(160.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(blockColor),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(4) {
                    Box(
                        Modifier
                            .width(214.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(ProtoCardCorner))
                            .background(blockColor),
                    )
                }
            }
        }
    }
}
