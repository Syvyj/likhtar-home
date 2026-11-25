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

        /** Останній сегмент потоку Flussonic — його прямий ефір; поруч лежить архів `index-<від>-<трив>.m3u8`. */
        private val FLUSSONIC_LIVE = setOf("index.m3u8", "video.m3u8", "mono.m3u8", "mpegts")

        /**
         * Адреса хвилини архіву годину тому для потоку Flussonic [liveUrl]; `null` — потік не схожий на Flussonic.
         * Для плейлистів без `catchup-*`: провайдер архів має, але не пише —
         * інші плеєри вгадують Flussonic за адресою, ми ж питаємо сервер, чи шматок справді є.
         */
        fun flussonicProbe(liveUrl: String, nowSec: Long): String? {
            val q = liveUrl.indexOf('?')
            val path = if (q < 0) liveUrl else liveUrl.substring(0, q)
            if (!path.startsWith("http") || path.substringAfterLast('/') !in FLUSSONIC_LIVE) return null
            return path.substringBeforeLast('/') + "/index-${nowSec - 3600}-60.m3u8" + (if (q < 0) "" else liveUrl.substring(q))
        }

        /**
         * Глибина, коли плейлист її не каже: тиждень — найчастіше в провайдерів Flussonic.
         * ponytail: вгадана; старіша передача за межею просто не відкриється — питати сервер про глибину, якщо заважатиме.
         */
        const val GUESSED_DAYS = 7
    }
}
