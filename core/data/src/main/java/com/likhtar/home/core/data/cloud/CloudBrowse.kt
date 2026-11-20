package com.likhtar.home.core.data.cloud

/*
 * Як показувати й запускати хмарні ігри — логіка з макета docs/prototypes/cloud-games-mock.html,
 * окремо від Compose, щоб її можна було перевірити тестами.
 */

private val ORDER = listOf(CloudService.BOOSTEROID, CloudService.GEFORCE_NOW, CloudService.XBOX)

/** Сервіси, де гра є, у порядку переваги, лише з [enabled]. */
fun CloudGame.services(enabled: Set<CloudService> = CloudService.entries.toSet()): List<CloudService> =
    ORDER.filter { it in enabled && !sources[it].isNullOrEmpty() }

/** Boosteroid «встановлення» ставить гру на віртуальну машину — це довше за звичайний запуск. */
private fun CloudGame.instant(service: CloudService) =
    service != CloudService.BOOSTEROID || sources[service].orEmpty().any { !it.install }

/** Головний сервіс — перший, де гра стартує одразу; Boosteroid-встановлення — лише якщо більше ніде. */
fun CloudGame.mainService(enabled: Set<CloudService> = CloudService.entries.toSet()): CloudService? =
    services(enabled).let { list -> list.firstOrNull { instant(it) } ?: list.firstOrNull() }

/** Варіант запуску в сервісі: у Boosteroid — спершу той, що без встановлення. */
fun CloudGame.mainLaunch(service: CloudService): CloudLaunch? =
    sources[service].orEmpty().let { list -> list.firstOrNull { !it.install } ?: list.firstOrNull() }

/**
 * Посилання запуску (перевірено на приставці). [app] — у застосунок сервісу,
 * [web] — запасний, коли застосунку немає. [clearTask] — Better xCloud читає посилання лише в
 * onCreate, тож відкритий застосунок треба перезапустити.
 */
data class CloudLink(val app: String, val web: String, val clearTask: Boolean = false)

fun cloudLink(service: CloudService, launch: CloudLaunch): CloudLink = when (service) {
    CloudService.BOOSTEROID -> CloudLink("stream://boosteroid.tv/play?id=${launch.id}", "https://cloud.boosteroid.com/application/${launch.id}")
    CloudService.GEFORCE_NOW -> CloudLink(
        "nvidia://deeplink?action=play-game&cms-id=${launch.id}",
        "https://play.geforcenow.com/mall/#/streamer?launchSource=GeForceNOW&cmsId=${launch.id}&shortName=${launch.name}&appLaunchMode=Default",
    )
    CloudService.XBOX -> CloudLink("bxc://play/${launch.name}/${launch.id}", "https://www.xbox.com/play/launch/${launch.name}/${launch.id}", clearTask = true)
}

/** Пакети застосунків сервісу на TV; Xbox — через Better xCloud. */
val CloudService.packages: List<String>
    get() = when (this) {
        CloudService.BOOSTEROID -> listOf("com.boosteroidtv.streaming", "com.boosteroid.streaming")
        CloudService.GEFORCE_NOW -> listOf("com.nvidia.geforcenow")
        CloudService.XBOX -> listOf("com.redphx.betterxc")
    }

/**
 * Фільтри каталогу. [mine] — лише мої (правило — [CloudLibrary.isMine]).
 * [only] — ключі ігор блоку, з якого відкрили «Відкрити всі».
 */
data class CloudFilter(
    val mine: Boolean = false,
    val free: Boolean = false,
    val coop: Boolean = false,
    val split: Boolean = false,
    val service: CloudService? = null,
    /** Лише ігри з образів. */
    val retro: Boolean = false,
    /** Лише образи цієї приставки ([CloudRom.system]). */
    val system: String? = null,
    val stores: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val query: String = "",
    val only: Set<String>? = null,
)

/** [mine] — правило «моєї» гри з бібліотеки ([CloudLibrary.isMine]); без неї — безкоштовні. */
fun CloudGame.passes(f: CloudFilter, mine: (CloudGame) -> Boolean = { it.free }): Boolean {
    val q = f.query.trim().lowercase()
    return (f.only == null || key in f.only) &&
        (!f.mine || mine(this)) &&
        (!f.free || free) &&
        (!f.coop || 'c' in coop) &&
        (!f.split || 'l' in coop) &&
        (!f.retro || rom != null) &&
        (f.system == null || rom?.system == f.system) &&
        (f.service == null || !sources[f.service].isNullOrEmpty()) &&
        (f.stores.isEmpty() || f.stores.any { it in stores }) &&
        (f.genres.isEmpty() || f.genres.any { it in genres }) &&
        (q.isEmpty() || q in title.lowercase())
}

/**
 * Блок сторінки каталогу. [key] — «popular», «split», «coop», «free», ключ жанру чи «other»;
 * порожній — уся сторінка одним списком (мало ігор).
 */
data class CloudBlock(val key: String, val games: List<CloudGame>, val popular: Boolean = false)

private const val FEW = 12
private const val SMALL_GENRE = 6

/**
 * Ряди-блоки, як головна Кінозалу: «Популярні» → теми (спліт-скрін, кооп онлайн, free-to-play) →
 * жанри від більших. Гра йде лише в перший жанр; жанри з < 6 ігор — в «Інше». Тема, що покриває
 * ≥ 90 % списку, нічого не додає й ховається.
 */
fun cloudBlocks(games: List<CloudGame>): List<CloudBlock> {
    if (games.isEmpty()) return emptyList()
    val byPopularity = games.sortedByDescending { it.popularity }
    if (games.size <= FEW) return listOf(CloudBlock("", byPopularity))
    val byGenre = LinkedHashMap<String, MutableList<CloudGame>>()
    for (g in byPopularity) byGenre.getOrPut(g.genres.firstOrNull() ?: "other") { mutableListOf() } += g
    // «Ретро» — свій блок, хай навіть кілька образів: це інша полиця, а не дрібний жанр.
    val (big, small) = byGenre.entries.partition { it.value.size >= SMALL_GENRE || it.key == "other" || it.key == "retro" }
    val other = big.firstOrNull { it.key == "other" }?.value ?: mutableListOf()
    other += small.flatMap { it.value }
    val genres = big.filter { it.key != "other" }.sortedByDescending { it.value.size }.map { CloudBlock(it.key, it.value) } +
        listOfNotNull(other.takeIf { it.isNotEmpty() }?.let { CloudBlock("other", it) })
    val themes = listOf(
        CloudBlock("split", byPopularity.filter { 'l' in it.coop }),
        CloudBlock("coop", byPopularity.filter { 'c' in it.coop && 'l' !in it.coop }),
        CloudBlock("free", byPopularity.filter { it.free }),
    ).filter { it.games.isNotEmpty() && it.games.size < games.size * 0.9 }
    return listOf(CloudBlock("popular", byPopularity, popular = true)) + themes + genres
}

/** Свій порядок і сховані — за ключем блоку (як cinemaRowOrder / hiddenCinemaRows у Кінозалі). */
fun arrangeBlocks(blocks: List<CloudBlock>, order: List<String>, hidden: Set<String>, withHidden: Boolean = false): List<CloudBlock> =
    blocks.withIndex()
        .sortedWith(compareBy({ order.indexOf(it.value.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.index }))
        .map { it.value }
        .filter { withHidden || it.key !in hidden }
