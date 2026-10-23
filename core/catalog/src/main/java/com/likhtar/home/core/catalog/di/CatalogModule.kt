package com.likhtar.home.core.catalog.di

import com.likhtar.home.core.catalog.CatalogRepository
import com.likhtar.home.core.catalog.ExternalPlayer
import com.likhtar.home.core.catalog.TitleSearch
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogModule {

    @Binds
    abstract fun bindTitleSearch(impl: CatalogRepository): TitleSearch

    /**
     * Оголошує набір зовнішніх плеєрів порожнім за замовчуванням, щоб каталог збирався
     * без жодного `@IntoSet`. Наповнює його збірка: personal додає свої, play — нічого.
     */
    @Multibinds
    abstract fun externalPlayers(): Set<ExternalPlayer>
}
