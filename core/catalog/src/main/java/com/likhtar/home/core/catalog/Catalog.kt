package com.likhtar.home.core.catalog

/** Тип одиниці каталогу. TMDB нумерує фільми й серіали окремо, тому тип — частина ключа. */
enum class TitleKind(val tmdbPath: String) {
    MOVIE("movie"),
    TV("tv"),
    ;

    companion object {
        fun fromTmdb(value: String?): TitleKind = if (value == "tv") TV else MOVIE
    }
}

/** Картка в рядку каталогу — рівно те, що потрібно намалювати постер і відкрити деталі. */
data class CatalogItem(
    val tmdbId: Int,
    val kind: TitleKind,
    val title: String,
    /**
     * Назва мовою оригіналу. Потрібна для пошуку в чужих застосунках: каталоги
     * онлайн-кінотеатрів індексують оригінальну назву, а український переклад TMDB там
     * здебільшого не знаходиться.
     */
    val originalTitle: String = "",
    val year: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val logoPath: String? = null,
    val rating: Double = 0.0,
    val overview: String = "",
    /**
     * Найкраща якість, яку є ЧИМ увімкнути, — з наших джерел. `0` — не знаємо: офіційні
     * сервіси якості не кажуть, і вигадане «4K» було б гіршим за мовчання.
     */
    val quality: Int = 0,
    /** HDR найкращого джерела: «Dolby Vision», «DV+HDR10+», «HDR». Порожньо — немає. */
    val hdr: String = "",
    /**
     * Жанри назвами зі словника TMDB — того самого, яким тепер говорить і наш сервер.
     *
     * Потрібні не для показу, а для розкладки: «Реаліті» кладе картку в ряд за жанром, і
     * без них класифікувати позицію з `discover` нічим — він віддає лише `genre_ids`.
     */
    val genres: List<String> = emptyList(),
    /**
     * Дата виходу повністю (`2026-09-12`), а не лише рік.
     *
     * Потрібна «Реаліті»: ряд упорядкований від найсвіжішого, і за роком це виходило
     * «то 2023, то 2026» — рік не розрізняє січень від грудня. Порожньо, коли TMDB дати
     * не дав.
     */
    val releaseDate: String = "",
    /**
     * Коли позиція стала доступною в українській вітрині.
     *
     * Це не дата прем'єри: старий серіал може з'явитися на сервісі сьогодні. Поле
     * заповнює лише серверний список свіжих надходжень і воно має пріоритет у
     * тематичних рядах, бо саме його користувач сприймає як «нове».
     */
    val availabilityDate: String = "",
    /**
     * Картка не тайтла, а сторінки: `franchise:1241` чи `chronology:mcu` — ряд «Франшизи й
     * всесвіти». Порожньо — звичайна картка, клік веде на сторінку фільму.
     */
    val opens: String = "",
    /**
     * Твір порталу (Телепортал, 1+1) з українського каталогу: адреса його сторінки шоу —
     * `serials/stb/likari`, `plus1/100807`. TMDB його не знає, тож сторінка фільму тут не
     * відкрилась би. Порожньо — звичайна картка.
     */
    val showId: String = "",
    /** Мовник твору поза TMDB — «СТБ», «1+1»: доказ української на сторінці шоу. */
    val channel: String = "",
    /** Підпис у кутку картки від сервера: «вийде 12 жовтня», «2 сезон · 4 жовтня». */
    val note: String = "",
    /** Картка ряду «Трейлери»: ролик YouTube, який відкриває OK (замість сторінки тайтла). */
    val trailerKey: String = "",
    /** Аніме (жанр TMDB 16 + Японія чи Китай) — ховається, коли аніме вимкнено. */
    val anime: Boolean = false,
) {
    /** Ключ, під яким Ліхтар зберігає збагачену картку: серіали з префіксом `tv`. */
    val likhtarKey: String get() = if (kind == TitleKind.TV) "tv$tmdbId" else "$tmdbId"

    // Картка сторінки ключується тим, куди веде: всесвіт і колекція часто мають той самий
    // id колекції TMDB, і однаковий ключ у ряду валив застосунок.
    val id: String get() = if (opens.isNotBlank()) "page-$opens" else "${kind.name.lowercase()}-$tmdbId"

    /** Чим шукати в чужому застосунку: оригінал, якщо він є. */
    val searchQuery: String get() = originalTitle.ifBlank { title }
}

