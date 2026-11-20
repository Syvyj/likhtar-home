package com.likhtar.home.core.catalog

import com.likhtar.home.core.model.UkrainianPlurals
import java.net.URL

/**
 * Готовий до відтворення потік від власного агрегатора.
 *
 * [label] приходить із сервера вже зібраним («🇺🇦 2160p · Дубляж · Джерело · 8.4 Мбіт»),
 * тому клієнт нічого не форматує — лише показує.
 */
data class StreamOption(
    val label: String,
    val url: String,
    val season: Int = 0,
    val episode: Int = 0,
    /**
     * Мова доріжки словами сервера: `ukr`, `unknown`, `other`. Порожньо — поля не було.
     *
     * Три стани, не два: `unknown` — це «ми не знаємо», а не «не українська». На неігровому
     * він основний (заміряно на сервері: 583 варіанти з 638 без мовної позначки), і саме
     * тому рішення «вмикати мовчки» спирається на підтверджене `ukr`, а не на відсутність
     * заперечення.
     */
    val lang: String = "",
) {
    /**
     * Чим це грається: свій медіасервер чи чужий прямий лінк.
     *
     * Ключем беремо хост, а не шматок [label]: підпис сервер збирає для показу
     * («🇺🇦 2160p · HDR10+ · Джерело») і вільний змінити його формат будь-коли, а хост —
     * це те, чим воно є. Структурного поля про джерело агрегатор не віддає взагалі:
     * у відповіді лише `name`, `stream`, `s`, `e` і завжди однаковий `method`.
     */
    val source: String
        get() = runCatching { URL(url).authority.orEmpty() }.getOrDefault("")

    /**
     * Роздільність із підпису, `0` — не розібрали.
     *
     * Так, це розбір показового рядка, чого [source] спеціально не робить. Різниця в ціні
     * помилки: `source` керує порядком джерел, а це число їде ЛИШЕ у звіт на сервер. Не
     * зійшовся формат — поле поїде порожнім, і сервер виведе якість із `ref`; нічого на
     * екрані від цього не змінюється. Окремого поля агрегатор не віддає (заміряно:
     * у відповіді лише `method`, `name`, `stream`, `title`, `url`).
     */
    val quality: Int get() = qualityOf(label)
}

/**
 * Чи підтверджено, що доріжка українська — і чи можна її вмикати без питання.
 *
 * Судимо за полем `lang`, а не за прапорцем у підписі: підпис — показовий рядок, сервер
 * вільний змінити його формат, а поле це поле. Прапорець лишається лише запасним шляхом
 * на випадок відповіді без поля — тоді краще повестись як раніше, ніж замовкнути на всьому.
 *
 * Невідома мова тут НЕ українська: мовчки стартувати те, чого ми не знаємо, — і є той
 * випадок, коли людина почує російську без попередження.
 *
 * Поки ворота «тільки українське» стоять, у базі все `ukr` (сервер позначив 27441 рядок
 * разово), тож на екрані не змінюється нічого.
 */
val StreamOption.ukrainianAudio: Boolean
    get() = if (lang.isNotBlank()) lang == "ukr" else label.startsWith(UA_FLAG)

/**
 * Прапорець, яким сервер підписує українську доріжку.
 *
 * Сервер і так віддає лише українське, тож формально вистачило б «є хоч одне джерело».
 * Перевіряємо прапор явно, щоб мітка лишилась правдивою, якщо ворота колись послаблять.
 */
const val UA_FLAG = "\uD83C\uDDFA\uD83C\uDDE6"

/**
 * Підпис джерела для людини: без прапора (його показує мітка UA), «DV» замість «Dolby
 * Vision» і без
 * порожніх «·  ·», що лишаються після вирізання.
 */
fun cleanStreamLabel(label: String): String =
    label.replace(UA_FLAG, "").replace("Dolby Vision", "DV").replace(" ·  · ", " · ").trim(' ', '·')

/** `«🇺🇦 2160p · DV+HDR10+ · Remux …»` → `2160`. Суворо: три-чотири цифри й `p` за ними. */
fun qualityOf(label: String): Int =
    QUALITY_P.find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 0

/** Dolby Vision у підписі варіанта: «DV», «DV+HDR10+», «Dolby Vision». */
fun hasDolbyVision(label: String): Boolean =
    "Dolby Vision" in label || Regex("(^|[^A-Za-z])DV([^A-Za-z]|$)").containsMatchIn(label)

