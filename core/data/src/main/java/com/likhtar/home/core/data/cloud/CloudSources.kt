package com.likhtar.home.core.data.cloud

import com.likhtar.home.core.data.http
import com.likhtar.home.core.data.text
import org.json.JSONArray
import org.json.JSONObject

/*
 * Три публічні API, без входу (docs/cloud-games-design.md, «Звідки каталог»). Кожне джерело
 * тягнеться порціями й одразу зводиться до [SourceGame]: сирі відповіді важать ~24 МБ (Xbox —
 * ~68 КБ на гру), а на слабкій приставці їх не можна тримати в пам'яті цілком.
 */

private const val TIMEOUT_MS = 20_000
private val JSON_HEADERS = mapOf("Accept" to "application/json", "User-Agent" to "Mozilla/5.0")

private fun get(url: String): String = http(url, TIMEOUT_MS, headers = JSON_HEADERS) { it.text() }

// ── Boosteroid ────────────────────────────────────────────────────────────────

private const val BOOST = "https://cloud.boosteroid.com/api/v1/public/applications"

/** Library (1) — готові до гри; Install (5) — ставляться на віртуальну машину. */
private val BOOST_COLLECTIONS = listOf(1 to false, 5 to true)

private val BOOST_STORES = mapOf(
    "Steam" to "Steam", "Epic Games Store" to "Epic", "Xbox" to "Xbox", "Ubisoft Connect" to "Ubisoft", "Battle.net" to "Battle.net",
    "EA" to "EA", "Wargaming.net" to "Wargaming", "Rockstar Games" to "Rockstar", "GOG" to "GOG",
)
private val REGION_SUFFIX = Regex("""\((na|asia|us)\)\s*$""", RegexOption.IGNORE_CASE)
private val YOUTUBE_ID = Regex("""(?:v=|youtu\.be/|embed/)([\w-]{11})""")
private val STEAM_APP = Regex("""/app/(\d+)""")
private val URL_IN_TEXT = Regex("""https?://[^'"\s,\]]+""")

/** Довідник `[{id, name}]` — магазини чи жанри Boosteroid. */
fun parseBoostNames(json: String): Map<Int, String> {
    val a = JSONArray(json)
    return (0 until a.length()).associate { a.getJSONObject(it).let { o -> o.getInt("id") to o.optString("name") } }
}

/** Сторінка каталогу Boosteroid: ігри й номер останньої сторінки. */
fun parseBoostPage(json: String, platforms: Map<Int, String>, genres: Map<Int, String>, install: Boolean): Pair<List<SourceGame>, Int> {
    val root = JSONObject(json)
    val data = root.getJSONArray("data")
    val games = (0 until data.length()).mapNotNull { i ->
        val x = data.getJSONObject(i)
        val name = x.optString("name")
        if (x.optBoolean("unavailable") || REGION_SUFFIX.containsMatchIn(name)) return@mapNotNull null
        val stores = x.optJSONArray("platform").ints().mapNotNull { BOOST_STORES[platforms[it]] }
        val trailers = x.optJSONArray("trailers")
        val trailer = (0 until (trailers?.length() ?: 0)).firstNotNullOfOrNull { t ->
            YOUTUBE_ID.find(trailers!!.optJSONObject(t)?.optString("url").orEmpty())?.groupValues?.get(1)
        }.orEmpty()
        // «images» Boosteroid — рядок виду "['https://…', '…']", а не масив; буває й масивом.
        val shots = when (val im = x.opt("images")) {
            is JSONArray -> im.strings()
            is String -> URL_IN_TEXT.findAll(im).map { it.value }.toList()
            else -> emptyList()
        }
        SourceGame(
            service = CloudService.BOOSTEROID,
            title = name,
            launch = listOf(CloudLaunch(x.getInt("id").toString(), store = stores.firstOrNull().orEmpty(), install = install)),
            stores = stores.toSet(),
            genres = x.optJSONArray("genre").ints().mapNotNull { genres[it]?.let(CloudGenre::genreKey) }.toSet(),
            free = x.optString("monetizeType") == "free",
            controller = x.optBoolean("controller"),
            desc = cleanDesc(x.str("description")),
            dev = x.str("developer"),
            pub = x.str("publisher"),
            year = x.str("releaseDate").take(10),
            shots = shots,
            cover = x.str("libraryImageUrl").ifEmpty { null },
            hero = x.str("bannerImage").ifEmpty { null },
            steamId = x.optJSONObject("stores")?.let { STEAM_APP.find(it.optString("steam"))?.groupValues?.get(1) },
            popularity = x.optInt("popularity"),
            trailer = trailer,
        )
    }
    return games to root.optJSONObject("meta")?.optInt("last_page", 1).let { it ?: 1 }
}

