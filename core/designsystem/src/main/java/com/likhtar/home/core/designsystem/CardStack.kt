package com.likhtar.home.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Стос: за карткою визирають ще дві — ознака, що за нею не один тайтл, а добірка (жанр,
 * колекція, франшиза). Задні картки — вужчі й темніші, лише верхні краї
 * над передньою; місце під них — [Peek]·2 зверху, тож ряд стосів трохи вищий за звичайний.
 *
 * У фокусі стос росте разом із передньою карткою ([focusedScale] — той самий, що в неї) і
 * злегка розходиться віялом: задні підіймаються й нахиляються. Масштабуємо НЕ обгортку, а
 * кожен задній шар окремо — трансформація над вузлом фокуса ламає D-pad (↓ їде вздовж
 * ряду); передня картка росте сама, під своїм вузлом фокуса.
 */
@Composable
fun CardStack(
    tone: Color,
    modifier: Modifier = Modifier,
    corner: Dp = LikhtarDimens.CornerLarge,
    focusedScale: Float = StackFocusScale,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    var focused by remember { mutableStateOf(false) }
    val t by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "stack",
    )
    // Запас зверху — лише під спокійний стос: розкрите віяло у фокусі налазить на заголовок
    // ряду, і це свідомо; заголовок малюється
    // поверх (zIndex у рядах). Росте стос униз (StackOrigin).
    Box(modifier.padding(top = Peek * 2).onFocusChanged { focused = it.hasFocus }) {
        StackLayer(shape, lerp(tone, Color.Black, 0.45f), inset = Inset * 2, lift = Peek * 2, liftFocused = PeekFocused * 2, tilt = -2.5f, t, focusedScale)
        StackLayer(shape, lerp(tone, Color.Black, 0.22f), inset = Inset, lift = Peek, liftFocused = PeekFocused, tilt = 1.5f, t, focusedScale)
        content()
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.StackLayer(
    shape: RoundedCornerShape,
    color: Color,
    inset: Dp,
    lift: Dp,
    liftFocused: Dp,
    tilt: Float,
    t: Float,
    focusedScale: Float,
) {
    Box(
        Modifier
            .matchParentSize()
            .padding(horizontal = inset)
            .graphicsLayer {
                val s = 1f + (focusedScale - 1f) * t
                scaleX = s
                scaleY = s
                transformOrigin = StackOrigin
                translationY = -(lift + (liftFocused - lift) * t).toPx()
                rotationZ = tilt * t
            }
            .clip(shape)
            .background(color),
    )
}

/** Від верхнього краю: стос у фокусі росте вниз, як і передня картка (focusOrigin). */
val StackOrigin = TransformOrigin(0.5f, 0f)

/** Масштаб фокуса для карток у стосі: більший тягнув би картку на ряд нижче. */
const val StackFocusScale = 1.06f

private val Peek = 7.dp
private val PeekFocused = 13.dp
private val Inset = 9.dp
