package com.likhtar.home.core.tv

/**
 * Архів каналу провайдера (`catchup-*` у плейлисті): тип, глибина й шаблон адреси.
 *
 * Адресу передачі в архіві будує плеєр («Ліхтар ТБ», `TvChannels.timeshiftUrl/archiveUrl`) — лаунчер
 * лише передає йому ці три поля у файлі програми. Своя збірка адрес тут була мертвим кодом, що вже
 * розійшовся з плеєром, тож її прибрано; лишилось одне спільне правило — [of]:
 * архів є лише там, де плеєр знає, як його відкрити.
 */
data class ChannelArchive(val type: String, val days: Int, val source: String) {

    companion object {
        /**
         * З атрибутів плейлиста для потоку [liveUrl]; `null` — архіву немає чи плеєр не збудує адресу.
         * Те саме правило, що `TvChannels.hasArchiveAt` у плеєрі: шаблон `catchup-source`; `shift` /
         * `default`; Flussonic (`flussonic*`, `fs`), коли потік — `mpegts`, `*.ts` чи `*.m3u8`.
         * Інакше (`append`, `xc` без шаблону) жовта й ←/→ рухали смугу й нічого не робили.
         */
        fun of(type: String, days: Int, source: String, liveUrl: String = ""): ChannelArchive? {
            if (type.isBlank() && source.isBlank() || days <= 0) return null
            val playable = when {
                source.isNotBlank() -> true
                type == "shift" || type == "default" -> true
                type.startsWith("flussonic") || type == "fs" -> {
                    val last = liveUrl.substringBefore('?').substringAfterLast('/')
                    last == "mpegts" || last.endsWith(".ts") || last.endsWith(".m3u8")
                }
                else -> false
            }
            return if (playable) ChannelArchive(type, days, source) else null
        }
    }
}