fun fetchBoosteroid(): List<SourceGame> {
    val platforms = parseBoostNames(get("$BOOST/platforms"))
    val genres = parseBoostNames(get("$BOOST/genres"))
    val out = ArrayList<SourceGame>()
    for ((collection, install) in BOOST_COLLECTIONS) {
        var page = 0
        var last = 1
        while (page < last) {
            page += 1
            val (games, lastPage) = parseBoostPage(get("$BOOST?collection=$collection&page=$page"), platforms, genres, install)
            out += games
            last = lastPage
        }
    }
    return out
}

// ── GeForce NOW ───────────────────────────────────────────────────────────────

private const val GFN = "https://games.geforce.com/graphql"
private val GFN_STORES = mapOf(
    "STEAM" to "Steam", "EPIC" to "Epic", "XBOX" to "Xbox", "UPLAY" to "Ubisoft", "BATTLENET" to "Battle.net",
    "EA_APP" to "EA", "GOG" to "GOG", "GAIJIN" to "Gaijin",
)
private val LOCAL_COOP = setOf(
    "local co-op", "co-op local", "split screen", "splitscreen", "shared screen", "shared split screen", "4 player local",
    "local multiplayer", "écran partagé", "coop locale", "co-op locale", "cooperativo local", "multijugador local", "multijoueur local",
)
private val ONLINE_COOP = setOf("co-op", "coop", "online co-op", "co-op online", "cooperativo")

/** Сторінка каталогу GeForce NOW: ігри й курсор наступної (`null` — остання). */
fun parseGfnPage(json: String): Pair<List<SourceGame>, String?> {
    val apps = JSONObject(json).getJSONObject("data").getJSONObject("apps")
    val items = apps.getJSONArray("items")
    val games = (0 until items.length()).mapNotNull { i ->
        val x = items.getJSONObject(i)
        val variants = x.optJSONArray("variants")
        val launch = (0 until (variants?.length() ?: 0)).mapNotNull { v ->
            val o = variants!!.getJSONObject(v)
            val short = o.str("shortName").ifEmpty { return@mapNotNull null }
            CloudLaunch(o.str("id"), short, GFN_STORES[o.str("appStore")].orEmpty())
        }
        if (launch.isEmpty()) return@mapNotNull null
        val genreNames = x.optJSONArray("genres").strings()
        val keywords = x.optJSONArray("keywords").strings().map { it.lowercase() }.toSet()
        val controls = x.optJSONArray("supportedControls").strings().toSet()
        val images = x.optJSONObject("images")
        SourceGame(
            service = CloudService.GEFORCE_NOW,
            title = x.optString("title"),
            launch = launch,
            stores = launch.map { it.store }.filter { it.isNotEmpty() }.toSet(),
            genres = genreNames.mapNotNull(CloudGenre::genreKey).toSet(),
            free = "FREE_TO_PLAY" in genreNames,
            coop = (if (keywords.any { it in LOCAL_COOP }) setOf('l', 'c') else emptySet<Char>()) +
                (if (keywords.any { it in ONLINE_COOP }) setOf('c') else emptySet()),
            pad = when {
                "GAMEPAD" in controls -> "full"
                "GAMEPAD_PARTIAL" in controls -> "part"
                controls.isNotEmpty() -> "none"
                else -> ""
            },
            desc = cleanDesc(x.str("shortDescription")),
            dev = x.str("developerName"),
            pub = x.str("publisherName"),
            shots = images?.optJSONArray("SCREENSHOTS").strings(),
            cover = images?.str("GAME_BOX_ART")?.ifEmpty { null },
            hero = (images?.str("HERO_IMAGE")?.ifEmpty { null }) ?: images?.str("TV_BANNER")?.ifEmpty { null },
        )
    }
    val info = apps.optJSONObject("pageInfo")
    val next = info?.takeIf { it.optBoolean("hasNextPage") }?.str("endCursor")?.ifEmpty { null }
    return games to next
}

