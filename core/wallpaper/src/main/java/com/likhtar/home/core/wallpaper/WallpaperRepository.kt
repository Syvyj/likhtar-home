package com.likhtar.home.core.wallpaper

import com.likhtar.home.core.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Те, що екран має намалювати: шпалери (може бути `null`) плюс налаштування показу. */
data class WallpaperState(
    val wallpaper: Wallpaper? = null,
    val dimPercent: Int = 30,
    val showAttribution: Boolean = true,
)

/**
 * Обирає активне джерело шпалер і крутить його набір за таймером.
 *
 * Провайдери реєструються в DI, тому додати нове джерело (зокрема зовнішній плагін) —
 * це один `@Provides`, без змін в екранах.
 */
@Singleton
class WallpaperRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val providers: Set<@JvmSuppressWildcards WallpaperProvider>,
    private val settingsRepository: SettingsRepository,
) {
    /** Порядок у налаштуваннях фіксований і осмислений; id-сортування давало випадковий. */
    fun providerNames(): List<Pair<String, String>> =
        providers.sortedBy { PROVIDER_ORDER.indexOf(it.id).takeIf { i -> i >= 0 } ?: PROVIDER_ORDER.size }
            .map { it.id to it.name }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<WallpaperState> = settingsRepository.settings
        .map {
            Config(
                providerId = it.wallpaperProviderId,
                intervalMinutes = it.wallpaperIntervalMinutes,
                dimPercent = it.wallpaperDimPercent,
                showAttribution = it.showWallpaperAttribution,
                // Тема входить у конфіг, щоб її зміна одразу перезапускала ротацію,
                // а не чекала наступного інтервалу зі старим набором.
                themeId = it.wallpaperTheme,
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { config ->
            rotate(providers.firstOrNull { it.id == config.providerId }, config) {
                com.likhtar.home.core.data.awaitOnline(context)
            }
        }

    internal data class Config(
        val providerId: String,
        val intervalMinutes: Int,
        val dimPercent: Int,
        val showAttribution: Boolean,
        val themeId: String = "mix",
    )

    internal companion object {
        private val PROVIDER_ORDER =
            listOf(MixWallpaperProvider.ID, "bing", "themed", "bundled", "manifest", "plain")

        /**
         * Ротація набору одного джерела. Виділено окремо, щоб її можна було перевірити
         * тестом на віртуальному часі, а не чеканням реальних хвилин.
         */
        internal fun rotate(
            provider: WallpaperProvider?,
            config: Config,
            /** Дочекатися мережі: порожній набір на старті — зазвичай Wi-Fi, що ще не піднявся. */
            awaitOnline: suspend () -> Unit = {},
        ): Flow<WallpaperState> = flow {
        var items = provider?.wallpapers().orEmpty()

        if (provider == null || items.isEmpty()) {
            emit(WallpaperState(null, config.dimPercent, config.showAttribution))
            if (provider == null) return@flow
            awaitOnline()
            items = provider.wallpapers()
            if (items.isEmpty()) return@flow
        }

        var index = 0
        while (true) {
            emit(WallpaperState(items[index % items.size], config.dimPercent, config.showAttribution))
            // Статичне джерело або єдиний варіант крутити нічого — виходимо з циклу,
            // інакше таймер даремно тримав би корутину активною.
            if (provider.updateMode == WallpaperUpdateMode.STATIC || items.size == 1) return@flow
            delay(config.intervalMinutes.coerceAtLeast(1) * 60_000L)
            index++
        }
        }
    }
}

/** URL маніфесту беремо з налаштувань — так провайдер лишається незалежним від сховища. */
@Singleton
class SettingsManifestUrlProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ManifestUrlProvider {
    override suspend fun manifestUrl(): String? =
        settingsRepository.settings.first().wallpaperManifestUrl
            .ifBlank { BuildConfig.DEFAULT_WALLPAPER_MANIFEST_URL }
}

/** Так само для теми: провайдер знає лише про [WallpaperTheme], не про DataStore. */
@Singleton
class SettingsWallpaperThemeProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : WallpaperThemeProvider {
    override suspend fun theme(): WallpaperTheme =
        WallpaperTheme.fromId(settingsRepository.settings.first().wallpaperTheme)
}