/**
 * Ключ «від кращого»: якість, а за рівної — без Dolby Vision вище, якщо [dolbyVisionFirst]
 * вимкнено. На деяких приставках DV-декодер без «DV HDR» у налаштуваннях (скидається
 * ребутом, вмикається лише вручну) дає темний екран — «Людина-павук» 4K DV. DV не
 * ховаємо: він у списку, лише нижче.
 */
fun bestFirstKey(label: String, dolbyVisionFirst: Boolean): Int =
    qualityOf(label) * 2 + if (!dolbyVisionFirst && hasDolbyVision(label)) 0 else 1

/**
 * Озвучка як одиниця вибору.
 *
 * Головне тут — саме озвучка, а сайт її властивість, а не навпаки: людина обирає
 * «дубляж чи Postmodern», а не «цей сайт чи той». Сервер зводить одну озвучку з різних
 * сайтів в один пункт, тому [refs] — це всі її варіанти в порядку сервера (краща якість,
 * тоді офіційніше джерело).
 *
 * [quality] і [hdr] — СТЕЛЯ серед варіантів, не середнє: якщо в групі є 2160p DV, вона
 * так і підписана, хоч поруч лежить 1080p.
 */
data class VoiceGroup(
    val voice: String,
    val quality: Int,
    val hdr: String,
    /** Скільком джерелам доступна ця озвучка — читається як «наскільки надійно». */
    val sources: Int,
    val refs: List<String>,
    /**
     * Мова доріжки, як її назвав сервер: `ukr`, `unknown`, `other`. Порожньо — поля не було.
     *
     * `unknown` — основний стан для неігрового, і підпис до нього мусить бути НЕЙТРАЛЬНИМ:
     * українська там цілком може бути, ми просто не знаємо. «Без української» тут було б
     * не скороченням, а вигадкою.
     */
    val lang: String = "",
    /**
     * Джерела прийшли від синтетичного двійника картки, де збіг неоднозначний.
     *
     * Показуємо такі пункти, але не мовчки: людина мусить бачити, що це здогад, а не
     * підтверджений збіг. Правило «неоднозначне не зливаємо» лишається — у базі це окремі
     * рядки, змінилось лише те, що картка більше не мовчить про сусіда.
     */
    val guess: Boolean = false,
    /**
     * Озвучку ніхто не назвав, і «Українська» — наша підстановка. Тоді підпис мусить
     * сказати бодай, ЗВІДКИ вона: пункт «Українська · 1080p» сам по собі не відповідає
     * ні чия це озвучка, ні чому їй вірити.
     */
    val unnamed: Boolean = false,
    /** Сайти, на яких ця озвучка лежить. */
    val from: List<String> = emptyList(),
    /**
     * Що ffprobe знайшов усередині файла: підписані аудіодоріжки й мови субтитрів.
     *
     * Одна «озвучка» в списку — це один ФАЙЛ, а в ньому доріжок буває кілька, і підпис
     * варіанта про це мовчить. З'являється не одразу: розбір іде фоном після першого
     * відкриття картки.
     */
    val audio: List<Track> = emptyList(),
    val subs: List<Track> = emptyList(),
    /** Підписи пунктів за ref — що саме відкривається («1080p · WEBRip · HEVC · 3.8 ГБ»). */
    val names: Map<String, String> = emptyMap(),
    /** Джерело пункта за ref — службова назва сервера. */
    val sourceOf: Map<String, String> = emptyMap(),
    /** Ранг варіанта за ref від сервера в мить пошуку; немає — сервер не сказав. */
    val rank: Map<String, Int> = emptyMap(),
) {
    /**
     * Підпис одного варіанта в розгорнутій озвучці: сайт і якість — з цього людина й
     * вирішує, що вмикати, а не сервер за неї. Збірка може підписати свої джерела інакше.
     */
    fun optionLabel(ref: String): String {
        val name = cleanStreamLabel(names[ref].orEmpty())
        val source = sourceOf[ref].orEmpty()
        flavorOptionLabel(source, name, rank[ref])?.let { return it }
        return buildString {
            append(siteName(source).ifBlank { "онлайн" })
            qualityOf(name).takeIf { it > 0 }?.let { append(" · ").append(it).append('p') }
        }
    }

    /** Що написати на кнопці: озвучка, її стеля якості й надійність. */
    val label: String
        get() = buildString {
            append(voice)
            if (quality > 0) append(" · ").append(quality).append('p')
            if (hdr.isNotBlank()) append(" · ").append(hdr)
            // Прапорець українського сервер уже ставить у власний підпис пункта, тож тут
            // домальовуємо лише те, чого в ньому немає: невідому й іншу мову.
            when (lang) {
                "unknown" -> append(" · мова не вказана")
                "other" -> append(" · інша мова")
            }
            // Сайт показуємо завжди, а не лише для безіменної озвучки: «3 джерела» саме
            // по собі ні про що не каже, а «сайт · 3 варіанти» — каже, що це три
            // різні релізи тієї самої озвучки й перший-ліпший можна брати.
            if (from.isNotEmpty()) {
                append(" · ").append(from.joinToString(", ") { siteName(it) })
            }
            if (guess) append(" · здогад за назвою")
            if (sources > 1) {
                append(" · ").append(sources).append(' ')
                append(UkrainianPlurals.pick(sources, "варіант", "варіанти", "варіантів"))
            }
        }

    /**
     * Коротко для шапки тайтла: лише чия озвучка. Якість — окремою міткою поруч, тут вона
     * лише рябіла («1080p» біля кожної). Безіменна — назвою сайту: «Джерело»,
     * а не «Українська (Джерело)».
     */
    val short: String
        get() = if (unnamed) from.joinToString(", ") { siteName(it) }.ifBlank { voice } else voice

    /**
     * Рядок під сфокусованою озвучкою: що лежить усередині файла, коротко. Українські —
     * до двох, оригінал — перша англійська, решта — «ще N». Повний перелік (два десятки
     * студій) не потрібен; російських тут уже немає — їх відкидає [probeTracks].
     */
    val inside: String
        get() {
            val ukr = audio.filter { it.lang == "ukr" }
            val original = audio.firstOrNull { it.lang == "eng" }
            val shown = ukr.take(2) + listOfNotNull(original)
            val rest = audio.size - shown.size
            return listOfNotNull(
                ukr.take(2).joinToString(", ") { it.label }.ifBlank { null },
                original?.let { "оригінал: ${it.label}" },
                "ще $rest".takeIf { rest > 0 },
                "укр. субтитри".takeIf { subs.any { it.lang == "ukr" } },
            ).joinToString(" · ")
        }
}