/** [vpc] — регіон GeForce NOW: від нього залежать cmsId (європейські й американські різні). */
fun fetchGeForceNow(vpc: String = GFN_EUROPE): List<SourceGame> {
    val out = ArrayList<SourceGame>()
    var cursor: String? = null
    do {
        val after = cursor?.let { """, after: "$it"""" }.orEmpty()
        val query = """{ apps(vpcId:"$vpc", language:"en_US", first: 100$after) { pageInfo { hasNextPage endCursor } items { id title genres """ +
            """images { GAME_BOX_ART TV_BANNER HERO_IMAGE SCREENSHOTS } variants { id appStore shortName } keywords supportedControls """ +
            """shortDescription developerName publisherName } } }"""
        val body = JSONObject().put("query", query).toString()
        val json = http(GFN, TIMEOUT_MS, method = "POST", headers = mapOf("Content-Type" to "application/json", "Origin" to "https://play.geforcenow.com"), body = body) { it.text() }
        val (games, next) = parseGfnPage(json)
        out += games
        cursor = next
    } while (cursor != null)
    return out
}

/** Варшава: GeForce NOW для Польщі, України й решти Європи. */
const val GFN_EUROPE = "NP-WAW-01"

// ── Xbox Cloud ────────────────────────────────────────────────────────────────

private const val XBOX_SIGL = "https://catalog.gamepass.com/sigls/v2?id=29a81209-df6f-41fd-a528-2ae6b91f719c&language=en-us&market="
private const val XBOX_PRODUCTS = "https://displaycatalog.mp.microsoft.com/v7.0/products?languages=en-us&market="
private val NON_SLUG = Regex("[^a-z0-9]+")

/** Хмарний список Game Pass: перший елемент — заголовок без id. */
fun parseXboxIds(json: String): List<String> {
    val a = JSONArray(json)
    return (0 until a.length()).mapNotNull { a.getJSONObject(it).str("id").ifEmpty { null } }
}

fun parseXboxProducts(json: String): List<SourceGame> {
    val products = JSONObject(json).optJSONArray("Products") ?: return emptyList()
    return (0 until products.length()).mapNotNull { i ->
        val p = products.getJSONObject(i)
        val lp = p.optJSONArray("LocalizedProperties")?.optJSONObject(0) ?: return@mapNotNull null
        val title = lp.str("ProductTitle").ifEmpty { return@mapNotNull null }
        val images = lp.optJSONArray("Images")
        fun image(vararg purpose: String): String? = purpose.firstNotNullOfOrNull { want ->
            (0 until (images?.length() ?: 0)).map { images!!.getJSONObject(it) }.firstOrNull { it.str("ImagePurpose") == want }?.str("Uri")
        }?.let(::https)
        val props = p.optJSONObject("Properties")
        val attrs = props?.optJSONArray("Attributes")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).str("Name") } }.orEmpty()
        val category = props?.optJSONArray("Categories").strings().firstOrNull() ?: props?.str("Category").orEmpty()
        SourceGame(
            service = CloudService.XBOX,
            title = title,
            launch = listOf(CloudLaunch(p.str("ProductId"), title.lowercase().replace(NON_SLUG, "-").trim('-'))),
            genres = setOfNotNull(CloudGenre.genreKey(category)),
            coop = (if (attrs.any { "Coop" in it }) setOf('c') else emptySet<Char>()) +
                (if (attrs.any { "LocalCoop" in it || "SplitScreen" in it }) setOf('l') else emptySet()),
            desc = cleanDesc(lp.str("ShortDescription")),
            dev = lp.str("DeveloperName"),
            pub = lp.str("PublisherName"),
            year = p.optJSONArray("MarketProperties")?.optJSONObject(0)?.str("OriginalReleaseDate").orEmpty().take(10),
            shots = (0 until (images?.length() ?: 0)).map { images!!.getJSONObject(it) }
                .filter { it.str("ImagePurpose") == "Screenshot" }.map { https(it.str("Uri")) }.take(8),
            cover = image("Poster", "BoxArt"),
            hero = image("SuperHeroArt", "TitledHeroArt"),
        )
    }
}

