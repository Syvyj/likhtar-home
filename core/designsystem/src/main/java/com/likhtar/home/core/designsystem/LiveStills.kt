package com.likhtar.home.core.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * Живий фон: кадри тайтла пливуть (Ken Burns) і змінюють один одного.
 *
 * Замість трейлера за кадром: вбудований плеєр YouTube у WebView на деяких приставках віддає
 * лише звук — апаратний декодер не малює в сторінку. Рух тут — лише масштаб і зсув
 * шару, без перемальовування: кадр один раз декодований, далі GPU його тільки тягне.
 *
 * @param parallax −1…1 — куди зсунути кадр за фокусом (протилежно до нього): глибина без
 * гіроскопа. Кадр для цього завжди трохи збільшений, щоб зсув не відкривав край.
 */
@Composable
fun LiveStills(uris: List<String>, modifier: Modifier = Modifier, parallax: () -> Float = { 0f }) {
    val context = LocalContext.current
    var index by remember(uris) { mutableIntStateOf(0) }
    LaunchedEffect(uris) {
        while (true) {
            delay(StillHoldMs)
            val next = (index + 1) % uris.size
            // Наступний кадр — спершу в кеш, інакше перехід показав би порожнечу.
            context.imageLoader.execute(ImageRequest.Builder(context).data(uris[next]).build())
            index = next
        }
    }
    Crossfade(targetState = index, animationSpec = tween(StillFadeMs), label = "still", modifier = modifier) { i ->
        val motion = Motions[i % Motions.size]
        val t = remember(uris, i) { Animatable(0f) }
        LaunchedEffect(uris, i) {
            t.animateTo(1f, tween((StillHoldMs + StillFadeMs).toInt(), easing = LinearEasing))
        }
        AsyncImage(
            model = uris[i],
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = FaceSafeCrop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = lerp(motion.scaleFrom, motion.scaleTo, t.value)
                    scaleX = scale
                    scaleY = scale
                    translationX = size.width * (lerp(motion.panFrom, motion.panTo, t.value) + parallax() * ParallaxShift)
                },
        )
    }
}

/** Як пливе кадр: наближення, проліт ліворуч, віддалення, проліт праворуч — по черзі. */
private data class Motion(val scaleFrom: Float, val scaleTo: Float, val panFrom: Float = 0f, val panTo: Float = 0f)

private val Motions = listOf(
    Motion(1.04f, 1.10f),
    Motion(1.08f, 1.08f, panFrom = 0.025f, panTo = -0.025f),
    Motion(1.10f, 1.04f),
    Motion(1.08f, 1.08f, panFrom = -0.025f, panTo = 0.025f),
)

private const val StillHoldMs = 5_000L
private const val StillFadeMs = 1_200

/** Зсув за фокусом — частка ширини екрана; запас масштабу (≥1.04) його покриває. */
private const val ParallaxShift = 0.012f
