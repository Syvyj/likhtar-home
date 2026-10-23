package com.likhtar.home.feature.cinema

import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.CatalogRepository
import com.likhtar.home.core.catalog.SectionBrand
import com.likhtar.home.core.catalog.SectionGenre
import com.likhtar.home.core.catalog.SectionRow
import com.likhtar.home.core.catalog.SectionRowsRepository
import com.likhtar.home.core.catalog.TitleMarksRevision
import com.likhtar.home.core.tv.RealityItem
import com.likhtar.home.core.tv.RealityRepository
import com.likhtar.home.core.tv.TeleportalClient
import javax.inject.Inject
import javax.inject.Singleton

/** Ряд розділу вже в тому вигляді, у якому його малює Кінозал. */
data class RealitySection(
    val title: String,
    val items: List<CatalogItem>,
    /**
     * Позиції ряду, у яких доріжка підтверджена. Їде разом із рядом, а не питається
     * вдруге з екрана: ряд щойно зібрано саме цим знанням.
     *
     * Тепер це ВСІ позиції ряду: сервер не пускає в ряд те, чого не можна ввімкнути.
     */
    val confirmed: Set<Int> = emptySet(),
    /**
     * Позиції, які тримаються на РОСІЙСЬКІЙ доріжці — української в них ми не знайшли.
     *
     * Окремо від [confirmed], бо на картці це різні мітки. Неігрове українською майже не
     * роздають, і розділ, який показував би лише українське, стояв би порожній; але
     * глядач має бачити, що вмикає, ще до натискання.
     */
    val russian: Set<Int> = emptySet(),
    /** Великий банер на всю ширину з однією позицією, як широкі секції Кінозалу. */
    val wide: Boolean = false,
    /** Своя сторінка ряду (`open_brand` сервера): «Для вас», тема, мережа. */
    val openBrand: String? = null,
)

/**
 * Наповнення «Реаліті»: ряди збирає СЕРВЕР, клієнт додає те, чого сервер не знає.
 *
 * Досі ряди будувались тут: одинадцять запитів у TMDB discover на кожну перезбірку плюс
 * відсів по озвечці.  із двадцяти позицій ряду «Реаліті-шоу» придатних
 * було нуль: TMDB не має відповіді на питання «що з цього можна ввімкнути тут», тож ми
 * платили запитами за вибірку, яку самі ж викидали.
 *
 * Сервер знає всі чотири джерела одразу — свої роздачі, портали мовників, ютуб і
 * сервіси, — і віддає ряд уже придатним. Клієнту лишається рівно те, чого сервер знати не
 * може: обкладинки й сторінки порталів (вони живуть у їхніх власних ручках) і те, що
 * встановлено на цій приставці.
 */
