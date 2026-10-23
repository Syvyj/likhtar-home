package com.likhtar.home.core.tv

import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Одна позиція розділу «Реаліті»: наш набір або шоу з Телепорталу.
 *
 * [teleportal] заповнене лише для другого: саме через нього відкриваються сезони й випуски.
 * У наборів свій шлях — діплінк у застосунок, як у ТВ Ефірі.
 */
data class RealityItem(
    val id: String,
    val title: String,
    val genres: List<String>,
    val image: String,
    /** Банер 1440×550. Порожньо — лишається дрібний [image]. */
    val imageBig: String = "",
    /** Чистий кадр 1440×810 без промо-напису — саме він годиться картці 16:9. */
    val frame: String = "",
    val description: String = "",
    val packageName: String = "",
    val url: String = "",
    val teleportal: TeleportalItem? = null,
    /**
     * Проєкт 1+1. Заповнене замість [teleportal] для його позицій: сезони, випуски й
     * потік у них свої, хоч на екрані картка та сама.
     */
    val plus1: Plus1Item? = null,
    /** Дата останнього випуску, `2026-09-06`. Порожньо — набір або порожній сезон. */
    val lastEpisode: String = "",
) {
    /** Найкраща картинка для кадру 16:9: чистий кадр, банер, дрібне. `null` — жодної. */
    val bestFrame: String?
        get() = listOf(frame, imageBig, image).firstOrNull { it.isNotBlank() }
}

/** Пошук наших шоу за назвою — вузько, щоб пошук не тягнув за собою весь репозиторій. */
fun interface ShowSearch {
    suspend fun find(query: String): List<RealityItem>
}

/** Ряд розділу: назва жанру й позиції в ньому. */
data class RealityRow(val title: String, val items: List<RealityItem>)

/**
 * Наповнення розділу «Реаліті» з ДВОХ джерел, змішаних за жанром.
 *
 * Правило: фільми й серіали — у Кінозал, шоу й реаліті — сюди. Тому з Телепорталу
 * беремо лише `serial == false` (104 позиції зі 275), а серіали лишаємо Кінозалу.
 *
 * Набори з `channel-links.json` — не окреме джерело, а ті самі реаліті: у них проставлені
 * категорії («Справжні злочини» 14, «Реаліті» 13, «Авто та майстерні» 6, Discovery+ 6,
 * «Кулінарія» 2), і саме вони дають жанрову вісь розділу.
 *
 * Жанр і дату останнього випуску дає сервер: у самого Телепорталу немає ні того, ні того.
 * Жанрова таблиця там написана руками — автоматика по описах давала фальшиве покриття, бо
 * SEO-тексти забиті словами «шоу» й «зірки», і вікторина ставала «Талантами». Тому дивна
 * позиція в ряду — це рядок тієї таблиці, а не поломка тут.
 *
 * **Чого бракує:** прогресу по випусках. Поки його немає, рядка «Продовжити перегляд» у
 * розділі теж немає — сервер робить його окремою таблицею й зіллє у `/api/continue`.
 */
