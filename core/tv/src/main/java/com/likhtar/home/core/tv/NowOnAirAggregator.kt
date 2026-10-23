package com.likhtar.home.core.tv

import javax.inject.Inject
import javax.inject.Singleton

/**
 * «Що зараз в ефірі» для списку карток: MEGOGO — основне джерело, публічний webapi
 * Sweet.TV добирає канали без MEGOGO-відповідника. Ключ — url картки: він унікальний,
 * на відміну від epgId, якого в sweet-каналів може не бути взагалі.
 *
 * Використовують ТБ Ефір і банер Головної — логіка одна, щоб джерела не розповзались.
 */
@Singleton
class NowOnAirAggregator @Inject constructor(
    private val epg: EpgRepository,
    private val sweet: SweetTvEpgRepository,
) {
    suspend fun byUrl(links: List<ChannelLink>): Map<String, ChannelSchedule> {
        val fromMegogo = epg.schedules(links.map { it.epgId })
        val sweetIds = links
            .filter { fromMegogo[it.epgId] == null }
            .mapNotNull { it.url.substringAfter(SWEET_PREFIX, "").toLongOrNull() }
        val fromSweet = sweet.nowOnAir(sweetIds)
        return links.mapNotNull { link ->
            val programme = fromMegogo[link.epgId]
                ?: link.url.substringAfter(SWEET_PREFIX, "").toLongOrNull()?.let(fromSweet::get)
            programme?.let { link.url to it }
        }.toMap()
    }

    private companion object {
        const val SWEET_PREFIX = "sweettv://channel/"
    }
}
