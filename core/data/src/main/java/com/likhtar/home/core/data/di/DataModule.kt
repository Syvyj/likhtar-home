package com.likhtar.home.core.data.di

import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.data.OpenMeteoWeatherRepository
import com.likhtar.home.core.data.PackageManagerAppsRepository
import com.likhtar.home.core.data.RecentHistoryRepository
import com.likhtar.home.core.data.SearchHistory
import com.likhtar.home.core.data.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Єдине місце, де mock-джерела підмінюються реальними: у Фазах 2–5 тут змінюються
 * лише прив'язки, а екрани залишаються без змін.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    /** Пошук пише історію через вузький контракт — див. [SearchHistory]. */
    @Binds
    @Singleton
    abstract fun searchHistory(impl: RecentHistoryRepository): SearchHistory

    @Binds
    @Singleton
    abstract fun bindAppsRepository(impl: PackageManagerAppsRepository): AppsRepository

    /** Фаза 5: справжня погода з Open-Meteo; mock лишається як запасний варіант усередині. */
    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: OpenMeteoWeatherRepository): WeatherRepository
}
