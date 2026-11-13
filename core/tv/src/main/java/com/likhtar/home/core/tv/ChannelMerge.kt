package com.likhtar.home.core.tv

import org.json.JSONArray
import org.json.JSONObject

/**
 * Правки людини до списку каналів ТБ Ефіру (редактор з телефона). Усе — за ключем каналу
 * ([channelKey] назви, після [joins]), а не за адресою: канал, у якого є Sweet.TV і рядок
 * плейлиста, — один канал із кількома джерелами.
 */
data class ChannelEdits(
    /** Ключі в порядку людини; яких тут немає — після них, у звичному порядку. */
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    /** Ключ → адреса джерела, яке відкривати, коли їх кілька. */
    val source: Map<String, String> = emptyMap(),
    /** Ключ → своя назва. */
    val names: Map<String, String> = emptyMap(),
    /** Ключ → ключ каналу, до якого його приклеєно («Новий» → «Новий канал»). */
    val joins: Map<String, String> = emptyMap(),
    /** Ключ → група (жанр), куди людина переставила канал; "" — «Інші канали». */
    val groups: Map<String, String> = emptyMap(),
    /** Порядок груп, заданий людиною (довге OK на групі → «Вище»/«Нижче»); решта — після, як ішли. */
    val groupOrder: List<String> = emptyList(),
    /** Сховані групи — з усім вмістом; повертає довге OK на «Усі канали». */
    val groupHidden: Set<String> = emptySet(),
    /** Група → своя назва. */
    val groupNames: Map<String, String> = emptyMap(),
) {
    fun keyOf(name: String): String = channelKey(name).let { joins[it] ?: it }

    fun encode(): String = JSONObject()
        .put("order", JSONArray(order))
        .put("hidden", JSONArray(hidden.toList()))
        .put("source", JSONObject(source))
        .put("names", JSONObject(names))
        .put("joins", JSONObject(joins))
        .put("groups", JSONObject(groups))
        .put("groupOrder", JSONArray(groupOrder))
        .put("groupHidden", JSONArray(groupHidden.toList()))
        .put("groupNames", JSONObject(groupNames))
        .toString()

    companion object {
        fun decode(raw: String?): ChannelEdits = runCatching {
            val o = JSONObject(raw.orEmpty())
            fun list(k: String) = o.optJSONArray(k)?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
            fun map(k: String) = o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } }.orEmpty()
            ChannelEdits(
                list("order"), list("hidden").toSet(), map("source"), map("names"), map("joins"), map("groups"),
                list("groupOrder"), list("groupHidden").toSet(), map("groupNames"),
            )
        }.getOrDefault(ChannelEdits())
    }
}

/**
 * Усі джерела кожного каналу: свої (плейлист, підписки) першими, далі каталог Sweet.TV /
 * MEGOGO. Жанр свого каналу береться з каталогу за назвою, щоб «Discovery» з MEGOGO стояв у
 * тому ж ряду, що й «Viasat Nature» зі Sweet.TV.
 */
fun channelSources(own: List<ChannelLink>, tv: TvCatalog, edits: ChannelEdits = ChannelEdits()): Map<String, List<ChannelLink>> {
    val byName = tv.channels.associateBy { channelKey(it.link.name) }
    val mine = own.map { link ->
        val c = byName[channelKey(link.name)]?.link
        link.copy(category = c?.category ?: ownCategory(link), mark = link.mark.ifBlank { c?.mark.orEmpty() })
    }
    val ownUrls = own.map { it.url }.toSet()
    return (mine + tv.channels.map { it.link }.filter { it.url !in ownUrls })
        .distinctBy { it.url }
        .groupBy { edits.keyOf(it.name) }
}

/**
 * Один канал — одне джерело. Без вибору людини: HD перемагає, далі — своє перед каталогом
 * (свій «Nat Geo Wild» без HD поступається «Nat Geo Wild HD» зі Sweet.TV). Повертає канали й
 * ті з каталогу, що лишились (у них своя програма).
 */
fun mergeChannels(own: List<ChannelLink>, tv: TvCatalog, edits: ChannelEdits = ChannelEdits()): Pair<List<ChannelLink>, List<CatalogChannel>> {
    val kept = channelSources(own, tv, edits)
        .filterKeys { it !in edits.hidden }
        .map { (key, links) ->
            val pick = links.firstOrNull { it.url == edits.source[key] } ?: autoPick(links)
            pick.copy(key = key, name = edits.names[key] ?: pick.name, category = edits.groups[key] ?: pick.category)
        }
    val keptUrls = kept.map { it.url }.toSet()
    return kept to tv.channels.filter { it.link.url in keptUrls }
}

/**
 * Запасні адреси свого каналу: той самий канал (за ключем злиття) з інших своїх джерел — плейлиста,
 * порталу, Xtream. Плеєр перемикається на них, коли основна адреса не відкривається.
 */
fun channelBackups(own: List<ChannelLink>, tv: TvCatalog, edits: ChannelEdits = ChannelEdits()): Map<String, List<String>> =
    channelSources(own, tv, edits).values.flatMap { links ->
        val streams = links.filter { it.packageName.isBlank() }.map { it.url }.distinct()
        if (streams.size < 2) emptyList() else streams.map { u -> u to streams.filter { it != u } }
    }.toMap()

/** Джерело, яке бере автомат: перше HD, інакше перше (свої стоять перед каталогом). */
internal fun autoPick(links: List<ChannelLink>): ChannelLink = links.firstOrNull { isHD(it.name) } ?: links.first()

private fun isHD(name: String) = HDSuffix.containsMatchIn(name)

private val HDSuffix = Regex("""\s(HD|FHD|UHD|4K)$""", RegexOption.IGNORE_CASE)

/** Ключ для зіставлення каналів за назвою: без «HD», регістру й розділових знаків. */
fun channelKey(name: String): String =
    channelLabel(name).lowercase().filter { it.isLetterOrDigit() }

/** Назва каналу без «HD»: на картці поруч із часом вона лише займає місце. */
fun channelLabel(name: String): String =
    name.replace(Regex("""\s+(HD|FHD|UHD|4K)$""", RegexOption.IGNORE_CASE), "").trim()

/** Жанр свого каналу, якого немає в каталозі: наші рядки з файлу → жанри каталогу. */
private fun ownCategory(link: ChannelLink): String = when (link.category) {
    "Українське ТБ" -> "Українські"
    "Кулінарія" -> "Подорожі й кухня"
    "Пізнавальні та тревел" ->
        if (TravelName.containsMatchIn(link.name)) "Подорожі й кухня" else "Природа, наука й історія"
    else -> link.category
}

private val TravelName = Regex("travel|орел|utravel|мандри|подорож|пригоди", RegexOption.IGNORE_CASE)