@Singleton
class RealityRows @Inject constructor(
    private val reality: RealityRepository,
    private val catalog: CatalogRepository,
    private val teleportal: TeleportalClient,
    private val sectionRows: SectionRowsRepository,
) {

    /** Велика обкладинка проєкту за id позиції. `null` — ще не питали або її немає. */
    private val heroArt = mutableMapOf<String, String?>()

    /** Позиція українського пласта за id картки; для решти — `null`. */
    private val ukrainian = mutableMapOf<Int, RealityItem>()

    /** Чим відкривати картку: `tmdb` | `portal` | `youtube` | `service`. */
    private val openBy = mutableMapOf<Int, String>()

    /** Ключ порталу позиції: сторінку шоу можна відкрити й без запису в каталозі порталів. */
    private val extBy = mutableMapOf<Int, String>()

    @Volatile
    private var cached: List<RealitySection>? = null

    @Volatile
    private var cachedAt = 0L

    /**
     * Розкладка розділу з терміном життя, а не на весь процес.
     *
     * Кеш «до перезапуску лаунчера» для розділу, який показує свіже, — це окрема поломка:
     * приставка живе тижнями, і новий випуск не з'явився б, доки людина не перезавантажить
     * застосунок.
     */
    suspend fun rows(): List<RealitySection> {
        val ttl = if (reality.stale) STALE_CACHE_MS else CACHE_MS
        cached?.takeIf { System.currentTimeMillis() - cachedAt < ttl && cachedAt >= TitleMarksRevision.changedAt }?.let { return it }
        return build().also {
            if (it.isNotEmpty()) {
                cached = it
                cachedAt = System.currentTimeMillis()
            }
        }
    }

    /**
     * Чи це позиція порталу — і яка саме.
     *
     * `null` означає, що картку відкриває не сторінка шоу, і саме за цією відповіддю екран
     * вибирає дорогу.
     */
    fun ukrainianOf(card: CatalogItem): RealityItem? = ukrainian[card.tmdbId]

    /**
     * Сторінка шоу за ключем порталу — для позицій, яких немає в каталозі порталів
     * (архівне «Він готує, вона керує» ТЕТу): сервер їх знає, а клієнтський зріз ні.
     */
    private fun portalPageOf(card: CatalogItem): String? =
        extBy[card.tmdbId]?.let { RealityTitleViewModel.idOfExtKey(it) }

    /** Чим відкривати картку — відповідь сервера, а не здогад за знаком id. */
    private fun openKindOf(card: CatalogItem): String = openBy[card.tmdbId] ?: "tmdb"

    /**
     * Куди веде картка Реаліті — одна відповідь для ряду, сторінки бренду й сітки «Відкрити
     * всі». Трьома копіями ці дороги вже розійшлись: сітка губила позиції порталу.
     * Наша позиція чи портал — власна сторінка шоу, TMDB — сторінка матеріалу Кінозалу,
     * решта (ютуб, сервіс) вмикається там, де лежить.
     */
    fun open(
        card: CatalogItem,
        onShow: (String) -> Unit,
        onTitle: (CatalogItem) -> Unit,
        external: (CatalogItem) -> Unit,
    ) {
        val kind = openKindOf(card)
        val page = ukrainianOf(card)?.id
            ?: card.showId.ifBlank { null }
            ?: portalPageOf(card)?.takeIf { kind == "portal" }
        when {
            page != null -> onShow(page)
            kind == "tmdb" -> onTitle(card)
            else -> external(card)
        }
    }

    /** Плитки внизу розділу: сервіси й мовники, чиє це насправді. */
    suspend fun brands(): List<SectionBrand> =
        runCatching { sectionRows.realityBrands() }.getOrDefault(emptyList())

    /** «Жанри» розділу — тією ж відповіддю, що й ряди. */
    suspend fun genres(): List<SectionGenre> =
        runCatching { sectionRows.realityGenres() }.getOrDefault(emptyList())

    /**
     * Сторінка плитки.
     *
     * Проходить тим самим збагаченням, що й ряди: інакше позиції порталів на сторінці
     * мовника лишились би без кадрів і без дороги на власну сторінку шоу.
     */
    /**
     * Сторінка плитки рядами — як стрімінг у Кінозалі: «Нове», жанри,
     * «Документальне», «Шоу». Порожньо — сервер не відповів.
     */
    suspend fun brandSections(key: String): Pair<String, List<RealitySection>> {
        val rows = runCatching { sectionRows.brandRows(key) }.getOrDefault(emptyList())
        if (rows.isEmpty()) return "" to emptyList()
        val index = portalIndex()
        val title = sectionRows.genreTitle(key) ?: runCatching { brands() }.getOrDefault(emptyList())
            .firstOrNull { it.key == key }?.title ?: rows.first().title
        return title to rows.map { enrich(it, index) }.filter { it.items.isNotEmpty() }
    }

    suspend fun brandRow(key: String): RealitySection? {
        val rows = runCatching { sectionRows.brandRows(key) }.getOrDefault(emptyList())
        if (rows.isEmpty()) return null
        val index = portalIndex()
        // Кілька підбірок зводяться в одну сітку: сторінка мережі — це «все її», а не ще
        // один екран із рядами. Дублікати між підбірками прибираємо тут же.
        val merged = rows.flatMap { enrich(it, index).items }.distinctBy { it.id }
        val title = runCatching { brands() }.getOrDefault(emptyList())
            .firstOrNull { it.key == key }?.title ?: rows.first().title
        return RealitySection(
            title = title,
            items = merged,
            confirmed = rows.flatMap { it.ukrainian }.toSet(),
            russian = rows.flatMap { it.russian }.toSet(),
        )
    }

    /**
     * Кадр проєкту за його id — для ряду «Продовжити».
     *
     * Сервер у рядку прогресу зображень не віддає (йому нема звідки), а картка без кадру
     * читається як недомальована. Беремо те, що вже лежить у розкладці розділу.
     */
    fun artOf(itemId: String): String? = ukrainian.values
        .firstOrNull { it.id == itemId }
        ?.bestFrame

    /** Логотип назви з TMDB для однієї картки — на фокусі, не наперед. */
    suspend fun logoOf(card: CatalogItem): String? =
        // У позиції поза TMDB логотипа немає за побудовою, і запит по відʼємному id
        // впирається в сторожу проксі.
        if (card.tmdbId <= 0) null else catalog.withLogo(card).logoPath

    suspend fun heroArtOf(card: CatalogItem): String? {
        val item = ukrainian[card.tmdbId] ?: return null
        val show = item.teleportal
        // У 1+1 обкладинки проєкту немає — у каталозі вже лежить найбільше, що є
        // (кадр 1920×830), тож окремий запит нічого б не додав.
        if (show == null) return null
        synchronized(heroArt) { if (heroArt.containsKey(item.id)) return heroArt[item.id] }
        // Невдачу (мережа, скасування) не кешуємо — інакше обкладинки не було б до
        // перезапуску; кешуємо лише справжню відповідь порталу, навіть без картинки.
        val project = com.likhtar.home.core.model.runCatchingCancellable { teleportal.project(show) }.getOrNull()
            ?: return null
        val art = listOf(project.logo, project.imageBig).firstOrNull { it.isNotBlank() }
        synchronized(heroArt) { heroArt[item.id] = art }
        return art
    }

    private suspend fun build(): List<RealitySection> {
        val server = runCatching { sectionRows.reality() }.getOrDefault(emptyList())
        if (server.isEmpty()) return emptyList()
        val byExtKey = portalIndex()
        ukrainian.clear()
        openBy.clear()
        extBy.clear()
        return server.map { row -> enrich(row, byExtKey) }
    }

    /**
     * Каталог порталів за ext-ключем.
     *
     * Потрібен НЕ заради вибірки — її зробив сервер, — а заради картинок і сторінки шоу:
     * сезони й випуски Телепорталу й 1+1 живуть у їхніх власних ручках, і сервер їх не
     * бачить.
     */
    private suspend fun portalIndex(): Map<String, RealityItem> =
        runCatching { reality.rows() }.getOrDefault(emptyList())
            .flatMap { it.items }
            .associateBy { RealityTitleViewModel.extKeyOf(it) }

    private fun enrich(row: SectionRow, byExtKey: Map<String, RealityItem>): RealitySection {
        val items = row.items.map { card ->
            openBy[card.tmdbId] = row.open[card.tmdbId] ?: "tmdb"
            row.extKeys[card.tmdbId]?.let { extBy[card.tmdbId] = it }
            val own = row.extKeys[card.tmdbId]?.let(byExtKey::get) ?: return@map card
            ukrainian[card.tmdbId] = own
            // Кадр порталу кращий за все, що має сервер: у нього чистий 1440×810, а в
            // каталозі для цієї позиції картинки немає взагалі.
            card.copy(
                backdropPath = own.bestFrame ?: card.backdropPath,
                posterPath = own.image.takeIf { it.isNotBlank() } ?: card.posterPath,
                overview = own.description.ifBlank { card.overview },
            )
        }
        return RealitySection(
            title = row.title,
            items = items,
            // Підтверджені — ті, що назвав сервер, а не весь ряд.
            //
            // Доти тут стояло «підтверджені всі», бо ряди збиралися лише з нашого
            // каталогу, куди без доріжки не заходять. Тепер у розділі є світові мережі:
            // Discovery й BBC приходять із TMDB, і українською там нічого немає. Мітка
            // «UA» на кожній їхній картці була б неправдою.
            confirmed = row.ukrainian,
            russian = row.russian,
            wide = row.wide != null,
            openBrand = row.openBrand,
        )
    }

    private companion object {
        /** Скільки живе зібрана розкладка. Далі розділ перезбирається сам. */
        const val CACHE_MS = 30 * 60 * 1000L

        /** Стільки живе розкладка, зібрана на збереженому зрізі сервера. */
        const val STALE_CACHE_MS = 2 * 60 * 1000L
    }
}
