package com.likhtar.home.core.wallpaper.di

import com.likhtar.home.core.wallpaper.BingWallpaperProvider
import com.likhtar.home.core.wallpaper.BundledWallpaperProvider
import com.likhtar.home.core.wallpaper.ManifestUrlProvider
import com.likhtar.home.core.wallpaper.ManifestWallpaperProvider
import com.likhtar.home.core.wallpaper.MixWallpaperProvider
import com.likhtar.home.core.wallpaper.PlainWallpaperProvider
import com.likhtar.home.core.wallpaper.SettingsManifestUrlProvider
import com.likhtar.home.core.wallpaper.SettingsWallpaperThemeProvider
import com.likhtar.home.core.wallpaper.ThemedWallpaperProvider
import com.likhtar.home.core.wallpaper.WallpaperThemeProvider
import com.likhtar.home.core.wallpaper.WallpaperProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Реєстр джерел шпалер. Додати нове джерело — це один `@Binds @IntoSet`;
 * саме тут згодом з'явиться провайдер, що ходить до зовнішнього плагіна через AIDL.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WallpaperModule {

    @Binds
    @IntoSet
    abstract fun bindMix(impl: MixWallpaperProvider): WallpaperProvider

    @Binds
    @IntoSet
    abstract fun bindPlain(impl: PlainWallpaperProvider): WallpaperProvider

    @Binds
    @IntoSet
    abstract fun bindBundled(impl: BundledWallpaperProvider): WallpaperProvider

    @Binds
    @IntoSet
    abstract fun bindBing(impl: BingWallpaperProvider): WallpaperProvider

    @Binds
    @IntoSet
    abstract fun bindThemed(impl: ThemedWallpaperProvider): WallpaperProvider

    @Binds
    @IntoSet
    abstract fun bindManifest(impl: ManifestWallpaperProvider): WallpaperProvider

    @Binds
    abstract fun bindManifestUrlProvider(impl: SettingsManifestUrlProvider): ManifestUrlProvider

    @Binds
    abstract fun bindThemeProvider(impl: SettingsWallpaperThemeProvider): WallpaperThemeProvider
}