/** [market] — країна магазину Microsoft (каталог і ціни різні по ринках). */
fun fetchXbox(market: String = "PL"): List<SourceGame> {
    val ids = parseXboxIds(get(XBOX_SIGL + market))
    // По 20 за запит: Microsoft віддає ~68 КБ на продукт, більше — важка відповідь для приставки.
    return ids.chunked(20).flatMap { chunk -> parseXboxProducts(get("${XBOX_PRODUCTS}$market&bigIds=${chunk.joinToString(",")}")) }
}

// ── Спільне ───────────────────────────────────────────────────────────────────

private fun https(uri: String) = if (uri.startsWith("//")) "https:$uri" else uri

private fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name)

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { if (isNull(it)) null else optString(it).ifEmpty { null } }

private fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { getInt(it) }

private val TAG = Regex("<[^>]+>")
private val SPACES = Regex("\\s+")

/** Опис без HTML і сутностей, до 420 знаків — як у merge.py. */
internal fun cleanDesc(s: String): String = s.replace(TAG, " ")
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("\\", "")
    .replace(SPACES, " ").trim().take(420)

// ── Файл джерела ──────────────────────────────────────────────────────────────

/** Знімок джерела у файл: компактний JSON без імен полів-повторів API. */
fun writeSourceGames(games: List<SourceGame>): String {
    val a = JSONArray()
    for (g in games) {
        a.put(
            JSONObject()
                .put("s", g.service.code.toString()).put("t", g.title)
                .put("l", JSONArray().apply { g.launch.forEach { put(JSONArray().put(it.id).put(it.name).put(it.store).put(it.install)) } })
                .put("st", JSONArray(g.stores.toList())).put("ge", JSONArray(g.genres.toList())).put("f", g.free)
                .put("co", g.coop.joinToString("")).put("pad", g.pad).put("ctl", g.controller)
                .put("d", g.desc).put("dev", g.dev).put("pub", g.pub).put("y", g.year).put("sc", JSONArray(g.shots))
                .put("c", g.cover ?: JSONObject.NULL).put("h", g.hero ?: JSONObject.NULL).put("steam", g.steamId ?: JSONObject.NULL)
                .put("p", g.popularity).put("tr", g.trailer),
        )
    }
    return a.toString()
}

fun readSourceGames(json: String): List<SourceGame> {
    val a = JSONArray(json)
    return (0 until a.length()).mapNotNull { i ->
        val o = a.getJSONObject(i)
        val service = CloudService.of(o.str("s").firstOrNull() ?: return@mapNotNull null) ?: return@mapNotNull null
        val l = o.optJSONArray("l")
        SourceGame(
            service = service,
            title = o.str("t"),
            launch = (0 until (l?.length() ?: 0)).map { j ->
                val x = l!!.getJSONArray(j)
                CloudLaunch(x.optString(0), x.optString(1), x.optString(2), x.optBoolean(3))
            },
            stores = o.optJSONArray("st").strings().toSet(),
            genres = o.optJSONArray("ge").strings().toSet(),
            free = o.optBoolean("f"),
            coop = o.str("co").toSet(),
            pad = o.str("pad"),
            controller = o.optBoolean("ctl"),
            desc = o.str("d"),
            dev = o.str("dev"),
            pub = o.str("pub"),
            year = o.str("y"),
            shots = o.optJSONArray("sc").strings(),
            cover = o.str("c").ifEmpty { null },
            hero = o.str("h").ifEmpty { null },
            steamId = o.str("steam").ifEmpty { null },
            popularity = o.optInt("p"),
            trailer = o.str("tr"),
        )
    }
}
