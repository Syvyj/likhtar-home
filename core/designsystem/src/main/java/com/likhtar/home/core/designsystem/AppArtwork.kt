package com.likhtar.home.core.designsystem

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Готова графіка застосунку для плитки.
 *
 * [banner] розрізняє два способи малювати: TV-банер вписується в плитку цілком, а квадратна
 * іконка лягає в центр на кольорову підкладку — розтягувати її на 16:9 не можна.
 */
data class AppArtwork(
    val image: ImageBitmap,
    val banner: Boolean,
    /** Домінантний колір іконки, притемнений під підкладку. Для банера не використовується. */
    val backdrop: Color,
    /** Власний розмір у пікселях — щоб не збільшувати дрібну іконку понад міру. */
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * Іконка застосунку за правилами замовника.
 *
 * Порядок джерел: банер активності → банер застосунку → іконка активності. Саме `loadBanner`
 * і `loadIcon`, а не сирі id ресурсів: система сама розбирається з щільністю, темою й
 * підмінами, а `android.resource://` віддавав би те, що лежить у APK, повз усе це.
 *
 * Ключ кешу — пакет плюс версія: після оновлення застосунку іконка може змінитися, а на
 * `PACKAGE_REPLACED` список і так перебудовується.
 */
@Composable
fun rememberAppArtwork(packageName: String, activityName: String): AppArtwork? {
    val context = LocalContext.current
    var artwork by remember(packageName, activityName) { mutableStateOf(ArtworkCache.get(packageName)) }

    LaunchedEffect(packageName, activityName) {
        if (artwork != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching { loadArtwork(context, packageName, activityName) }.getOrNull()
        } ?: return@LaunchedEffect
        ArtworkCache.put(packageName, loaded)
        artwork = loaded
    }

    return artwork
}

private fun loadArtwork(context: Context, packageName: String, activityName: String): AppArtwork? {
    val pm = context.packageManager
    val component = android.content.ComponentName(packageName, activityName)
    val activity = runCatching { pm.getActivityInfo(component, 0) }.getOrNull()

    val banner = activity?.loadBanner(pm) ?: runCatching {
        pm.getApplicationInfo(packageName, 0).loadBanner(pm)
    }.getOrNull()

    if (banner != null) {
        val bitmap = banner.toBitmap() ?: return null
        return AppArtwork(
            image = bitmap.asImageBitmap(),
            banner = true,
            backdrop = Color.Transparent,
            widthPx = bitmap.width,
            heightPx = bitmap.height,
        )
    }

    val icon = activity?.loadIcon(pm) ?: runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
    val bitmap = icon?.toBitmap() ?: return null
    return AppArtwork(
        image = bitmap.asImageBitmap(),
        banner = false,
        backdrop = dominantBackdrop(bitmap),
        widthPx = bitmap.width,
        heightPx = bitmap.height,
    )
}

/**
 * Drawable → bitmap зі збереженням прозорості (ARGB_8888, без заливки білим).
 *
 * Adaptive-іконку не малюємо сирою: її полотно — 108 умовних одиниць, з яких видно
 * центральні 72, тому решта — навмисний «бліде» поле під маску системи. Малюємо її
 * збільшеною в 1.5× зі зсувом, тобто рівно safe zone 66/108, а форму задає вже
 * скруглення плитки.
 */
private fun Drawable.toBitmap(): Bitmap? {
    val target = ICON_TARGET_PX
    val adaptive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this is AdaptiveIconDrawable

    if (!adaptive && this is BitmapDrawable) {
        val source = bitmap ?: return null
        return if (source.config == Bitmap.Config.ARGB_8888) source else source.copy(Bitmap.Config.ARGB_8888, false)
    }

    val width = if (intrinsicWidth > 0) minOf(intrinsicWidth, target) else target
    val height = if (intrinsicHeight > 0) minOf(intrinsicHeight, target) else target
    if (width <= 0 || height <= 0) return null

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (adaptive) {
        val scale = ADAPTIVE_SCALE
        val inset = (scale - 1f) / 2f
        setBounds(
            (-width * inset).toInt(),
            (-height * inset).toInt(),
            (width * (1f + inset)).toInt(),
            (height * (1f + inset)).toInt(),
        )
    } else {
        setBounds(0, 0, width, height)
    }
    draw(canvas)
    return bitmap
}

/**
 * Домінантний колір іконки, притемнений під підкладку.
 *
 * Palette з androidx сюди не тягнемо: заради одного кольору це ціла бібліотека, а середній
 * колір непрозорих пікселів дає той самий результат — фон, що явно належить цій іконці.
 * Прозорі пікселі пропускаємо, інакше все зводиться до сірого.
 */
private fun dominantBackdrop(bitmap: Bitmap): Color {
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0
    val stepX = (bitmap.width / SAMPLE_SIDE).coerceAtLeast(1)
    val stepY = (bitmap.height / SAMPLE_SIDE).coerceAtLeast(1)

    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if ((pixel ushr 24) > 128) {
                r += (pixel shr 16) and 0xFF
                g += (pixel shr 8) and 0xFF
                b += pixel and 0xFF
                count++
            }
            x += stepX
        }
        y += stepY
    }

    if (count == 0) return FallbackBackdrop
    val average = Color(r / count / 255f, g / count / 255f, b / count / 255f)
    // Притемнення до ~20 % світлоти: підкладка має підказувати колір іконки, а не сперечатися з нею.
    return lerp(average, Color.Black, 0.78f)
}

private val FallbackBackdrop = Color.White.copy(alpha = 0.07f)

/** Стільки пікселів вистачає плитці на 4K; більше — марна пам'ять. */
private const val ICON_TARGET_PX = 192
private const val ADAPTIVE_SCALE = 1.5f
private const val SAMPLE_SIDE = 24

private val ArtworkCache = android.util.LruCache<String, AppArtwork>(96)
