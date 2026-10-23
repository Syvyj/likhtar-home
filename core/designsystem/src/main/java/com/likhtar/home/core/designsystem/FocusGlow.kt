package com.likhtar.home.core.designsystem

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Теплий ореол під карткою у фокусі — тінь прототипів `0 26px 60px -16px` бурштином:
 * прямокутник картки, звужений на 8 dp, зсунутий на 13 dp униз і розмитий на 30 dp.
 * Малюється лише під однією карткою (тією, що у фокусі), тож розмиття тут копійчане.
 * Кільце фокуса — у самій картці, щоб росло разом із нею.
 */
@Composable
fun Modifier.focusGlow(corner: Dp): Modifier {
    var focused by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "glow")
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    return this
        .onFocusChanged { focused = it.hasFocus }
        .drawBehind {
            if (glow <= 0f) return@drawBehind
            val blur = 30.dp.toPx()
            // До API 28 апаратне полотно BlurMaskFilter ігнорує — тоді ореолу просто немає.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@drawBehind
            paint.color = LikhtarColors.Amber.copy(alpha = 0.55f * glow).toArgb()
            paint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            val inset = 8.dp.toPx()
            val dy = 13.dp.toPx()
            val r = corner.toPx()
            drawIntoCanvas {
                it.nativeCanvas.drawRoundRect(inset, inset + dy, size.width - inset, size.height - inset + dy, r, r, paint)
            }
        }
}


/** Біле кільце 5 px прототипу (`box-shadow: 0 0 0 5px var(--white)`) — по краю картки у фокусі. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.FocusRing(focused: Boolean, corner: Dp) {
    if (focused) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .matchParentSize()
                .border(2.5.dp, LikhtarColors.Paper, androidx.compose.foundation.shape.RoundedCornerShape(corner)),
        )
    }
}

/** Скруглення карток прототипу: 18 px. */
val ProtoCardCorner = 9.dp

