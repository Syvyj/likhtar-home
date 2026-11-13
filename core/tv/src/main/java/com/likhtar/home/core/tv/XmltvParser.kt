package com.likhtar.home.core.tv

import java.io.InputStream
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
    /**
     * Канали з архівом (id файлу) і скільки днів назад для них тримати: минулі передачі —
     * це меню «Архів». Решті минуле не потрібне, і тримати його для тисячі каналів — зайва пам'ять.
     */
    private val archiveIds: Set<String> = emptySet(),
    private val archiveMillis: Long = 0,
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

        val handler = Handler(keepChannelIds, keepChannelNames, nowMillis() - PAST_WINDOW_MS, nowMillis() + horizonMillis, archiveIds, nowMillis() - archiveMillis)
        SAXParserFactory.newInstance().newSAXParser().parse(body, handler)
        return XmltvGuide(handler.channelNames, handler.programmes.sortedBy { it.startMillis })
    }

    private class Handler(
        private val keep: Set<String>?,
        private val keepNames: Set<String>?,
        private val from: Long,
        private val until: Long,
        private val archiveIds: Set<String>,
        private val archiveFrom: Long,
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
                    if (start > until) return
                    if (stop < from && (id !in archiveIds || stop < archiveFrom)) return
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
            for (i in 0 until 14) if (raw[i] !in '0'..'9') return null
            val n = { from: Int, to: Int -> var v = 0; for (i in from until to) v = v * 10 + (raw[i] - '0'); v }
            // Зона «+0300» / «-0530»; без неї (чи кривої) — UTC.
            val zone = raw.substring(14).trim()
            val offsetMin = if (zone.length >= 5 && (zone[0] == '+' || zone[0] == '-') && (1..4).all { zone[it] in '0'..'9' }) {
                val z = (zone[1] - '0') * 600 + (zone[2] - '0') * 60 + (zone[3] - '0') * 10 + (zone[4] - '0')
                if (zone[0] == '-') -z else z
            } else 0
            // ponytail: календар і пошук зони на кожну передачу коштували на ТВ-приставці понад хвилину на файл
            // (100 тис. дат); дні від епохи рахуємо самі (алгоритм days_from_civil).
            val days = daysFromCivil(n(0, 4), n(4, 6), n(6, 8))
            val seconds = days * 86_400L + n(8, 10) * 3600L + n(10, 12) * 60L + n(12, 14) - offsetMin * 60L
            return seconds * 1000
        }

        /** Днів від 1970-01-01 до дати григоріанського календаря (H. Hinnant, days_from_civil). */
        private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
            val y = (if (month <= 2) year - 1 else year).toLong()
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146_097 + doe - 719_468
        }
    }
}

/** Розібране — на диск (лише наші канали): після перезапуску процесу не розбирати файл провайдера знову. */
fun XmltvGuide.writeTo(out: java.io.DataOutputStream) {
    out.writeInt(channelNames.size)
    channelNames.forEach { (id, names) ->
        out.writeUTF(id)
        out.writeInt(names.size)
        names.forEach { out.writeUTF(it.take(UTF_MAX)) }
    }
    out.writeInt(programmes.size)
    programmes.forEach { p ->
        out.writeUTF(p.channelId)
        out.writeUTF(p.title.take(UTF_MAX))
        out.writeLong(p.startMillis)
        out.writeLong(p.endMillis)
        out.writeBoolean(p.description != null)
        p.description?.let { out.writeUTF(it.take(UTF_MAX)) }
    }
}

fun readXmltvGuide(input: java.io.DataInputStream): XmltvGuide {
    val names = HashMap<String, List<String>>()
    repeat(input.readInt()) { names[input.readUTF()] = List(input.readInt()) { input.readUTF() } }
    val programmes = List(input.readInt()) {
        val channel = input.readUTF()
        val title = input.readUTF()
        val start = input.readLong()
        val end = input.readLong()
        TvProgramme(channel, title, start, end, if (input.readBoolean()) input.readUTF() else null)
    }
    return XmltvGuide(names, programmes)
}

/** writeUTF бере до 64 КБ у байтах; 8 тис. символів — з запасом і для кирилиці. */
private const val UTF_MAX = 8000

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
        // Два канали XMLTV з однаковою назвою («euronews» під двома id) обидва лягають на наш —
        // і кожна передача була двічі. Одна передача на одну хвилину старту.
        .mapValues { (_, list) -> list.sortedBy { it.startMillis }.distinctBy { it.startMillis } }
}
