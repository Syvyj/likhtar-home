package com.likhtar.home.core.catalog

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Власних потоків у збірці з сайту немає: «Дивитись» веде лише в офіційні сервіси. Той самий
 * API, що в домашній збірці, щоб сторінки тайтлів не знали, яка збірка, — і нічого не знаходить.
 */
@Singleton
class LikhtarStreams @Inject constructor() {
    enum class Readiness { READY, DEAD, UNKNOWN }

    suspend fun seasons(item: CatalogItem): List<Int> = emptyList()

    suspend fun streams(item: CatalogItem, season: Int? = null): List<StreamOption> = emptyList()

    fun ownTorrentServer(): String? = null

    suspend fun warmOnOwn(streamUrl: String): Boolean = false

    suspend fun coldTorrents(item: CatalogItem, known: Set<String>): List<StreamOption> = emptyList()

    suspend fun torrentStat(ref: String): TorrentStat? = null

    suspend fun prepare(streamUrl: String, waitSeconds: Int = 0): Readiness = Readiness.UNKNOWN

    suspend fun playlistUrl(
        title: String,
        originalTitle: String,
        year: String,
        season: Int,
        episode: Int,
        ref: String = "",
        tmdbId: Int = 0,
    ): String? = null

    suspend fun playlistEntries(url: String): List<QueueEntry> = emptyList()

    suspend fun voices(item: CatalogItem, season: Int, episode: Int): List<VoiceGroup> = emptyList()

    suspend fun playVoice(item: CatalogItem, refs: List<String>, season: Int, episode: Int, waitSeconds: Int = 0): PlayResult = PlayResult()

    suspend fun playByName(
        item: CatalogItem,
        season: Int = 0,
        episode: Int = 0,
        waitSeconds: Int = 0,
        unscripted: Boolean = false,
    ): PlayResult = PlayResult()

    suspend fun reading(streamUrl: String, waitSeconds: Int = 0): ReadingCheck = ReadingCheck()
}
