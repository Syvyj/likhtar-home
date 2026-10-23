package com.likhtar.home.core.wallpaper

import com.likhtar.home.core.data.text
import com.likhtar.home.core.data.http
import android.util.Log
import androidx.annotation.StringRes
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * «Фото дня Bing» — те саме джерело, що й у плагіні
 * `spocky/projectivy-plugin-wallpaper-bingwallpaper`, з якого ми брали ідею плагінів.
 *
 * Endpoint `HPImageArchive.aspx` неофіційний: Bing його не документує й може змінити або
 * закрити будь-коли. Тому провайдер мовчки повертає порожній список при будь-якій помилці —
 * лаунчер у такому разі просто показує попереднє джерело, а не порожній екран.
 *
 * Архів Bing тримає 8 останніх днів на ринок, тож ходимо по кількох ринках: набір стає
 * помітно різноманітнішим, а кадри на різних ринках здебільшого різні.
 *
 * Атрибуція обов'язкова й не декоративна: знімки належать фотографам і агенціям
 * (Getty, Alamy), рядок `copyright` від Bing — єдине, що ми маємо право показати замість них.
 */
@Singleton
class BingWallpaperProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : WallpaperProvider {

    override val id = "bing"
    override val name: String get() = context.getString(R.string.wallpaper_provider_bing)
    override val updateMode = WallpaperUpdateMode.TIME_ELAPSED

