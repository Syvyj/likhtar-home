package com.likhtar.home.feature.search

import com.likhtar.home.core.catalog.UaAudioRepository
import com.likhtar.home.core.catalog.UaLookup
import com.likhtar.home.core.tv.RealityRepository
import com.likhtar.home.core.tv.ShowSearch
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Вузькі залежності пошуку — щоб його ViewModel збиралась у тесті без мережі й DataStore. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {
    @Binds
    abstract fun showSearch(impl: RealityRepository): ShowSearch

    @Binds
    abstract fun uaLookup(impl: UaAudioRepository): UaLookup
}
