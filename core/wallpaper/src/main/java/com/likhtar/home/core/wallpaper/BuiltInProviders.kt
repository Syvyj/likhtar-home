package com.likhtar.home.core.wallpaper

import com.likhtar.home.core.data.text
import com.likhtar.home.core.data.http
import android.util.Log
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.likhtar.home.core.model.BackdropBlob
import com.likhtar.home.core.model.BackdropSpec
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Поточний вигляд лаунчера: без зображень, лише темний градієнт. Типове джерело. */
@Singleton
class PlainWallpaperProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : WallpaperProvider {
    override val id = "plain"
    override val name: String get() = context.getString(R.string.wallpaper_provider_plain)
    override val updateMode = WallpaperUpdateMode.STATIC

    override suspend fun wallpapers(): List<Wallpaper> = emptyList()
}

/**
 * Вбудований набір: чотири процедурні фони, описані в коді (градієнт + світлові плями).
 *
 * Це не заповнювач до «справжніх» шпалер, а робочий стан для приставки без мережі —
 * і єдиний, який гарантовано не псує контраст інтерфейсу.
 */
@Singleton
class BundledWallpaperProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : WallpaperProvider {
    override val id = "bundled"
    override val name: String get() = context.getString(R.string.wallpaper_provider_bundled)
    override val updateMode = WallpaperUpdateMode.TIME_ELAPSED

    override suspend fun wallpapers(): List<Wallpaper> = listOf(
        bundledImage("tent-rainy-lake", "Намет біля озера"),
        bundledImage("norway-fjord", "Норвезький фіорд"),
        bundledImage("northern-whales", "Північне море — кити"),
        bundledImage("sailing-cabin", "Каюта в морі"),
        bundledImage("alpine-lake", "Альпійське озеро"),
        bundledImage("forest-stream", "Лісовий струмок"),
        bundledImage("amsterdam-morning", "Амстердам — ранок"),
        bundledImage("amsterdam-blue-hour", "Амстердам — синя година"),
        bundledImage("oslo-day", "Осло — набережна"),
        bundledImage("oslo-evening", "Осло — вечір"),
        bundledImage("copenhagen-morning", "Копенгаген — ранок"),
        bundledImage("copenhagen-rain", "Копенгаген — дощ"),
        bundledImage("stockholm-morning", "Стокгольм — ранок"),
        bundledImage("stockholm-archipelago", "Стокгольм — архіпелаг"),
        Wallpaper(
            id = "bundled-aurora",
            title = "Північне сяйво",
            backdrop = BackdropSpec(
                baseGradient = listOf(0xFF06202A, 0xFF08181F, 0xFF27231E),
                blobs = listOf(
                    BackdropBlob(0xFF19C9DE, centerX = 0.22f, centerY = 0.18f, radius = 0.95f, alpha = 0.34f),
                    BackdropBlob(0xFF1D8F6B, centerX = 0.78f, centerY = 0.42f, radius = 0.8f, alpha = 0.24f),
                    BackdropBlob(0xFF0E7490, centerX = 0.55f, centerY = 0.9f, radius = 0.7f, alpha = 0.18f),
                ),
            ),
        ),
        Wallpaper(
            id = "bundled-dusk",
            title = "Вечірній туман",
            backdrop = BackdropSpec(
                baseGradient = listOf(0xFF151B26, 0xFF11151C, 0xFF27231E),
                blobs = listOf(
                    BackdropBlob(0xFF7C6BB8, centerX = 0.75f, centerY = 0.2f, radius = 0.9f, alpha = 0.26f),
                    BackdropBlob(0xFF2C5A8F, centerX = 0.2f, centerY = 0.55f, radius = 0.85f, alpha = 0.22f),
                    BackdropBlob(0xFFE0A14B, centerX = 0.9f, centerY = 0.85f, radius = 0.5f, alpha = 0.12f),
                ),
            ),
        ),
        Wallpaper(
            id = "bundled-depth",
            title = "Глибина",
            backdrop = BackdropSpec(
                baseGradient = listOf(0xFF0A1120, 0xFF080D16, 0xFF050708),
                blobs = listOf(
                    BackdropBlob(0xFF2563EB, centerX = 0.18f, centerY = 0.8f, radius = 1.0f, alpha = 0.28f),
                    BackdropBlob(0xFF0E7490, centerX = 0.85f, centerY = 0.22f, radius = 0.75f, alpha = 0.22f),
                ),
            ),
        ),
        Wallpaper(
            id = "bundled-ash",
            title = "Попіл",
            backdrop = BackdropSpec(
                baseGradient = listOf(0xFF17191C, 0xFF121417, 0xFF27231E),
                blobs = listOf(
                    BackdropBlob(0xFF9AA6AC, centerX = 0.3f, centerY = 0.25f, radius = 0.9f, alpha = 0.14f),
                    BackdropBlob(0xFF6B7280, centerX = 0.8f, centerY = 0.7f, radius = 0.8f, alpha = 0.16f),
                ),
            ),
        ),
    )

    /**
     * Вбудовані шпалери лежать у WebP, а не PNG: той самий набір важив 23 МБ у PNG і
     * важить 1.1 МБ у WebP-82. Для APK, який у релізі важить 3 МБ, різниця вирішальна.
     */
    private fun bundledImage(id: String, title: String) = Wallpaper(
        id = "likhtar-$id",
        title = title,
        imageUri = "file:///android_asset/wallpapers/$id.webp",
    )
}

/**
 * Віддалений набір за JSON-маніфестом.
 *
 * Свідомо **не** беремо неофіційний Bing-endpoint (див. юридичне зауваження в §6 плану):
 * URL задає власник збірки в налаштуваннях, і за замовчуванням його немає — тоді джерело
 * просто нічого не віддає.
 *
 * Очікуваний формат:
 * ```json
 * {"wallpapers":[{"id":"1","title":"…","url":"https://…","author":"…","source":"https://…"}]}
 * ```
 */
@Singleton
class ManifestWallpaperProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manifestUrlProvider: ManifestUrlProvider,
) : WallpaperProvider {

    override val id = "manifest"
    override val name: String get() = context.getString(R.string.wallpaper_provider_manifest)
    override val updateMode = WallpaperUpdateMode.TIME_ELAPSED

    override suspend fun wallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
        val url = manifestUrlProvider.manifestUrl()?.takeIf { it.isNotBlank() }
            ?: return@withContext emptyList()

        runCatching { fetch(url) }
            .onFailure { Log.w(TAG, "Не вдалося прочитати маніфест шпалер", it) }
            .getOrDefault(emptyList())
    }

    private fun fetch(url: String): List<Wallpaper> {
        val body = http(url, TIMEOUT_MS) { it.text() }

        val items = JSONObject(body).optJSONArray("wallpapers") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val imageUrl = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Wallpaper(
                id = item.optString("id").ifBlank { imageUrl },
                title = item.optString("title").ifBlank { "Шпалери" },
                imageUri = imageUrl,
                author = item.optString("author").takeIf { it.isNotBlank() },
                sourceUri = item.optString("source").takeIf { it.isNotBlank() },
            )
        }
    }

    private companion object {
        const val TAG = "LikhtarWallpaper"
        const val TIMEOUT_MS = 6_000
    }
}

/** Звідки брати URL маніфесту. Окремий інтерфейс, щоб провайдер не залежав від налаштувань. */
interface ManifestUrlProvider {
    suspend fun manifestUrl(): String?
}
