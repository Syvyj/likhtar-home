package com.likhtar.home.core.catalog.media

/** Недодивлене на сервері: запис, де зупинились, скільки триває й коли дивились (0 — невідомо). */
internal data class ServerResume(val itemId: String, val positionMs: Long, val runtimeMs: Long, val playedAt: Long)

/**
 * Медіасервер зі своєю бібліотекою й історією переглядів (Jellyfin, Plex). На відміну від
 * DLNA чи SMB, сервер сам знає TMDB id кожного запису й пам'ятає, де людина зупинилась, —
 * лаунчер лише читає це й повертає назад.
 *
 * [LocalFile.path] — id запису на сервері.
 */
internal interface MediaServer {
    /** Усі фільми й серії — файлами медіатеки з TMDB id. Невдача — виняток із текстом. */
    fun walk(source: MediaSource): List<LocalFile>

    /** Пряме посилання на оригінальний файл, без перекодування. */
    fun streamUrl(source: MediaSource, file: LocalFile): String

    /** Недодивлене — те, що сервер сам показує в «Продовжити». */
    fun resume(source: MediaSource): List<ServerResume>

    /** Де зупинились, мс; `null` — сервер не відповів. */
    fun position(source: MediaSource, itemId: String): Long?

    /** Плеєр закрився — позиція на сервер. */
    fun reportStopped(source: MediaSource, itemId: String, positionMs: Long)

    /** Позначити переглянутим або зняти позначку. */
    fun setPlayed(source: MediaSource, itemId: String, played: Boolean)

    companion object {
        /** Адреса як людина її набрала: «192.168.0.5:8096» → «http://192.168.0.5:8096». */
        fun normalize(address: String): String {
            val a = address.trim().trimEnd('/')
            return if (a.startsWith("http://") || a.startsWith("https://")) a else "http://$a"
        }
    }
}
