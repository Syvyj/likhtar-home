package com.likhtar.home.di

import com.likhtar.home.core.catalog.CatalogConfig
import com.likhtar.home.core.tv.LikhtarServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Звідки `:core:tv` бере адресу нашого сервера для `/api/epg`.
 *
 * Лежить у `:app`, а не в самому `:core:tv`, і не в `:core:catalog`, де адреса живе.
 * Причина в флейворах: `dist` (personal/play) оголошений у `:core:catalog`, і будь-який
 * модуль, який його підключає, мусить оголосити вимір у себе. Потягнути каталог у
 * `:core:tv` означало б рознести флейвори ще на чотири модулі фіч. `:app` уже має і те,
 * і те, тож шов зшивається тут — одним `@Provides`, без нових ребер між модулями.
 */
@Module
@InstallIn(SingletonComponent::class)
object LikhtarServerModule {

    @Provides
    @Singleton
    fun likhtarServer(config: CatalogConfig): LikhtarServer = object : LikhtarServer {
        override suspend fun baseUrl(): String? = config.streamsUrl()
    }
}