/** Рядок каталогу: заголовок, джерело курації і запит, яким він наповнюється. */
data class CatalogRow(
    val id: String,
    val title: String,
    val source: String,
    val path: String,
    val params: Map<String, String>,
    val items: List<CatalogItem> = emptyList(),
    /** Порядок у рядку сам є змістом (хронологія, частини франшизи) — тоді картки нумеруються. */
    val numbered: Boolean = false,
    /** Банер франшизи дня: кадр і куди веде «Детальніше». `null` — звичайний ряд. */
    val spotlight: RowSpotlight? = null,
    /** Як малювати ряд на сторінці всесвіту. */
    val layout: RowLayout = RowLayout.DEFAULT,
    /** Порядок кураторський (саги, «Вулиці Нью-Йорка») — не переставляти за озвучкою. */
    val keepOrder: Boolean = false,
    /** Плитки жанрів ([RowLayout.GENRES]) — ряд без карток. */
    val genres: List<SectionGenre> = emptyList(),
)

/** Подача ряду на сторінці всесвіту — як у Disney+. */
enum class RowLayout {
    DEFAULT,

    /** «Нове й популярне»: більші картки, три на екран. */
    FEATURED,

    /** Вертикальні постери — ряди сторінки стрімінгу. */
    POSTERS,

    /** Топ із великими номерами на горизонтальних картках, як топ-10 в Apple TV. */
    TOP,

    /** Плитки жанрів сервісу: ведуть у жанр усередині цього сервісу. */
    GENRES,

    /** Банер одного тайтлу на всю ширину: кадр, лого, опис, кнопка. */
    SPOTLIGHT,
}

/** Ряд курованої сторінки всесвіту з `likhtar-rows.json`. */
data class UniverseRow(
    val title: String,
    /**
     * `featured` | `spotlight` | `chronology` | `collections` | `worlds` | `server` |
     * `top` | `posters` | порожньо — звичайний.
     */
    val layout: String,
    val items: List<Pair<TitleKind, Int>>,
    /** Картки сторінок у ряду `worlds`: `u:middleearth` — всесвіт, `c:119` — колекція TMDB. */
    val pages: List<String> = emptyList(),
    /** Для `server` — котрий ряд сервера стоїть на цьому місці. */
    val serverIndex: Int = -1,
)

/** Банер ряду у подачі Disney+: кадр франшизи, назва, кнопка й частини поруч. */
data class RowSpotlight(
    val backdropPath: String?,
    /** `franchise:131635` — те саме, що [CatalogItem.opens]. */
    val opens: String,
    /** Лого франшизи; немає — назва текстом. */
    val logoPath: String? = null,
)

/**
 * Сервіс або телеканал із курації Ліхтаря — власна сторінка з усіма його рядками.
 *
 * У головну стрічку Кінозалу з кожного сервісу йде лише початок: дев'ять сервісів по
 * 3–11 категорій — це 68 рядків, які пультом не пройти. Решта живе тут.
 */
data class CatalogStudio(
    val id: String,
    val title: String,
    val rows: List<CatalogRow>,
    /** Кураторська сторінка стрімінгу: флагмани, світи, банери між рядами сервера. */
    val page: List<UniverseRow> = emptyList(),
)

/** Позиція хронології: назва й рік. Саме назва, а не TMDB id — її можна перевірити очима. */
data class ChronologyEntry(
    val title: String,
    val year: Int,
)

/** Хронологія всесвіту: порядок перегляду за подіями, а не за датою виходу. */
data class Chronology(
    val key: String,
    val title: String,
    val entries: List<ChronologyEntry>,
    /** Лого всесвіту (шлях TMDB) — голова сторінки. Своїх лого в колекцій TMDB немає. */
    val logoPath: String? = null,
    /** Запит discover/tv для ряду «Серіали всесвіту»: `with_keywords=180547`. */
    val tvQuery: String? = null,
    /** Колекції TMDB для ряду «Колекції» в кінці сторінки всесвіту. */
    val collections: List<Int> = emptyList(),
    /** Кураторська сторінка всесвіту. Порожньо — хронологія, серіали й колекції. */
    val page: List<UniverseRow> = emptyList(),
)

