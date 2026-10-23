package com.likhtar.home.di

import com.likhtar.home.core.tv.DefaultTvSource
import com.likhtar.home.core.tv.TvSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Джерело каналів вибирає `DefaultTvSource`: плейлист, якщо людина вказала його в
 * Налаштуваннях, інакше вбудовані прив'язки. Поки поле порожнє — а за замовчуванням воно
 * порожнє, — поведінка та сама, що була.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TvSourceModule {
    @Binds
    @Singleton
    abstract fun tvSource(source: DefaultTvSource): TvSource
}
