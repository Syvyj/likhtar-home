package com.likhtar.home.core.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Сторінка, яку гортають стрілками пульта.
 *
 * `verticalScroll` на TV рухається лише за фокусом усередині себе; сторінці без
 * фокусованих вузлів рухати нічого — тому корінь тут фокусований сам і обробляє ↑/↓,
 * гортаючи на [step]. Back та решта клавіш проходять далі: їх чекає навігація, а
 * OK екран за потреби ловить власним `onPreviewKeyEvent` перед цим модифікатором.
 */
@Composable
fun Modifier.dpadScrollable(scrollState: ScrollState, step: Dp = 240.dp): Modifier {
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { step.toPx().toInt() }
    return this
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionDown -> {
                    scope.launch {
                        scrollState.animateScrollTo(
                            (scrollState.value + stepPx).coerceAtMost(scrollState.maxValue),
                        )
                    }
                    true
                }
                Key.DirectionUp -> {
                    scope.launch {
                        scrollState.animateScrollTo((scrollState.value - stepPx).coerceAtLeast(0))
                    }
                    true
                }
                else -> false
            }
        }
        .verticalScroll(scrollState)
}
