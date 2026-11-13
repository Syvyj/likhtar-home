package com.likhtar.home.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import java.util.WeakHashMap

/**
 * Один рух прокрутки на весь лаунчер — м'яка пружина без відскоку, як на Apple TV: ряд і
 * колонка під'їжджають і гальмують, а не стрибають. Досі колонки рядів (Кінозал-сторінки,
 * Застосунки, Пошук) ставали на місце миттєво, а ряди їхали типовою, різкішою пружиною.
 */
val LikhtarGlide: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * Той самий spec, але з рухом [LikhtarGlide]. `scrollAnimationSpec` у Compose 1.12 позначено
 * застарілим, проте ContentInViewNode його досі читає.
 */
// ponytail: застаріле API; коли Compose перестане його читати — рух лишиться типовим, не зламається.
@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class)
fun BringIntoViewSpec.gliding(): BringIntoViewSpec {
    val base = this
    return object : BringIntoViewSpec {
        override val scrollAnimationSpec: AnimationSpec<Float> get() = LikhtarGlide
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) =
            base.calculateScrollDistance(offset, size, containerSize)
    }
}

private class Glide {
    var velocity = 0f
    var at = 0L
}

// Головний потік: Compose скролить лише з нього, синхронізація не потрібна.
private val glides = WeakHashMap<ScrollableState, Glide>()

/**
 * Прокрутити на [delta] рухом [LikhtarGlide]. Новий виклик перебиває попередній (не стає в
 * чергу), а швидкість переймає: утримана стрілка веде колонку одним плавним рухом, а не
 * серією «розгін — стоп».
 */
suspend fun ScrollableState.glideBy(delta: Float) {
    if (delta == 0f) return
    val glide = glides.getOrPut(this) { Glide() }
    val now = android.os.SystemClock.uptimeMillis()
    val startVelocity = if (now - glide.at < CARRY_MS) glide.velocity else 0f
    var done = 0f
    scroll {
        animate(0f, delta, startVelocity, LikhtarGlide) { value, velocity ->
            done += scrollBy(value - done)
            glide.velocity = velocity
            glide.at = android.os.SystemClock.uptimeMillis()
        }
    }
    glide.velocity = 0f
}

/** Скільки після перерваного руху його швидкість ще вважається поточною. */
private const val CARRY_MS = 120L
