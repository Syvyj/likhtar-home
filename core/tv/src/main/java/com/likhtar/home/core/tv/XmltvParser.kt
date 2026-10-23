package com.likhtar.home.core.tv

import java.io.InputStream
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/** Що вдалося витягти з XMLTV: назви каналів (для зіставлення) і самі передачі. */
data class XmltvGuide(
    val channelNames: Map<String, List<String>> = emptyMap(),
    val programmes: List<TvProgramme> = emptyList(),
)

/**
 * Розбір XMLTV — формату, у якому телепрограму віддають усі IPTV/OTT-провайдери.
 *
 * SAX, а не XmlPullParser і не DOM: DOM тримає весь документ у пам'яті (файл провайдера
 * на кількасот каналів — це десятки мегабайтів), а `XmlPullParser` в android.jar — заглушка,
 * тож розбір неможливо було б перевірити юніт-тестом. SAX є і в JDK, і в Android.
 *
 * [keepChannelIds] обмежує результат каналами, які нас цікавлять: у спільнотних файлах
 * бувають десятки тисяч каналів, а показуємо ми кількасот. `null` — залишити все.
 * [keepChannelNames] — те саме за назвою (уже через [normalizeChannelName]): id у чужих
 * файлах рідко збігаються з нашими, і `programmesFor` шукає ще й за назвою, тож передачі
 * такого каналу теж лишаємо. Назви беруться з `<channel>`, які за DTD XMLTV стоять перед
 * усіма `<programme>`.
 * [horizonMillis] відкидає передачі далі за вказаний горизонт: сітка все одно показує добу.
 */