    override suspend fun wallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
        val seen = mutableSetOf<String>()
        // Ринки опитуються паралельно: чотири послідовні GET — це чотири таймаути поспіль
        // у найгіршому разі, а фото між собою ніяк не залежать.
        coroutineScope {
            MARKETS.map { market ->
                async {
                    runCatching { fetchMarket(market) }
                        .onFailure { Log.w(TAG, "Bing не відповів для ринку $market", it) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }.filter { seen.add(it.id) }
    }

    private fun fetchMarket(market: String): List<Wallpaper> {
        val body = httpGet("$ARCHIVE?format=js&idx=0&n=8&mkt=$market")
        val images = JSONObject(body).optJSONArray("images") ?: return emptyList()

        return (0 until images.length()).mapNotNull { index ->
            val image = images.optJSONObject(index) ?: return@mapNotNull null
            val base = image.optString("urlbase").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val copyright = image.optString("copyright")

            Wallpaper(
                id = base.substringAfterLast("id=").ifBlank { base },
                // `title` у відповіді часто порожній, а перед дужкою в copyright стоїть
                // саме назва місця — беремо її, а автора лишаємо в атрибуції.
                title = copyright.substringBefore(" (©").ifBlank { "Фото дня" },
                imageUri = "$HOST$base${"_UHD.jpg"}",
                author = copyright.substringAfter("(©", "").substringBefore(")").trim()
                    .takeIf { it.isNotBlank() }?.let { "© $it" },
                sourceUri = image.optString("copyrightlink").takeIf { it.startsWith("http") },
            )
        }
    }

    private companion object {
        const val TAG = "LikhtarWallpaper"
        const val HOST = "https://www.bing.com"
        const val ARCHIVE = "$HOST/HPImageArchive.aspx"
        val MARKETS = listOf("en-US", "en-GB", "de-DE", "ja-JP")
    }
}

/**
 * Тема набору шпалер. `MIX` — усе разом, перемішане.
 * [title] — лише українською; для UI — `stringResource(titleRes)`.
 */
enum class WallpaperTheme(val id: String, val title: String, @StringRes val titleRes: Int, val query: String?) {
    NATURE("nature", "Природа", R.string.wallpaper_theme_nature, "landscape +nature photography"),
    ANIMALS("animals", "Тварини", R.string.wallpaper_theme_animals, "+animals +wildlife photography"),
    CITIES("cities", "Міста", R.string.wallpaper_theme_cities, "+cityscape +city photography"),
    SPACE("space", "Космос", R.string.wallpaper_theme_space, "+space +nebula"),
    MIX("mix", "Мікс", R.string.wallpaper_theme_mix, null),
    ;

    companion object {
        fun fromId(id: String?): WallpaperTheme = entries.firstOrNull { it.id == id } ?: MIX
    }
}

/** Звідки провайдер бере обрану тему. Окремий інтерфейс — щоб не залежати від налаштувань. */
interface WallpaperThemeProvider {
    suspend fun theme(): WallpaperTheme
}

/**
 * Тематичні шпалери з wallhaven.cc — публічний API, ключ потрібен лише для NSFW і
 * персональних налаштувань, а нам вистачає анонімного пошуку.
 *
 * Чому не Unsplash чи Pexels, у яких якість вища: обидва вимагають реєстрації застосунку
 * й ключа. Ключ у відкритому лаунчері — це або чужа квота, яку швидко вичерпають, або
 * вимога до користувача завести акаунт заради шпалер. Коли ключ буде — джерело додається
 * одним `@Binds @IntoSet`, решта коду не змінюється.
 *
 * `sorting=relevance`, а не `toplist`: за популярністю в темі «Тварини» першими йдуть
 * фентезійні ілюстрації, які до тварин не мають стосунку. `categories=100` лишає лише
 * розділ General (без Anime та People), `purity=100` — лише SFW.
 */
@Singleton
class ThemedWallpaperProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themeProvider: WallpaperThemeProvider,
) : WallpaperProvider {

    override val id = "themed"
    override val name: String get() = context.getString(R.string.wallpaper_provider_themed)
    override val updateMode = WallpaperUpdateMode.TIME_ELAPSED

    override suspend fun wallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
        val theme = themeProvider.theme()
        val themes = if (theme == WallpaperTheme.MIX) {
            WallpaperTheme.entries.filter { it.query != null }
        } else {
            listOf(theme)
        }

        // Теми опитуються паралельно — див. коментар у BingWallpaperProvider.
        coroutineScope {
            themes.map { current ->
                async {
                    runCatching { search(current) }
                        .onFailure { Log.w(TAG, "wallhaven не відповів для теми ${current.id}", it) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }.shuffled()
    }

    private fun search(theme: WallpaperTheme): List<Wallpaper> {
        val query = URLEncoder.encode(theme.query.orEmpty(), "UTF-8")
        val body = httpGet(
            "$API?q=$query&categories=100&purity=100&ratios=16x9" +
                "&atleast=1920x1080&sorting=relevance",
        )
        val items = JSONObject(body).optJSONArray("data") ?: return emptyList()

        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            // Оригінали тут бувають 8K PNG на 30 МБ — для приставки це і трафік, і пам'ять.
            if (item.optString("file_type") != "image/jpeg") return@mapNotNull null
            if (item.optLong("file_size") > MAX_FILE_BYTES) return@mapNotNull null
            val path = item.optString("path").takeIf { it.isNotBlank() } ?: return@mapNotNull null

            Wallpaper(
                id = "wh-${item.optString("id")}",
                title = theme.title,
                imageUri = path,
                author = "wallhaven",
                sourceUri = item.optString("short_url").takeIf { it.startsWith("http") },
            )
        }
    }

    private companion object {
        const val TAG = "LikhtarWallpaper"
        const val API = "https://wallhaven.cc/api/v1/search"
        const val MAX_FILE_BYTES = 8L * 1024 * 1024
    }
}

/**
 * Спільний GET для джерел шпалер.
 *
 * User-Agent проставляємо явно: wallhaven відповідає 403 на запити без нього, а Bing
 * без нього іноді віддає інший формат.
 */
internal fun httpGet(url: String): String =
    http(url, TIMEOUT_MS, headers = mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json")) { it.text() }

private const val TIMEOUT_MS = 8_000
private const val USER_AGENT = "LIKHTAR-TV-launcher/0.1 (Android TV)"