/** Доріжка файла з ffprobe: підпис для людини й код мови (`ukr`, `eng`, порожньо — невідома). */
data class Track(val label: String, val lang: String)

/**
 * Відповідь `/api/play`.
 *
 * [reason] важлива не менше за [url]: «джерело готується» — це «спробуйте за хвилину», а
 * не «мертве джерело», і на екрані це різні слова.
 */
/** Підсумок підготовки на своєму сервері потоків: чи наповнився початок і як швидко качало. */
data class OwnWarm(val ready: Boolean, val bytesPerSec: Double = 0.0, val connections: Int = 0)

data class PlayResult(
    val url: String = "",
    val name: String = "",
    val reason: String = "",
    /**
     * Яке саме джерело зіграло — щоб потім переслати назад у `/api/attempt`.
     *
     * Сервер бере якість варіанта за `ref`, тож без нього статистика по плеєрах сліпа на
     * той самий розріз, заради якого й збиралась: «який плеєр тягне 2160p» лишається без
     * відповіді, хоч рядки й ідуть.
     */
    val ref: String = "",
    val source: String = "",
    val quality: Int = 0,
    /** Субтитри, які знайшов сервер (з самого джерела й OpenSubtitles): підпис → адреса. */
    val subs: List<Pair<String, String>> = emptyList(),
) {
    val ok: Boolean get() = url.startsWith("http")
}

/**
 * Чи читає плеєр потік.
 *
 * Не про мережу: сервер дивиться список читачів потоку з їхньою позицією,
 * тому показник рухається й тоді, коли плеєр бере дані з кешу, де трафіку немає взагалі.
 *
 * [readers] `0` — сигнал сильний: плеєр навіть не відкрив потік. [moved] `0` при живому
 * читачі — слабкий: плеєр, що набрав буфер уперед, може законно кілька секунд нічого не
 * читати, тож вердикт лише з другої спроби.
 */
