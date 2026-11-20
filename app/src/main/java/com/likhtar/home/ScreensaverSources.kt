package com.likhtar.home

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.CatalogRepository
import com.likhtar.home.core.catalog.SectionRowsRepository
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.wallpaper.Wallpaper
import com.likhtar.home.core.wallpaper.WallpaperProvider
import com.likhtar.home.core.wallpaper.WallpaperUpdateMode
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray

/**
 * Джерела заставки, спільні для неї самої й шпалер Головної: відео з висоти Apple
 * (у застосунку лише список посилань, assets/aerials.json) і кадри нового з моїх сервісів.
 */
@Singleton
class ScreensaverSources @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rows: SectionRowsRepository,
    private val catalog: CatalogRepository,
) {
    data class Aerial(val url: String, val name: String)
    data class Fresh(val item: CatalogItem, val brand: String)

    fun aerials(): List<Aerial> = runCatching {
        val uk = Locale.getDefault().language == "uk"
        val array = JSONArray(context.assets.open("aerials.json").bufferedReader().use { it.readText() })
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Aerial(o.getString("url"), if (uk) o.getString("uk") else o.getString("en"))
        }
    }.getOrDefault(emptyList())

    /** Перший ряд (нове) сторінок моїх сервісів — ті самі, що плитки Кінозалу. */
    suspend fun freshTitles(): List<Fresh> = runCatching {
        val brands = rows.cinemaBrands(onlyPlayable = false).take(MAX_BRANDS)
        val picked = brands.flatMap { brand ->
            rows.cinemaBrandRows(brand.key, limit = PER_BRAND).take(2)
                .flatMap { it.items.take(PER_BRAND) }
                .filter { it.backdropPath != null }
                .map { brand.title to it }
        }.distinctBy { it.second.tmdbId }
        val withLogos = catalog.attachLogos(picked.map { it.second }).associateBy { it.tmdbId }
        picked.map { (brand, item) -> Fresh(withLogos[item.tmdbId] ?: item, brand) }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_BRANDS = 5
        const val PER_BRAND = 8
    }
}

/** Шпалери «З висоти»: на Головній — нерухомий кадр відео, у «Спокої» воно грає. */
class AerialWallpaperProvider @Inject constructor(
    private val sources: ScreensaverSources,
    @ApplicationContext private val context: Context,
) : WallpaperProvider {
    override val id = "aerial"
    override val name: String get() = context.getString(R.string.wallpaper_provider_aerial)
    override val updateMode = WallpaperUpdateMode.TIME_ELAPSED

    override suspend fun wallpapers() = sources.aerials().shuffled().map {
        Wallpaper(id = it.url, title = it.name, videoUri = it.url, author = "${it.name} · Apple")
    }
}

/** Шпалери «Нове з моїх сервісів»: кадри TMDB тих самих тайтлів, що й на заставці. */
class FreshWallpaperProvider @Inject constructor(
    private val sources: ScreensaverSources,
    @ApplicationContext private val context: Context,
) : WallpaperProvider {
    override val id = "fresh"
    override val name: String get() = context.getString(R.string.wallpaper_provider_fresh)
    override val updateMode = WallpaperUpdateMode.TIME_ELAPSED

    override suspend fun wallpapers() = sources.freshTitles().shuffled().map {
        Wallpaper(
            id = "fresh-${it.item.tmdbId}",
            title = it.item.title,
            imageUri = TmdbImages.hero(it.item.backdropPath),
            author = "${it.item.title} · ${it.brand}",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScreensaverWallpaperModule {
    @Binds @IntoSet abstract fun bindAerial(impl: AerialWallpaperProvider): WallpaperProvider
    @Binds @IntoSet abstract fun bindFresh(impl: FreshWallpaperProvider): WallpaperProvider
}

/**
 * Відео з висоти тлом Головної. Поки інтерфейс на екрані — стоїть на одному кадрі (з 10-ї
 * секунди, бо початок у Apple часто темний), у «Спокої» грає по колу без звуку. Коли Ліхтар
 * іде з екрана, декодер віддаємо: він потрібен плеєру.
 */
@androidx.compose.runtime.Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
fun AerialWallpaper(uri: String, playing: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = androidx.compose.runtime.remember(uri) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri), STILL_AT_MS)
        }
    }
    androidx.compose.runtime.LaunchedEffect(player, playing) { player.playWhenReady = playing }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(player, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> player.prepare()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> player.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        factory = {
            androidx.media3.ui.PlayerView(it).apply {
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        update = { it.player = player },
    )
}

private const val STILL_AT_MS = 10_000L
