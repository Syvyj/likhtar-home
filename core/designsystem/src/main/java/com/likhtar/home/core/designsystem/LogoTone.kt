package com.likhtar.home.core.designsystem

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Чи логотип — світлий знак на прозорому тлі (білий «Інтер+», «Discovery»): такому потрібна
 * темна плашка, на світлій він зникає. Непрозорі квадратні логотипи (ICTV, СТБ) — `false`.
 * Рахується один раз на URL за мініатюрою 32×32; доки не пораховано — `false`.
 */
@Composable
fun rememberLogoIsLight(url: String?): Boolean {
    val context = LocalContext.current
    var light by remember(url) { mutableStateOf(url?.let { LogoToneCache[it] } ?: false) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || LogoToneCache.containsKey(url)) return@LaunchedEffect
        val computed = withContext(Dispatchers.IO) {
            runCatching {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(url).size(Size(32, 32)).allowHardware(false).build(),
                )
                (result.image as? coil3.BitmapImage)?.bitmap?.let(::isLightOnTransparent)
            }.getOrNull()
        } ?: false
        LogoToneCache[url] = computed
        light = computed
    }
    return light
}

private val LogoToneCache = ConcurrentHashMap<String, Boolean>()

/** Мало непрозорого (знак, а не плашка) і воно світле. */
internal fun isLightOnTransparent(bitmap: Bitmap): Boolean {
    var opaque = 0
    var total = 0
    var luma = 0.0
    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
        val p = bitmap.getPixel(x, y)
        val a = (p ushr 24) and 0xFF
        total++
        if (a < 40) continue
        opaque++
        luma += 0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)
    }
    if (opaque == 0 || opaque > total * 0.85) return false
    return luma / opaque / 255.0 > 0.7
}