data class ReadingCheck(
    val reading: Boolean = true,
    val readers: Int = 1,
    val moved: Int = 1,
    val why: String = "",
) {
    /** Плеєр не відкрив потік — те єдине, у чому можна бути впевненим з першого разу. */
    val silent: Boolean get() = !reading && readers == 0

    /** Живий читач без руху: законний стан плеєра з буфером, тож питаємо ще раз. */
    val doubtful: Boolean get() = !reading && !silent && moved == 0
}

/**
 * Чи справді плеєр не читає — з однієї або двох перевірок.
 *
 * `readers:0` вирішує одразу. Живий читач без руху вирішує лише тоді, коли й друга
 * перевірка каже те саме: плеєр, який набрав буфер уперед, кілька секунд законно нічого
 * не читає, і вердикт з першого разу звинувачував би справний плеєр.
 */
fun playerIsSilent(first: ReadingCheck, second: ReadingCheck? = null): Boolean = when {
    first.silent -> true
    first.reading -> false
    first.doubtful -> second != null && !second.reading
    else -> false
}

/**
 * Клієнт до власного сервера джерел.
 *
 * Навіщо окремо від [ResolverClient]: той віддає діплінки в чужі застосунки (MEGOGO,
 * Джерело), а цей — **прямі потоки**, які грає будь-який зовнішній плеєр. Пошук по
 * джерелах, нормалізацію підписів, фільтр «тільки українське», сортування від 4K і
 * пробінг плейлистів (бітрейт/кодек/аудіодоріжки) робить СЕРВЕР — для приставки це
 * задорого, та й частина сайтів вимагає українського IP і браузерних заголовків.
 *
 * Запитуємо з `rjson=true`: тоді агрегатор віддає JSON. Без цього параметра він
 * повертає HTML для інших клієнтів, який нам не потрібен.
 *
 * Порожня адреса означає «не налаштовано» — кнопка просто не з'являється.
 */

/** Код мови з ffprobe у слово. Незнайомий код показуємо як є — це чесніше за «інша». */
internal fun langName(code: String): String = when (code) {
    "ukr" -> "українська"
    "eng" -> "англійська"
    "rus" -> "російська"
    "pol" -> "польська"
    "fre", "fra" -> "французька"
    "ger", "deu" -> "німецька"
    "spa" -> "іспанська"
    "jpn" -> "японська"
    "" , "und" -> ""
    else -> code
}


/** Стан джерела під час запуску: з'єднання, швидкість, хід підвантаження початку. */
data class SourceStat(
    val connections: Int,
    val sources: Int,
    val speedBytes: Double,
    val buffered: Long,
    val bufferTarget: Long,
) {
    /** Частка підвантаженого початку, 0..1; `null` — підвантаження ще не почалось. */
    val bufferShare: Float? get() = if (bufferTarget > 0) (buffered.toFloat() / bufferTarget).coerceIn(0f, 1f) else null
}

/**
 * Серія в черзі плеєра. [overview] і [still] — з TMDB, для колонки «Епізоди» в нашому Just+: опис під
 * серією у фокусі й кадр самої серії замість постера серіалу.
 */
data class QueueEntry(
    val episode: Int,
    val name: String,
    val url: String,
    val overview: String = "",
    val still: String = "",
)

/** `#EXTINF:-1,S01E03 · Назва` + адреса → серії; номер серії — з `SxxEyy`. */
internal fun parseM3u(body: String): List<QueueEntry> {
    val out = mutableListOf<QueueEntry>()
    var name: String? = null
    for (raw in body.lineSequence()) {
        val line = raw.trim()
        when {
            line.startsWith("#EXTINF") -> name = line.substringAfter(',', "").trim()
            line.startsWith("http") -> {
                val n = name.orEmpty()
                val ep = EPISODE_TAG.find(n)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                out += QueueEntry(ep, n, line)
                name = null
            }
        }
    }
    return out
}


// Шаблони — один раз на процес, а не на кожен виклик: `qualityOf` кличуть на кожну картку й
// кожне джерело.
private val QUALITY_P = Regex("""(?<!\d)(\d{3,4})p(?!\d)""")
private val EPISODE_TAG = Regex("""S\d+E(\d+)""")

/** Назва джерела для людини; назви сайтів — у джерелах збірки ([FLAVOR_SITE_NAMES]). */
internal fun siteName(source: String): String = FLAVOR_SITE_NAMES[source] ?: when (source) {
    "teleportal" -> "Телепортал"
    "1plus1" -> "1+1"
    else -> source.removeSuffix("_ua").replaceFirstChar(Char::uppercase)
}