/** Франшиза, до якої належить фільм (TMDB collection). */
data class FranchiseRef(
    val id: Int,
    val name: String,
)

/** Що показує окрема сторінка каталогу: сервіс, хронологія або франшиза. */
enum class CatalogListingKind {
    STUDIO,
    CHRONOLOGY,
    FRANCHISE,

    /** Типова добірка Кінозалу («Новинки», «Топи», «Серіали»…) — та сама сітка, більший ліміт. */
    COLLECTION,

    /**
     * Ряд «Суто українське»: курація плюс наш каталог, зведені в одну стрічку.
     *
     * Окремий вид, бо з одного запиту TMDB цей ряд не відтворити — у ньому є позиції,
     * яких у TMDB немає взагалі.
     */
    UKRAINIAN,

    /**
     * Мовник із TMDB: 1+1, СТБ, ICTV, Netflix.
     *
     * Цінні тут саме українські — це ті самі канали, з чиїх ютуб-каналів зібране «Наше»,
     * тож колись рядок «1+1» зможе показувати і серіали каталогу, і шоу поруч.
     */
    NETWORK,
    ;

    companion object {
        fun fromRoute(value: String?): CatalogListingKind? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/** Мовник і скільком його тайтлам є що вмикати. */
data class CatalogNetwork(
    val name: String,
    val titles: Int,
    /**
     * Код країни мовника (`UA`, `US`, `JP`), порожньо — сервер не сказав.
     *
     * Порожнє тут означає «не знаємо», а не «не наша»: за таким мовником не фільтруємо,
     * бо прибрати справжній канал через власне незнання гірше, ніж показати лишній.
     */
    val country: String = "",
)

/** Наповнена сторінка каталогу: заголовок і готові рядки. */
data class CatalogListing(
    val title: String = "",
    val subtitle: String = "",
    val rows: List<CatalogRow> = emptyList(),
    val message: String? = null,
    /**
     * Кадр сторінки франшизи чи всесвіту. Є — голова сторінки героєм: кадр на весь екран і
     * назва великими літерами (подача Disney+). Немає — звичайний заголовок.
     */
    val backdropPath: String? = null,
    /** Лого франшизи замість назви текстом. */
    val logoPath: String? = null,
    /** «Відкрити всі»: частини одного ряду сітками різної форми з банером між ними. */
    val sectioned: Boolean = false,
) {
    companion object {
        /**
         * Голова-герой без кадру тайтлу — спільний кіношний кадр із ресурсів. «Суто українське» брало кадр
         * першої картки, і «Лікарі» стояли і в банері, і першою ж карткою під ним.
         */
        const val NEUTRAL_BACKDROP = "neutral:ua"
    }
}

/** Плитка настрою: фільтр за жанрами TMDB. Порожній список жанрів — «на твій смак». */
data class MoodTile(
    val title: String,
    val genres: List<String>,
)

/**
 * Типова добірка Кінозалу: рядок під кандидатами й та сама сітка під «Відкрити всі».
 *
 * Запит живе тут, а не в курації Ліхтаря: це не смак, а чотири очевидні зрізи TMDB, які
 * мають бути на місці навіть коли курація недоступна.
 */
enum class WatchCollection(
    val key: String,
    val title: String,
    val path: String,
    val params: Map<String, String>,
) {
    // «Вийшло в цифрі» (`with_release_type=4|6`) стояв окремим рядом і показував ті самі
    // назви, що й цей: заміряно — обидва ряди відкривались четвіркою «Біжи!»,
    // «Фестиваль небезпек», «Загадкова жінка», «Barreda». Два ряди з однаковим вмістом —
    // це не вибір, а враження, що каталог порожній. Лишився один.
    FRESH(
        "fresh",
        // Назва без «українською»: у вітрину не заходить нічого без доріжки, тож
        // уточнення лише додає слів.
        "Новинки фільмів",
        "discover/movie",
        mapOf(
            "sort_by" to "primary_release_date.desc",
            "vote_count.gte" to "60",
            "primary_release_date.lte" to "{current_date}",
        ),
    ),
    NEW_SERIES(
        "new_series",
        "Новинки серіалів",
        "discover/tv",
        mapOf(
            "sort_by" to "first_air_date.desc",
            "first_air_date.lte" to "{current_date}",
            "vote_count.gte" to "20",
        ),
    ),
    TOP(
        "top",
        "Топи",
        "discover/movie",
        mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "1500"),
    ),
    // «Популярне» без межі по роках — це вічнозелене: у TMDB популярним майже завжди
    // лишається старе й відоме. Заміряно: ряд «Серіали» відкривався «Менталістом»
    // (2008) і «Законом і порядком» (2004). Три роки — не смак, а відповідь на скаргу
    // «є відчуття застарілості». «Топи» межі не мають навмисно: там саме це і є змістом.
    SERIES(
        "series",
        "Серіали",
        "discover/tv",
        mapOf(
            "sort_by" to "popularity.desc",
            "vote_count.gte" to "200",
            "first_air_date.gte" to "{current_date-1095}",
            "first_air_date.lte" to "{current_date}",
        ),
    ),
    MOVIES(
        "movies",
        "Фільми",
        "discover/movie",
        mapOf(
            "sort_by" to "popularity.desc",
            "vote_count.gte" to "300",
            "primary_release_date.gte" to "{current_date-1095}",
            "primary_release_date.lte" to "{current_date}",
        ),
    ),
    STREAMING(
        "streaming",
        "Нове на підписках",
        "discover/movie",
        mapOf(
            "sort_by" to "primary_release_date.desc",
            "with_watch_providers" to "8|350|119|337|1899",
            "with_release_type" to "4|6",
            "vote_count.gte" to "20",
            "primary_release_date.lte" to "{current_date}",
        ),
    ),
    ;

    companion object {
        fun byKey(key: String?): WatchCollection? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Перше питання Кінозалу: скільки в людини часу.
 *
 * Це не фільтр каталогу, а половина відповіді на «що подивитись сьогодні ввечері»:
 * о 22:40 у будній день тригодинний фільм — неправильна порада, хоч би який він добрий.
 */
enum class WatchTime(val title: String) {
    NINETY("≈ 90 хв"),
    EPISODE("Одна серія"),
    ANY("Без різниці"),
}

/**
 * Друге питання: настрій. Жанри TMDB для фільмів і серіалів різні, тому пари окремо —
 * інакше «Напружене» в серіалах віддавало б порожньо: жанру «трилер» у TV немає.
 */
/**
 * Що дивимось — перше питання Ліхтарика (замість «≈ 90 хв», на який у базі
 * 0–6 фільмів). [param] — `format=` у `/api/advisor`.
 */
enum class WatchFormat(val param: String) {
    MOVIE("movie"),
    TV("tv"),
    ANIME("anime"),
    CARTOON("cartoon"),
    DOC("doc"),
    REALITY("reality"),
}

enum class WatchMood(
    val title: String,
    /**
     * Корінь слова для `mood=` у `/api/advisor` — сервер шукає підрядком по вільному
     * словнику пачок, тож повне слово майже завжди дає нуль: заміряно, «тепла» —
     * 3 збіги, «тепл» — 414.
     *
     * Корені дібрані заміром, а не на слух: «легк» 3, «напруж» 5, «драм» 5. Найближчі за
     * змістом до «Розумного» — «розум», «склад» і «детектив» — віддають нуль, хоча
     * «Складний» і «Детективний» самі стоять у `mood[]` відповідей.
     */
    val moodRoot: String,
) {
    // Кілька коренів через «|» — сервер бере будь-який, але лише з початку слова й серед
    // перших трьох тегів картки («містич» ловив «оптимістичний», «Новобранець» у «Таємниче»).
    LIGHT("Легке", "легк|тепл|затишн"),
    FUNNY("Смішне", "комед|весел|смішн|іронічн"),
    ROMANTIC("Романтичне", "романт"),
    TENSE("Напружене", "напруж|тривож"),
    MYSTERY("Таємниче", "таємн|загадк|містич"),
    ADVENTURE("Пригоди", "пригод|епічн"),
    SMART("Розумне", "драм|психолог|склад"),
}

/**
 * Кандидат на вечір: назва плюс чесна причина, чому вона тут.
 *
 * Причина не вигадується: або текст із картки Ліхтаря, або рядок, зібраний із того, що ми
 * справді знаємо — жанр, рік, оцінка, де безкоштовно. Порада без причини це вітрина.
 */
data class Candidate(
    val item: CatalogItem,
    val meta: String,
    /** Слово радника з картки Ліхтаря. Порожнє — картки немає, і вигадувати нічого не будемо. */
    val worth: String,
    /** Сервіс, де це можна подивитись безкоштовно; `null` — такого немає або не знаємо. */
    val freeOn: String? = null,
    /** Чому радник пропонує саме це — від позначок 👍 («жанр «драма», як «Озарк»»). */
    val why: String = "",
)


/**
 * Збагачена картка Ліхтаря — те, чого немає ні в TMDB, ні в JustWatch.
 *
 * Порожні поля — норма: база Ліхтаря наростає прогонами, і для фільму, якого ще немає,
 * сторінка мусить працювати без цих панелей.
 */
data class LikhtarCard(
    val summary: String = "",
    val worth: String = "",
    val forWhom: String = "",
    val notForWhom: String = "",
    val moods: List<String> = emptyList(),
    val complexity: Int = 0,
    val violence: Int = 0,
    val erotica: Int = 0,
    val childrenFriendly: Boolean = false,
    /** Пояснення до скорборду — «чому 3 зі складності» — рядком під кожною шкалою. */
    val complexityNote: String = "",
    val violenceNote: String = "",
    val eroticaNote: String = "",
    val childrenNote: String = "",
    val endingExplained: String = "",
    val easterEggs: List<String> = emptyList(),
) {
    val hasContent: Boolean
        get() = summary.isNotBlank() || worth.isNotBlank() || moods.isNotEmpty()

    /** Є що показати в панелі «Чи варто?» — оцінка й кому підійде. */
    val hasVerdict: Boolean
        get() = worth.isNotBlank() && (forWhom.isNotBlank() || notForWhom.isNotBlank())
}

/** Де можна подивитися: сервіс із TMDB watch/providers плюс спосіб відкрити його на пристрої. */
data class WatchOption(
    val label: String,
    val kind: OptionKind,
    /** `null` — застосунок не встановлений, показуємо лише як довідку про доступність. */
    val launch: LaunchAction? = null,
    /**
     * Що на ЦЬОМУ сервісі українською: `uk` — озвучка, `subs` — лише субтитри, порожньо —
     * не знаємо. Кнопка «Netflix» досі мовчала про це, хоча для вибору вечора це головне.
     */
    val audio: String = "",
    /**
     * Назва сервісу чи застосунку без дієслова («Netflix», «MEGOGO»): за нею екран шукає
     * бренд. [label] уже мовою інтерфейсу, тож розбирати його на префікси не можна.
     */
    val service: String = "",
) {
    /** Порядок на екрані: спершу те, що справді відкриє цей фільм. */
    enum class OptionKind {
        /** Відкриває саме цю картку в застосунку. */
        DIRECT,

        /** Універсальний зовнішній плеєр, який відкриває картку за TMDB id. */
        EXTERNAL_PLAYER,

        /** Відкриває пошук у застосунку — далі користувач вибирає сам. */
        SEARCH,

        /** Відкриває лише сам застосунок: картку в ньому доведеться шукати руками. */
        OPEN,

        /** Сервіс має контент, але застосунка на пристрої немає. */
        INFO,
    }
}

/**
 * Що саме зробити, щоб відкрити.
 *
 * Порядок спроб у ViewModel такий самий, як тут: [uri] — конкретна адреса; [searchQuery] —
 * системний SEARCH-інтент у застосунок, який оголосив `android.app.searchable`; якщо немає
 * ні того, ні того — просто запуск за пакетом.
 */
data class LaunchAction(
    val packageName: String,
    val uri: String? = null,
    val searchQuery: String? = null,
)

/** Серія сезону — рівно те, що показує картка епізода на сторінці серіалу. */
data class Episode(
    val number: Int,
    val title: String,
    val overview: String = "",
    val stillPath: String? = null,
    val runtimeMinutes: Int = 0,
    val airDate: String = "",
) {
    /** Серія ще не вийшла: дата виходу пізніше за сьогодні. */
    val upcoming: Boolean
        get() = airDate.isNotBlank() &&
            airDate > java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    /**
     * Чи має серія власну назву, а не «Серія 3» / «Епізод 3».
     *
     * TMDB для частини серіалів віддає назвою просто номер, а коли не віддає нічого,
     * номер підставляємо ми самі. У таких випадках назва дублює номер, який і так стоїть
     * над нею, — і картка каже те саме двічі.
     */
    val hasOwnTitle: Boolean
        get() = title.isNotBlank() &&
            !title.equals("Серія $number", ignoreCase = true) &&
            !title.equals("Епізод $number", ignoreCase = true) &&
            !title.equals("Episode $number", ignoreCase = true)
}


/**
 * Ролик із TMDB: трейлер, тизер, уривок чи закадрове.
 *
 * Лише YouTube: інші майданчики TMDB віддає рідко, і відкрити їх на приставці нічим.
 */
data class TitleVideo(
    val key: String,
    val name: String,
    val type: String,
    /** Мова ролика з TMDB (`iso_639_1`): український трейлер — першим. */
    val language: String = "",
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$key"
}

/** Повні дані сторінки фільму. */
/** Актор у «Деталях»: ім'я, роль і фото TMDB (`profile_path`). */
data class CastMember(val name: String, val role: String = "", val photoPath: String? = null)

data class TitleDetails(
    val item: CatalogItem,
    /** Кадри без тексту для живого фону першої сцени (TMDB `images.backdrops`, до 6). */
    val stills: List<String> = emptyList(),
    val runtimeMinutes: Int = 0,
    val genres: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val ageRating: String = "",
    val logoPath: String? = null,
    /** `null` — фільм не входить у франшизу; інакше кнопка «Франшиза» на сторінці. */
    val franchise: FranchiseRef? = null,
    val card: LikhtarCard = LikhtarCard(),
    val watchOptions: List<WatchOption> = emptyList(),
    /**
     * Студії озвучки, які зробили саме цей тайтл.
     *
     * Окремо від [watchOptions], бо це не «чим відкрити», а «чия доріжка»: людина обирає
     * конкретну студію свідомо, і в спільному списку варіантів вона тонула серед двох
     * десятків рядків.
     */
    val dubStudios: List<String> = emptyList(),
    /** Назви сервісів із TMDB watch/providers для регіону — потрібні для атрибуції JustWatch. */
    val providerNames: List<String> = emptyList(),
    /** Перші кілька акторів — «У ролях …» унизу сторінки, як на Apple TV. */
    val cast: List<String> = emptyList(),
    val director: String = "",
    /** Актори з фото й роллю — ряд «Актори» в «Деталях». */
    val people: List<CastMember> = emptyList(),
    /** Гасло TMDB — бурштиновим рядком під описом у «Деталях». */
    val tagline: String = "",
    /** Автор серіалу (created_by); у фільмів — порожньо, там режисер. */
    val creator: String = "",
    /** Канал серіалу (networks) — «Канал» у «Деталях». */
    val network: String = "",
    /** Скільки всього серій у серіалі — «Сезонів і серій». */
    val episodeCount: Int = 0,
    /** Дата прем'єри (ISO) — «Прем'єра». */
    val premiere: String = "",
    /** Номери сезонів серіалу з TMDB; порожньо для фільмів. */
    val seasonNumbers: List<Int> = emptyList(),
    /** Трейлери й закадрове з TMDB; порожньо, коли роликів немає або ключа немає. */
    val videos: List<TitleVideo> = emptyList(),
    /** Що дивитись далі — рекомендації TMDB за цим тайтлом. */
    val similar: List<CatalogItem> = emptyList(),
    /**
     * Що сервер знає про тайтл. `null` — його ще не питали.
     *
     * Потрібно, щоб «джерел немає» не злилося з «ще не перевіряли»: перше — відповідь,
     * друге — очікування, і на екрані це різні речі.
     */
    val scan: WatchScan? = null,
)

/** Розміри зображень TMDB. Постер 342 і бекдроп 1280 — компроміс для 1080p-приставки. */
object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p"

    /**
     * Мова логотипа за його шляхом — TMDB віддає її разом із файлом.
     *
     * Потрібна одному рішенню: чи підписувати знак українською назвою. Лише 22 з 50
     * тайтлів наших рядів мають логотип українською (заміряно); решта показували
     * «THE RUNNER» над карткою «Біжи!». Мапа в пам'яті, а не поле картки: логотип
     * довантажується в кількох місцях, і тягнути нове поле крізь усі копії картки було б
     * більше коду, ніж користі.
     *
     * ponytail: живе до перезапуску процесу; після нього мова знову приходить із першим
     * же запитом логотипа.
     */
    private val logoLanguages = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun rememberLogoLanguage(path: String, language: String) {
        logoLanguages[path] = language
    }

    /** Чи треба під знаком українська назва: знак чужою мовою, а назва в нас своя. */
    fun needsLocalTitle(path: String?, title: String): Boolean {
        if (path.isNullOrBlank()) return false
        val language = logoLanguages[path] ?: return false
        return language != "uk" && title.any { it in 'а'..'я' || it in 'А'..'Я' || it in "іїєґІЇЄҐ" }
    }

    /**
     * Готову адресу лишаємо як є.
     *
     * Не всі картки в лаунчері приходять із TMDB: у «Реаліті» кадри дає Телепортал і наші
     * набори, і там уже повний URL. Без цієї перевірки до нього спереду приклеювався б
     * `image.tmdb.org`, і картка мовчки лишалась порожньою — саме такий вигляд і має
     * «ряд без зображень».
     */
    private fun size(prefix: String, path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http") -> path
        else -> "$BASE/$prefix$path"
    }

    fun poster(path: String?): String? = size("w342", path)

    /**
     * Постер для картки в сітці чи рядку.
     *
     * Картка займає 96 dp — на 1080p це 192 пікселі, тобто `w342` віддає вдвічі більше,
     * ніж екран здатен показати, і кожен такий бітмап важить у пам'яті ~700 КБ проти 220.
     * На сітці з чотирьох десятків карток різниця — десятки мегабайтів, а коли кеш
     * переповнюється, Coil починає перезавантажувати щойно показане. Ту саму помилку вже
     * ловили в ТВ Ефірі (кадр 980×560 у картку 126 dp давав 48 мс на кадр прокрутки).
     */
    fun posterThumb(path: String?): String? = size("w185", path)
    fun backdrop(path: String?): String? = size("w1280", path)

    /** Один кадр на всю ширину 1080p: w1280 там розмитий, беремо оригінал (зазвичай 1920). */
    fun hero(path: String?): String? = size("original", path)

    /** Кадр серії у картці епізода ~300dp — w500 достатньо і вчетверо легше за w1280. */
    fun still(path: String?): String? = size("w500", path)

    /**
     * Кадр для картки в рядку (126 dp ≈ 252 px). `w1280` тут — 25-кратний надлишок пікселів
     * і стільки ж зайвого трафіку на кожну картку; на екрані різниці не видно.
     */
    fun backdropThumb(path: String?): String? = size("w300", path)
    fun logo(path: String?): String? = size("w500", path)
}

/** «4K · Dolby Vision», «1080p» — мітка якості для шапки. Порожньо — нічого показати. */
val CatalogItem.qualityLabel: String
    get() {
        val q = when {
            quality >= 2160 -> "4K"
            quality > 0 -> "${quality}p"
            else -> return ""
        }
        return if (hdr.isNotBlank()) "$q · ${cleanStreamLabel(hdr)}" else q
    }

/** Куди веде картка-двері (`opens` = «kind:key»): сторінка добірки. `null` — це звичайний твір. */
val CatalogItem.opensListing: Pair<CatalogListingKind, String>?
    get() {
        val (kind, key) = opens.split(":", limit = 2).let { it.first() to it.getOrElse(1) { "" } }
        val listing = CatalogListingKind.fromRoute(kind) ?: return null
        return if (key.isNotBlank()) listing to key else null
    }

/**
 * Кадр шапки сторінки (стрімінг, студія, жанр, бренд Реаліті) — не завжди перший
 * матеріал, а один із перших [POOL] з кадром, свій на кожну годину Зерно — сторінка й година: протягом години
 * повернення на сторінку не міняє шапку під ногами.
 */
fun rotatingPick(key: String, items: List<CatalogItem>, now: Long = System.currentTimeMillis()): CatalogItem? {
    val pool = items.filter { it.backdropPath != null }.distinctBy { it.id }.take(POOL)
    if (pool.isEmpty()) return null
    val hour = now / 3_600_000L
    return pool[kotlin.random.Random(key.hashCode().toLong() * 31 + hour).nextInt(pool.size)]
}

private const val POOL = 12