@Singleton
class RealityRepository @Inject constructor(
    private val links: ChannelLinksRepository,
    private val teleportal: TeleportalClient,
    private val plus1: Plus1Client,
) : ShowSearch {

    override suspend fun find(query: String): List<RealityItem> = find(query, SEARCH_LIMIT)

    /**
     * Розкладка розділу. Кешується на весь час життя процесу: по неї ходять три екрани —
     * сам розділ, сітка «Відкрити всі» й сторінка шоу, — і без кеша кожен перехід тягнув би
     * каталог Телепорталу заново.
     */
    /**
     * Чи зібраний каталог стоїть на збереженому зрізі сервера.
     *
     * Потрібне тому, хто кешує вище: тримати вчорашнє півгодини означало б чекати на
     * свіже довше, ніж сервер його збирає.
     */
    @Volatile
    var stale: Boolean = false
        private set

    suspend fun rows(): List<RealityRow> {
        fresh()?.let { return it }
        // Розділ, сторінка бренду й «Відкрити всі» просили каталог одночасно й збирали його
        // (увесь Телепортал і 1+1) кожен сам. Тепер другий чекає на
        // першого й бере готове.
        return buildLock.withLock {
            fresh() ?: build().also {
                if (it.isNotEmpty()) {
                    cached = it
                    cachedAt = System.currentTimeMillis()
                }
            }
        }
    }

    private val buildLock = kotlinx.coroutines.sync.Mutex()

    private fun fresh(): List<RealityRow>? {
        val ttl = if (stale) STALE_CACHE_MS else CACHE_MS
        return cached?.takeIf { System.currentTimeMillis() - cachedAt < ttl }
    }

    /** Одна позиція за її [RealityItem.id] — сторінка шоу починається саме з неї. */
    suspend fun item(id: String): RealityItem? {
        // Серіал порталу з Кінозалу в рядах Реаліті не лежить, тож збирати заради нього
        // весь каталог (а це Телепортал цілком) — лише чекати: сторінка висіла на
        // «Завантажую…», поки портал думав. Відкриваємо одразу за ключем.
        if (id.startsWith("serials/")) return portalItem(id)
        // Позиція сервісу: назву, опис і кадр сторінка бере з картки (PortalShowHints).
        if (id.startsWith("service/")) return RealityItem(id = id, title = "", genres = emptyList(), image = "")
        // Уже зібраний каталог — там є картинки й жанри. Не зібраний — не збираємо заради
        // однієї сторінки: це весь Телепортал, а він буває годинами недоступний.
        val cachedRows = cached?.takeIf { System.currentTimeMillis() - cachedAt < CACHE_MS }
        if (cachedRows == null && id.startsWith("plus1/")) return portalItem(id)
        return rows().firstNotNullOfOrNull { row ->
            row.items.firstOrNull { it.id == id }
        } ?: portalItem(id)
    }

    /**
     * Твір порталу поза рядами Реаліті — серіал Телепорталу чи 1+1 з українського каталогу
     * Кінозалу. Рядів у нього тут немає (Реаліті бере лише шоу), тож збираємо позицію з
     * самого ключа: сторінка шоу далі питає портал про опис, сезони й випуски сама.
     */
    private suspend fun portalItem(id: String): RealityItem? {
        val parts = id.split("/")
        return when {
            parts.size == 3 && (parts[0] == "serials" || parts[0] == "show") -> {
                // Назву й картинки дасть сторінка проєкту — каталог порталу тут не потрібен.
                val tp = TeleportalItem(
                    name = "", channelSlug = parts[1], projectSlug = parts[2], imagePath = "",
                    serial = parts[0] == "serials",
                )
                RealityItem(
                    id = id, title = tp.name, genres = tp.genres, image = tp.imagePath,
                    imageBig = tp.imageBigPath, frame = tp.framePath, teleportal = tp, lastEpisode = tp.lastEpisode,
                )
            }
            // Твір YouTube-книги: усе про нього сторінка бере з картки й плейлиста.
            parts.size == 2 && parts[0] == "youtube" ->
                RealityItem(id = id, title = "", genres = emptyList(), image = "")
            parts.size == 2 && parts[0] == "plus1" -> {
                val p1 = Plus1Item(id = parts[1], name = "", genre = "", imagePath = "")
                RealityItem(id = id, title = "", genres = emptyList(), image = "", plus1 = p1)
            }
            else -> null
        }
    }

    suspend fun row(title: String): RealityRow? = rows().firstOrNull { it.title == title }

    /**
     * Наші шоу, чия назва містить запит — для наскрізного пошуку.
     *
     * Портальні позиції TMDB-id не мають за природою, тому в пошуку по TMDB їх не знайти
     * НІКОЛИ, хоч у рядах «Реаліті» вони є. Це не про якість матчингу, це про те, що
     * половини каталогу в пошуку просто не було.
     */
    suspend fun find(query: String, limit: Int = SEARCH_LIMIT): List<RealityItem> =
        matches(rows(), query, limit)

    private suspend fun build(): List<RealityRow> {
        val sets = links.links()
            .filter { it.variants.isNotEmpty() }
            .map { link ->
                RealityItem(
                    id = link.name,
                    title = link.name,
                    // Категорії наших наборів — це наш власний словник, а решта системи
                    // говорить словником TMDB (ним тепер говорить і сервер). Зводимо тут,
                    // щоб далі в жодному місці не було двох словників одного поняття.
                    genres = listOf(OUR_CATEGORIES[link.category] ?: link.category.ifBlank { OTHER }),
                    image = link.image.ifBlank { link.logo },
                    description = link.variants.firstOrNull()?.description.orEmpty(),
                    packageName = link.packageName,
                    url = link.url,
                )
            }

        val shows = teleportal.catalogue()
            .filterNot { it.serial }
            .map { item ->
                RealityItem(
                    id = item.id,
                    title = item.name,
                    genres = item.genres.ifEmpty { listOf(OTHER) },
                    image = item.imagePath,
                    imageBig = item.imageBigPath,
                    frame = item.framePath,
                    teleportal = item,
                    lastEpisode = item.lastEpisode,
                )
            }

        // 1+1 video — третє джерело українського пласта, і дублів із Телепорталом у ньому
        // немає за побудовою: це різні мовники (СТБ, Новий, ICTV проти 1+1, 2+2, ТЕТ), і
        // жодна назва не збігається. Тому зливати за назвою не треба — сервер це перевірив.
        val plus1Raw = runCatching { plus1.catalogue() }.getOrDefault(emptyList())
        stale = teleportal.catalogueStale || plus1.catalogueStale
        val plus1Shows = plus1Raw
            .map { item ->
                RealityItem(
                    id = "plus1/${item.id}",
                    title = item.name,
                    // Жанр тут дає саме джерело, тож таблиці немає: «Реаліті» й
                    // «Розважальні шоу» — це те, як 1+1 сам називає свої проєкти.
                    genres = listOf(PLUS1_GENRES[item.genre] ?: item.genre),
                    image = item.imagePath,
                    imageBig = item.imageBigPath,
                    frame = item.framePath,
                    plus1 = item,
                    lastEpisode = item.lastEpisode,
                )
            }

        // Свіже зверху — усюди, одним правилом. Дату дає сервер (`last_episode`), і саме
        // вона впорядковує і ряд, і пул слайдера: розділ відкривають заради нового випуску.
        // Набори дати не мають за природою, тож осідають у хвіст свого ряду.
        val byDate = compareByDescending<RealityItem> { it.lastEpisode }

        // Один жанр — один ряд, незалежно від джерела: «Кулінарія» Телепорталу й
        // «Кулінарія» наших наборів — це той самий ряд, а не два однойменні.
        val byGenre = (shows + plus1Shows + sets)
            .flatMap { item -> item.genres.map { genre -> genre to item } }
            .groupBy({ it.first }, { it.second })

        // Ряд із двох карток виглядає як недомальований, тому дрібні жанри зсипаються в
        // «Інше». Поріг саме тут: три картки вже читаються як ряд.
        val (full, thin) = byGenre.entries.partition { it.value.size >= MIN_ROW }
        val other = thin.flatMap { it.value }.distinctBy { it.id } + byGenre[OTHER].orEmpty()

        return buildList {
            // Ряд суто українського — усе, що робить український ефір, одним місцем.
            // «Суто українське» — і Телепортал, і 1+1: розділ не ділиться за джерелом.
            val ukrainian = (shows + plus1Shows).sortedWith(byDate)
            if (ukrainian.isNotEmpty()) add(RealityRow(UKRAINIAN_SHOWS, ukrainian))
            full.asSequence()
                .filterNot { it.key == OTHER }
                .sortedByDescending { it.value.size }
                .forEach { (genre, items) ->
                    add(RealityRow(genre, items.distinctBy { it.id }.sortedWith(byDate)))
                }
            other.distinctBy { it.id }
                .takeIf { it.isNotEmpty() }
                ?.let { add(RealityRow(OTHER, it.sortedWith(byDate))) }
        }
    }

    @Volatile
    private var cached: List<RealityRow>? = null

    @Volatile
    private var cachedAt = 0L

    private companion object {
        const val UKRAINIAN_SHOWS = "Суто українське"

        /** Скільки наших шоу показуємо в пошуку: далі сітку заповнює TMDB. */
        const val SEARCH_LIMIT = 12
        const val OTHER = "Інше"

        /** Менше — це вже не ряд, а залишок: такі жанри йдуть в «Інше». */
        const val MIN_ROW = 3

        /**
         * Скільки живе зібраний каталог.
         *
         * Кеш на весь процес тут був тією ж поломкою, що в розкладці розділу: приставка
         * живе тижнями, і новий випуск Телепорталу не з'явився б до перезапуску лаунчера.
         */
        const val CACHE_MS = 30 * 60 * 1000L

        /**
         * Кеш на зріз — коротко: фоновий обхід сервера триває три-чотири хвилини, і
         * тримати вчорашнє довше за це означає ховати свіже без причини.
         */
        const val STALE_CACHE_MS = 2 * 60 * 1000L

        /**
         * Категорії `channel-links.json` → словник TMDB.
         *
         * Таблиця маленька й наша власна: ці категорії писали ми, тож і зводити їх нікому,
         * крім нас. «Авто та майстерні» лишається як є — відповідника в TMDB немає.
         */
        /**
         * Жанри 1+1 → словник, яким говорить решта розділу.
         *
         * «Реаліті» в них — це наше «Реаліті-шоу»; «Розважальні шоу» найближче до нього ж,
         * бо окремого ряду під вар'єте в таксономії немає, а вигадувати його під два
         * десятки позицій означало б ряд із трьох карток.
         */
        val PLUS1_GENRES = mapOf(
            "Реаліті" to "Реаліті-шоу",
            "Розважальні шоу" to "Реаліті-шоу",
        )

        val OUR_CATEGORIES = mapOf(
            "Справжні злочини" to "Кримінал",
            "Реаліті" to "Реаліті-шоу",
            "Discovery+" to "Документальний",
            "Кулінарія" to "Кулінарія",
        )
    }
}

/**
 * Збіг назви з запитом — окремою функцією, щоб її можна було перевірити тестом.
 *
 * Спершу ті, що з запиту починаються, потім ті, де він усередині: на «мас» людина шукає
 * «МастерШеф», а не «Хто зверху». Одна позиція лежить у кількох рядах розділу, тому
 * дублікати знімаємо за id.
 */
internal fun matches(rows: List<RealityRow>, query: String, limit: Int): List<RealityItem> {
    val needle = query.trim().lowercase()
    if (needle.length < 2) return emptyList()
    val hits = rows.asSequence()
        .flatMap { it.items.asSequence() }
        .distinctBy { it.id }
        .filter { it.title.lowercase().contains(needle) }
        .toList()
    return hits.sortedBy { if (it.title.lowercase().startsWith(needle)) 0 else 1 }.take(limit)
}