class XmltvParser(
    private val keepChannelIds: Set<String>? = null,
    private val keepChannelNames: Set<String>? = null,
    private val horizonMillis: Long = DEFAULT_HORIZON_MS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun parse(input: InputStream): XmltvGuide {
        // Провайдери майже завжди віддають .xml.gz, але не завжди позначають це заголовком,
        // тому дивимося на самі байти, а не на Content-Type.
        val stream = input.buffered()
        stream.mark(2)
        val magic = ByteArray(2)
        val read = stream.read(magic)
        stream.reset()
        val body = if (read == 2 && magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte()) {
            GZIPInputStream(stream)
        } else {
            stream
        }

        val handler = Handler(keepChannelIds, keepChannelNames, nowMillis() - PAST_WINDOW_MS, nowMillis() + horizonMillis)
        SAXParserFactory.newInstance().newSAXParser().parse(body, handler)
        return XmltvGuide(handler.channelNames, handler.programmes.sortedBy { it.startMillis })
    }

    private class Handler(
        private val keep: Set<String>?,
        private val keepNames: Set<String>?,
        private val from: Long,
        private val until: Long,
    ) : DefaultHandler() {

        val channelNames = mutableMapOf<String, MutableList<String>>()
        val programmes = mutableListOf<TvProgramme>()

        /** Канали файлу, що збіглися з [keepNames] за назвою, — їхні передачі теж беремо. */
        private val kept = mutableSetOf<String>()
        private val filtered = keep != null || keepNames != null

        private var channelId: String? = null
        private var programme: Pending? = null
        private var text: StringBuilder? = null

        override fun startElement(uri: String?, local: String?, name: String?, attrs: Attributes?) {
            when (name) {
                "channel" -> channelId = attrs?.getValue("id")

                "programme" -> {
                    val id = attrs?.getValue("channel") ?: return
                    if (filtered && id !in kept && keep?.contains(id) != true) return
                    val start = parseXmltvTime(attrs.getValue("start")) ?: return
                    val stop = parseXmltvTime(attrs.getValue("stop")) ?: (start + DEFAULT_LENGTH_MS)
                    if (stop < from || start > until) return
                    programme = Pending(id, start, stop)
                }

                "display-name" -> if (channelId != null) text = StringBuilder()
                "title" -> if (programme != null) text = StringBuilder()
                "desc" -> if (programme != null) text = StringBuilder()
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            text?.appendRange(ch ?: return, start, start + length)
        }

        override fun endElement(uri: String?, local: String?, name: String?) {
            when (name) {
                "channel" -> {
                    val id = channelId
                    // За id канал пропускає вже [keep]; тут — лише ті, що збіглися за назвою.
                    val names = keepNames
                    if (id != null && names != null &&
                        (normalizeChannelName(id) in names || channelNames[id].orEmpty().any { normalizeChannelName(it) in names })
                    ) {
                        kept += id
                    }
                    channelId = null
                }

                "display-name" -> {
                    val id = channelId
                    val value = text?.toString()?.trim()
                    if (id != null && !value.isNullOrEmpty()) {
                        channelNames.getOrPut(id) { mutableListOf() }.add(value)
                    }
                    text = null
                }

                "title" -> {
                    programme?.title = text?.toString()?.trim().orEmpty()
                    text = null
                }

                "desc" -> {
                    programme?.description = text?.toString()?.trim()
                    text = null
                }

                "programme" -> {
                    programme?.takeIf { it.title.isNotEmpty() }?.let {
                        programmes += TvProgramme(it.channelId, it.title, it.start, it.stop, it.description)
                    }
                    programme = null
                    text = null
                }
            }
        }

        private class Pending(val channelId: String, val start: Long, val stop: Long) {
            var title: String = ""
            var description: String? = null
        }
    }

    companion object {
        private const val DEFAULT_HORIZON_MS = 36L * 60 * 60 * 1000
        private const val PAST_WINDOW_MS = 3L * 60 * 60 * 1000
        private const val DEFAULT_LENGTH_MS = 30L * 60 * 1000

        /**
         * Час XMLTV: `20260812183000 +0300`, зона може бути відсутня.
         *
         * Розбираємо вручну, а не `SimpleDateFormat`: у реальних файлах трапляються і
         * `20260812183000`, і `20260812183000 +0300`, і `20260812183000+0300`, а кожен
         * невдалий формат у `SimpleDateFormat` — це виняток на кожній із десятків тисяч передач.
         */
        fun parseXmltvTime(value: String?): Long? {
            val raw = value?.trim() ?: return null
            if (raw.length < 14) return null
            val digits = raw.take(14)
            if (!digits.all { it.isDigit() }) return null

            val zonePart = raw.drop(14).trim()
            val zone = when {
                zonePart.length >= 5 && (zonePart[0] == '+' || zonePart[0] == '-') ->
                    TimeZone.getTimeZone("GMT$zonePart")

                else -> TimeZone.getTimeZone("UTC")
            }

            return GregorianCalendar(zone).apply {
                clear()
                set(
                    digits.substring(0, 4).toInt(),
                    digits.substring(4, 6).toInt() - 1,
                    digits.substring(6, 8).toInt(),
                    digits.substring(8, 10).toInt(),
                    digits.substring(10, 12).toInt(),
                    digits.substring(12, 14).toInt(),
                )
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
}

/**
 * Зіставлення телепрограми з каналами: спершу за id, і лише для тих, що не збіглися, — за
 * канонізованою назвою.
 *
 * Запасний шлях потрібен тому, що id у чужих файлах майже ніколи не збігаються: провайдер
 * пише `1plus1`, iptv-org — `1Plus1Ukraina.ua@SD`, а XMLTV — щось третє. Назви ж збігаються
 * майже завжди, і `normalizeChannelName` доводить їх до спільного вигляду разом з абетками
 * («ICTV» латиницею й «IСТV» кирилицею — один канал).
 *
 * [channels] — пари «id каналу → його назва». Так сюди підходять і канали каталогу iptv-org,
 * і рядки плейлиста M3U, не заводячи третього типу для того самого.
 */
fun XmltvGuide.programmesFor(channels: List<Pair<String, String>>): Map<String, List<TvProgramme>> {
    val byId = channels.associate { (id, _) -> id to id }
    val byName = channels.associate { (id, name) -> normalizeChannelName(name) to id }

    // Один прохід по каналах XMLTV: чий це наш канал. Далі передачі просто перекладаються
    // на наші id — інакше на файлі провайдера в кількадесят мегабайтів пошук ішов би
    // для кожної передачі окремо.
    val alias = mutableMapOf<String, String>()
    channelNames.forEach { (xmltvId, names) ->
        val target = byId[xmltvId]
            ?: names.firstNotNullOfOrNull { byName[normalizeChannelName(it)] }
            ?: byName[normalizeChannelName(xmltvId)]
        if (target != null) alias[xmltvId] = target
    }

    return programmes
        .mapNotNull { programme ->
            val channelId = alias[programme.channelId] ?: byId[programme.channelId] ?: return@mapNotNull null
            programme.copy(channelId = channelId)
        }
        .groupBy { it.channelId }
        .mapValues { (_, list) -> list.sortedBy { it.startMillis } }
}
