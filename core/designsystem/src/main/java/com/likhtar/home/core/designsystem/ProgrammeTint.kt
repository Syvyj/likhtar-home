package com.likhtar.home.core.designsystem

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Tint передачі з її кадру: середній колір мініатюри, притемнений до діапазону tint'ів
 * прототипу (#1c3b46…#3d3018). Рахується один раз на URL, кеш LRU.
 */
@Composable
fun rememberProgrammeTint(imageUrl: String?): Color {
    val context = LocalContext.current
    var tint by remember { mutableStateOf(GlassTintFallback) }
    LaunchedEffect(imageUrl) {
        if (imageUrl == null) {
            tint = GlassTintFallback
            return@LaunchedEffect
        }
        TintCache[imageUrl]?.let { cached ->
            tint = cached
            return@LaunchedEffect
        }
        delay(BACKDROP_SETTLE_MS)
        val computed = withContext(Dispatchers.IO) {
            runCatching {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(imageUrl).size(Size(32, 32)).allowHardware(false).build(),
                )
                (result.image as? coil3.BitmapImage)?.bitmap?.let(::averageTint)
            }.getOrNull()
        } ?: GlassTintFallback
        TintCache[imageUrl] = computed
        tint = computed
    }
    return tint
}

private fun averageTint(bitmap: Bitmap): Color {
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0
    val stepX = (bitmap.width / 16).coerceAtLeast(1)
    val stepY = (bitmap.height / 16).coerceAtLeast(1)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            r += (pixel shr 16) and 0xFF
            g += (pixel shr 8) and 0xFF
            b += pixel and 0xFF
            count++
            x += stepX
        }
        y += stepY
    }
    if (count == 0) return GlassTintFallback
    // Притемнюємо лише трохи: підкладка має нести колір кадру, а не ставати чорною.
    return lerp(
        Color(r / count / 255f, g / count / 255f, b / count / 255f),
        Color.Black,
        0.3f,
    )
}

private val TintCache = object : LinkedHashMap<String, Color>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?) = size > 128
}

private val GlassTintFallback = LikhtarColors.TintFallback

/** Кадр щойно змінився — чекаємо, поки фокус осяде, і лише тоді рахуємо колір. */
private const val BACKDROP_SETTLE_MS = 180L
