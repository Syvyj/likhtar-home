package com.likhtar.home.core.tv

/** Країна зі списку iptv-org: код ISO-3166-1 alpha-2, назва й прапорець-емодзі. */
data class TvCountry(
    val code: String,
    val name: String,
    val flag: String,
)

/**
 * Канал каталогу.
 *
 * [id] — це `tvg-id` у термінах XMLTV (наприклад `1plus1.ua`); саме за ним телепрограма
 * зшивається з каналом, тому він і є ключем, а не назва: назв у каналу буває кілька.
 */
data class TvChannel(
    val id: String,
    val name: String,
    val country: String,
    val categories: List<String>,
    val logo: String? = null,
) {
    /** Альтернативні назви знадобляться, коли XMLTV користувача не має нашого `id`. */
    val matchKey: String get() = normalizeChannelName(name)
}

/** Одна передача телепрограми. Час у мілісекундах епохи — щоб сітка не залежала від формату. */
data class TvProgramme(
    val channelId: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val description: String? = null,
)

/**
 * Групи каналів. Автоматичні беруться з категорій iptv-org, власні створює користувач.
 *
 * [channelIds] порожній у автогрупи: її склад обчислюється з категорії щоразу, інакше
 * група «Новини» застигла б у тому вигляді, у якому користувач її вперше відкрив.
 */
data class TvGroup(
    val id: String,
    val name: String,
    val categoryId: String? = null,
    val channelIds: List<String> = emptyList(),
) {
    val isAutomatic: Boolean get() = categoryId != null
}

/**
 * Категорії iptv-org, які мають сенс на екрані телевізора. Решта (`xxx`, `outdoor`,
 * `auto`…) або надто вузькі, або порожні для більшості країн. Значення — рядкові ресурси назв.
 */
val TV_CATEGORY_TITLES: Map<String, Int> = linkedMapOf(
    "news" to R.string.tv_category_news,
    "general" to R.string.tv_category_general,
    "movies" to R.string.tv_category_movies,
    "series" to R.string.tv_category_series,
    "sports" to R.string.tv_category_sports,
    "kids" to R.string.tv_category_kids,
    "music" to R.string.tv_category_music,
    "documentary" to R.string.tv_category_documentary,
    "entertainment" to R.string.tv_category_entertainment,
    "culture" to R.string.tv_category_culture,
    "science" to R.string.tv_category_science,
    "business" to R.string.tv_category_business,
    "weather" to R.string.tv_category_weather,
)

/**
 * Назва каналу без того, що заважає порівнянню: регістру, позначок якості та розділових знаків.
 *
 * Потрібно, бо канал у XMLTV провайдера і той самий канал у каталозі майже ніколи не збігаються
 * символ у символ: «1+1 HD», «1 + 1», «1plus1» — це один канал.
 */
fun normalizeChannelName(name: String): String = name
    .lowercase()
    .let { CONFUSABLE.entries.fold(it) { acc, (from, to) -> acc.replace(from, to) } }
    .replace(QUALITY_MARKS, " ")
    .replace(NON_ALPHANUMERIC, "")
    .trim()

/**
 * Кириличні літери, які на екрані не відрізнити від латинських.
 *
 * Без цього згортання `1+1`, `2+2` чи `ICTV` у чужому плейлисті й у XMLTV лежать як різні
 * канали, хоч на око однакові. Таблиця взята з `voiceKey` на сервері (`scanner/voices.go:32`):
 * там на цьому вже спіймалися — `15K3` і `15К3` жили як дві різні студії озвучки.
 */
private val CONFUSABLE = mapOf(
    'а' to 'a', 'в' to 'b', 'е' to 'e', 'к' to 'k', 'м' to 'm', 'н' to 'h', 'о' to 'o',
    'р' to 'p', 'с' to 'c', 'т' to 't', 'у' to 'y', 'х' to 'x', 'і' to 'i', 'ї' to 'i',
)

private val QUALITY_MARKS = Regex("\\b(hd|fhd|uhd|4k|sd|hevc|h265|1080p?|720p?)\\b")
private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]")
