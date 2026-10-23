package com.likhtar.home.core.catalog.media

/**
 * Що вдалося прочитати з імені файлу: назва для пошуку в TMDB, рік, сезон і серія.
 * [season]/[episode] обидва задані — це серія; інакше фільм.
 */
data class MediaName(val title: String, val year: Int = 0, val season: Int = 0, val episode: Int = 0) {
    val isEpisode: Boolean get() = episode > 0
}

/**
 * Розбір імен відеофайлів медіатеки: `Dark.Matter.S02E08.1080p.WEB-DL.mkv`,
 * `Дюна (2021) 4K.mkv`, `Сезон 1/Серія 03.mp4`.
 *
 * Назва — до першої мітки (рік, SxxEyy, якість, кодек); якщо файл серії названо лише номером,
 * назва береться з папок вище, пропускаючи «Season 1» / «Сезон 1».
 */
object MediaNames {
    private val VIDEO = setOf("mkv", "mp4", "m4v", "avi", "mov", "ts", "m2ts", "mts", "wmv", "webm", "mpg", "mpeg", "flv", "3gp", "vob")

    fun isVideo(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO

    private val SXE = Regex("""(?i)\bS(\d{1,2})[ ._-]?E(\d{1,3})\b""")
    private val NXN = Regex("""(?i)\b(\d{1,2})x(\d{2,3})\b""")
    private val EPISODE_ONLY = Regex("""(?i)\b(?:E|Ep|Episode|Серія|Серия|Епізод)[ ._-]?(\d{1,3})\b""")
    private val SEASON_DIR = Regex("""(?i)^(?:Season|Сезон|S)[ ._-]?(\d{1,2})\b.*|^(\d{1,2})[ ._-]?(?:Season|Сезон)\b.*""")
    private val YEAR = Regex("""(?:^|[\s(\[])((?:19|20)\d{2})(?:$|[\s)\]])""")
    private val TAGS = Regex(
        """(?i)\b(?:2160p|1080p|1080i|720p|576p|480p|4k|uhd|hdr10?\+?|hdr|dv|dolby[ ._-]?vision|web[ ._-]?dl|web[ ._-]?rip|webrip|bdrip|brrip|blu[ ._-]?ray|bdremux|remux|hdtv|dvdrip|hdrip|x264|x265|h[ ._]?264|h[ ._]?265|hevc|avc|aac|ac3|dts|ddp?5[ ._]1|atmos|proper|repack|extended|unrated|imax|multi|ukr|rus|eng|sub|subs)\b""",
    )

    /** [path] — шлях усередині джерела, з «/»; останній сегмент — ім'я файлу. */
    fun parse(path: String): MediaName {
        val parts = path.trim('/').split('/').filter(String::isNotBlank)
        val file = parts.lastOrNull().orEmpty().substringBeforeLast('.')
        val dirs = parts.dropLast(1)
        val name = clean(file)

        var season = 0
        var episode = 0
        var cut = name.length
        SXE.find(name)?.let { season = it.groupValues[1].toInt(); episode = it.groupValues[2].toInt(); cut = minOf(cut, it.range.first) }
            ?: NXN.find(name)?.let { season = it.groupValues[1].toInt(); episode = it.groupValues[2].toInt(); cut = minOf(cut, it.range.first) }
            ?: EPISODE_ONLY.find(name)?.let { m ->
                episode = m.groupValues[1].toInt()
                cut = minOf(cut, m.range.first)
                season = dirs.asReversed().firstNotNullOfOrNull(::seasonOf) ?: 1
            }

        val year = YEAR.find(name)?.groupValues?.get(1)?.toInt() ?: 0
        YEAR.find(name)?.let { cut = minOf(cut, it.range.first) }
        TAGS.find(name)?.let { cut = minOf(cut, it.range.first) }

        var title = tidy(name.substring(0, cut))
        // «Серія 03.mp4» у «Темна матерія/Сезон 2/» — назва в папці вище сезону.
        if (title.isEmpty() || (episode > 0 && title.all { it.isDigit() || it == ' ' })) {
            val dir = dirs.asReversed().firstOrNull { seasonOf(it) == null } ?: ""
            val parsedDir = parse(dir + ".mkv")
            title = parsedDir.title
            return MediaName(title, if (year > 0) year else parsedDir.year, season, episode)
        }
        return MediaName(title, year, season, episode)
    }

    private fun seasonOf(dir: String): Int? = SEASON_DIR.matchEntire(clean(dir))?.let { m ->
        (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()
    }

    /** Крапки й підкреслення — пробіли, крім звуку на кшталт 5.1 (одна цифра з обох боків). */
    private fun clean(s: String): String = s.replace(Regex("""\b(\d)\.(\d)\b"""), "$1§$2")
        .replace(Regex("""[._]"""), " ").replace('§', '.').replace(Regex("""\s+"""), " ").trim()

    private fun tidy(s: String): String = s.replace(Regex("""[\[(]\s*$"""), "").trim(' ', '-', '–', '(', '[', ',').replace(Regex("""\s+"""), " ")
}
