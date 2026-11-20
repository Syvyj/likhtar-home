package com.likhtar.home.core.data.cloud

/**
 * Хмарні ігри: каталог Boosteroid, GeForce NOW і Xbox Cloud, зведений в одну картку на гру
 * (docs/cloud-games-design.md). Каталог збирає сама приставка з публічних API сервісів —
 * ні сервера, ні роздавання чужої бази в APK.
 */
enum class CloudService(val code: Char) {
    BOOSTEROID('b'), GEFORCE_NOW('g'), XBOX('x');

    companion object {
        fun of(code: Char): CloudService? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Чим сервіс запускає гру. Boosteroid: [id] — числовий id. GeForce NOW: [id] — cmsId варіанта,
 * [name] — shortName. Xbox: [id] — productId, [name] — slug. [store] — магазин варіанта
 * («Steam», «Epic»…), [install] — Boosteroid ставить гру на віртуальну машину (колекція Install).
 */
data class CloudLaunch(
    val id: String,
    val name: String = "",
    val store: String = "",
    val install: Boolean = false,
)

/** Гра так, як її віддав один сервіс, — уже без зайвих полів API. */
data class SourceGame(
    val service: CloudService,
    val title: String,
    val launch: List<CloudLaunch>,
    val stores: Set<String> = emptySet(),
    /** Ключі жанрів ([CloudGenre]), не підписи: підпис — мовою інтерфейсу. */
    val genres: Set<String> = emptySet(),
    val free: Boolean = false,
    /** 'c' — грають разом, 'l' — на одному екрані. */
    val coop: Set<Char> = emptySet(),
    /** «full» / «part» / «none» з GeForce NOW; порожньо — сервіс не сказав. */
    val pad: String = "",
    /** Boosteroid: гра підтримує контролер. */
    val controller: Boolean = false,
    val desc: String = "",
    val dev: String = "",
    val pub: String = "",
    val year: String = "",
    val shots: List<String> = emptyList(),
    val cover: String? = null,
    val hero: String? = null,
    val steamId: String? = null,
    val popularity: Int = 0,
    /** YouTube id трейлера (Boosteroid). */
    val trailer: String = "",
)

/** Одна гра — одна картка, скільки б сервісів її не стрімили. */
data class CloudGame(
    val key: String,
    val title: String,
    val sources: Map<CloudService, List<CloudLaunch>>,
    val stores: List<String>,
    val genres: List<String>,
    val free: Boolean,
    val coop: String,
    val popularity: Int,
    val pad: String,
    val desc: String,
    val dev: String,
    val pub: String,
    val year: String,
    val shots: List<String>,
    val trailer: String,
    val cover: String?,
    val cover2: String?,
    val hero: String?,
    /** Гра з образу на приставці (ретро): тоді [sources] порожні, запускає RetroArch. */
    val rom: CloudRom? = null,
)

/** Образ ретро-гри: [path] — файл, [system] — назва приставки; [missing] — файлу вже немає. */
data class CloudRom(val path: String, val system: String, val missing: Boolean = false)

/** Канонічні ключі жанрів; з API трьох сервісів — у [genreKey]. */
object CloudGenre {
    private val MAP = mapOf(
        // GeForce NOW
        "ACTION" to "action", "ADVENTURE" to "adventure", "ROLE_PLAYING" to "rpg", "STRATEGY" to "strategy",
        "SIMULATION" to "simulation", "FIRST_PERSON_SHOOTER" to "shooter", "RACING" to "racing", "SPORTS" to "sports",
        "PUZZLE" to "puzzle", "FIGHTING" to "fighting", "PLATFORMER" to "platformer", "MASSIVELY_MULTIPLAYER_ONLINE" to "mmo",
        "CASUAL" to "casual", "FAMILY" to "family", "INDIE" to "indie",
        // Boosteroid
        "Action" to "action", "Adventure" to "adventure", "Role-Playing" to "rpg", "Strategy" to "strategy",
        "Simulation" to "simulation", "Shooter" to "shooter", "Racing" to "racing", "Sports" to "sports",
        "Puzzle" to "puzzle", "Fighting" to "fighting", "Platformer" to "platformer", "MMO" to "mmo",
        "Horror" to "horror", "Casual" to "casual", "Indie" to "indie",
        // Xbox
        "Action & adventure" to "action", "Role playing" to "rpg", "Racing & flying" to "racing",
        "Family & kids" to "family", "Puzzle & trivia" to "puzzle", "Classics" to "classics",
    )

    fun genreKey(name: String): String? = MAP[name]
}

private val SYMBOLS = Regex("[™®©]")
private val STORE_SUFFIX = Regex(
    """\s*[(\[](steam|epic( games)?( store)?|xbox|ea|ea app|ubisoft( connect)?|battle\.net|gog|wargaming(\.net)?|rockstar( games)?|pc|windows|microsoft store)[)\]]\s*""",
    RegexOption.IGNORE_CASE,
)
private val EDITION_SUFFIX = Regex(
    """(\s*[-–:]?\s*\(?xbox (series x\|s|one)( edition| version)?\)?|\s*[-–:]?\s*(digital )?standard edition|\s*\((eu|europe)\)|\s*\(game preview\)|\s*[-–]\s*cross-gen (bundle|edition)|\s*\(no dlc\))$""",
    RegexOption.IGNORE_CASE,
)
private val JUNK = Regex("""([\s:–-]+(prologue |chainsaw )?demo|\s+playtest|\s*[-–]\s*open beta)$""", RegexOption.IGNORE_CASE)

/** Назва без ™/®, суфікса магазину й «видання». */
fun cleanTitle(title: String): String =
    title.replace(SYMBOLS, "").replace(STORE_SUFFIX, "").trim().replace(EDITION_SUFFIX, "").trim()

/** Ключ зведення: та сама гра в різних сервісах → той самий ключ. */
fun cloudKey(title: String): String =
    cleanTitle(title).lowercase().replace("&", "and").replace(Regex("[^a-z0-9]+"), "")

/** Демо, плейтести й відкриті бети — не окремі ігри. */
fun isJunkTitle(title: String): Boolean = JUNK.containsMatchIn(title.replace(SYMBOLS, "").trim())

private const val STEAM_ART = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps"

/** Ретельно зібрана гра, поки зводимо джерела. */
private class Acc(val title: String) {
    val sources = LinkedHashMap<CloudService, MutableList<CloudLaunch>>()
    val stores = sortedSetOf<String>()
    val genres = sortedSetOf<String>()
    var free = false
    val coop = sortedSetOf<Char>()
    var popularity = 0
    var pad = ""
    var desc = ""
    var dev = ""
    var pub = ""
    var year = ""
    var shots = emptyList<String>()
    var trailer = ""
    var steam: String? = null
    val covers = HashMap<CloudService, String>()
    val heroes = HashMap<CloudService, String>()

    /** Перше непорожнє виграє; гасло (< 60 знаків) поступається довшому опису. */
    fun info(s: SourceGame) {
        if (s.desc.isNotBlank() && desc.length < 60 && s.desc.length > desc.length) desc = s.desc
        if (dev.isBlank()) dev = s.dev.trim()
        if (pub.isBlank()) pub = s.pub.trim()
        // Xbox ставить 9998 як «невідомо».
        if (year.isBlank() && s.year.length >= 4 && s.year.take(4).all(Char::isDigit) && s.year.take(4) in "1971".."2027") year = s.year.take(4)
        if (shots.isEmpty() && s.shots.isNotEmpty()) shots = s.shots.distinct().take(8)
    }
}

/**
 * Зведення трьох сервісів. Порядок той самий, що в tools/cloud-catalog/merge.py: Boosteroid
 * дає джерела, GeForce NOW і Xbox — опис і рік першими, Boosteroid доповнює. Будь-який список
 * може бути порожнім (сервіс не відповів) — решта зводиться як є.
 */
fun mergeCloud(boost: List<SourceGame>, gfn: List<SourceGame>, xbox: List<SourceGame>): List<CloudGame> {
    val all = LinkedHashMap<String, Acc>()
    fun acc(title: String): Acc? {
        if (isJunkTitle(title)) return null
        val key = cloudKey(title).ifEmpty { return null }
        return all.getOrPut(key) { Acc(cleanTitle(title)) }
    }
    val boosted = ArrayList<Pair<Acc, SourceGame>>()
    for (s in boost) {
        val a = acc(s.title) ?: continue
        a.sources.getOrPut(CloudService.BOOSTEROID) { mutableListOf() } += s.launch
        a.stores += s.stores; a.genres += s.genres
        if (s.free) a.free = true
        if (a.steam == null) a.steam = s.steamId
        s.cover?.let { a.covers.putIfAbsent(CloudService.BOOSTEROID, it) }
        s.hero?.let { a.heroes.putIfAbsent(CloudService.BOOSTEROID, it) }
        a.popularity = maxOf(a.popularity, s.popularity)
        if (s.controller && a.pad.isEmpty()) a.pad = "b"
        boosted += a to s
    }
    for (s in gfn) {
        val a = acc(s.title) ?: continue
        if (s.launch.isEmpty()) continue
        a.sources[CloudService.GEFORCE_NOW] = s.launch.toMutableList()
        a.stores += s.stores; a.genres += s.genres
        if (s.free) a.free = true
        a.coop += s.coop
        if (s.pad.isNotEmpty()) a.pad = s.pad
        a.info(s)
        s.cover?.let { a.covers.putIfAbsent(CloudService.GEFORCE_NOW, it) }
        s.hero?.let { a.heroes.putIfAbsent(CloudService.GEFORCE_NOW, it) }
    }
    for (s in xbox) {
        val a = acc(s.title) ?: continue
        a.sources[CloudService.XBOX] = s.launch.toMutableList()
        a.stores += "Game Pass"; a.genres += s.genres
        a.coop += s.coop
        a.info(s)
        // Xbox Cloud — завжди з геймпадом.
        if (a.pad.isEmpty() || a.pad == "b") a.pad = "full"
        s.cover?.let { a.covers.putIfAbsent(CloudService.XBOX, it) }
        s.hero?.let { a.heroes.putIfAbsent(CloudService.XBOX, it) }
    }
    for ((a, s) in boosted) {
        if (a.trailer.isEmpty()) a.trailer = s.trailer
        a.info(s)
    }
    return all.entries.mapNotNull { (key, a) ->
        if (a.sources.isEmpty()) return@mapNotNull null
        val order = listOf(CloudService.XBOX, CloudService.GEFORCE_NOW, CloudService.BOOSTEROID)
        val cover = a.steam?.let { "$STEAM_ART/$it/library_600x900.jpg" } ?: order.firstNotNullOfOrNull { a.covers[it] }
        val hero = a.steam?.let { "$STEAM_ART/$it/library_hero.jpg" } ?: order.firstNotNullOfOrNull { a.heroes[it] }
        CloudGame(
            key = key,
            title = a.title,
            sources = a.sources.mapValues { it.value.toList() },
            stores = a.stores.toList(),
            genres = a.genres.toList(),
            free = a.free,
            coop = a.coop.joinToString(""),
            popularity = a.popularity,
            pad = if (a.pad == "b") "full" else a.pad,
            desc = a.desc,
            dev = a.dev,
            pub = a.pub,
            year = a.year,
            shots = a.shots,
            trailer = a.trailer,
            cover = cover,
            cover2 = order.mapNotNull { a.covers[it] }.firstOrNull { it != cover },
            hero = hero,
        )
    }.sortedBy { it.title.lowercase() }
}
