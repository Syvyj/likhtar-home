package com.likhtar.home.core.tv

/**
 * Архів каналу провайдера (`catchup-*` у плейлисті) і адреса передачі в ньому.
 *
 * Перевірено на справжньому провайдері (Flussonic): `…/mpegts?token` →
 * `…/archive-{початок}-{тривалість}.ts?token` віддає передачу цілим файлом, і плеєр мотає її,
 * як фільм.
 */
data class ChannelArchive(val type: String, val days: Int, val source: String) {

    /** Адреса передачі [startMillis]..[endMillis] для потоку [liveUrl]; `null` — схеми не знаємо. */
    fun url(liveUrl: String, startMillis: Long, endMillis: Long, nowMillis: Long = System.currentTimeMillis()): String? {
        val start = startMillis / 1000
        val end = endMillis / 1000
        val duration = (end - start).coerceAtLeast(60)
        val now = nowMillis / 1000
        if (source.isNotBlank()) {
            val filled = fill(source, start, end, duration, now)
            return when {
                filled.startsWith("http") -> filled
                // append: шаблон дописується до адреси ефіру.
                else -> liveUrl + filled
            }
        }
        return when (type) {
            "flussonic", "flussonic-ts", "fs", "flussonic-hls" -> flussonic(liveUrl, start, duration)
            // shift / default без шаблону — найпоширеніша форма: utc і lutc у запиті.
            "shift", "default" -> liveUrl + (if ('?' in liveUrl) "&" else "?") + "utc=$start&lutc=$now"
            else -> null
        }
    }

    private fun flussonic(liveUrl: String, start: Long, duration: Long): String? {
        val query = liveUrl.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
        val path = liveUrl.substringBefore('?')
        val base = path.substringBeforeLast('/')
        return when (path.substringAfterLast('/')) {
            "mpegts", "video.ts", "index.ts" -> "$base/archive-$start-$duration.ts$query"
            "index.m3u8", "video.m3u8", "mono.m3u8", "tracks-v1a1.m3u8" -> "$base/index-$start-$duration.m3u8$query"
            else -> null
        }
    }

    private fun fill(template: String, start: Long, end: Long, duration: Long, now: Long): String = template
        .replace("\${start}", "$start").replace("\${end}", "$end").replace("\${timestamp}", "$now")
        .replace("{utc}", "$start").replace("{start}", "$start").replace("{utcend}", "$end").replace("{end}", "$end")
        .replace("{lutc}", "$now").replace("{now}", "$now").replace("{timestamp}", "$now")
        .replace("{duration}", "$duration").replace("{offset}", "${now - start}")

    companion object {
        /** З атрибутів плейлиста; без типу чи без днів — архіву немає. */
        fun of(type: String, days: Int, source: String): ChannelArchive? =
            if (type.isBlank() || days <= 0) null else ChannelArchive(type, days, source)
    }
}
