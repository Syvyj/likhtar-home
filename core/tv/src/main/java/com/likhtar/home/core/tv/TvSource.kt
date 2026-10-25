package com.likhtar.home.core.tv

import com.likhtar.home.core.data.http
import android.util.Log
import com.likhtar.home.core.data.SettingsRepository
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Звідки ТВ Ефір бере список каналів.
 *
 * Шов заведено тому, що `channel-links.json` — це не список каналів, а особисті підписки:
 * у людини без них кнопка відкриє застосунок і впреться в платну заглушку. Сітка 5B
 * при цьому не змінюється взагалі — вона працює з `ChannelSchedule`, і їй байдуже, хто його
 * наповнив. Розбір і порядок кроків — у `docs/plan-tv-sources.md`.
 */
interface TvSource {
    suspend fun channels(): List<ChannelLink>
}

/**
 * Вбудовані прив'язки з `channel-links.json`. Лишається типовим джерелом: поки немає ні
 * парсера M3U, ні онбордингу, будь-яке інше зробило б ТВ Ефір порожнім.
 */
@Singleton
class BuiltinTvSource @Inject constructor(
    private val links: ChannelLinksRepository,
) : TvSource {
    override suspend fun channels(): List<ChannelLink> = links.links()
}

/**
 * Стан розділу → картки сітки.
 *
 * Категорія береться з першої групи, яка містить канал: у сітці 5B це назва вкладки, і канал
 * без групи має лишитися видимим, а не зникнути разом із порожньою назвою.
 */
fun TvGuideState.toChannelLinks(): List<ChannelLink> {
    val groupTitles = channels.associate { channel ->
        channel.id to groups.firstOrNull { group ->
            group.channelsFrom(channels).any { it.id == channel.id }
        }?.name.orEmpty()
    }
    return channels.map { channel ->
        ChannelLink(
            name = channel.name,
            packageName = channelApps[channel.id].orEmpty(),
            url = "",
            category = groupTitles[channel.id].orEmpty(),
            epgId = channel.id,
            logo = channel.logo.orEmpty(),
        )
    }
}

/**
 * Канали з плейлиста M3U, який вказала сама людина.
 *
 * Це те джерело, яке робить ТВ Ефір придатним для чужих рук: посилання на потік, назви,
 * групи й логотипи приходять від її провайдера, а не з наших підписок.
 *
 * `packageName` порожній навмисно: пряме посилання на потік відкриває плеєр, а не застосунок
 * провайдера, тож перехоплювати нічого. Порожній список (не впало, а нічого не прийшло) —
 * привід лишити людину з вбудованим джерелом, і саме так робить [DefaultTvSource].
 */
@Singleton
class PlaylistTvSource @Inject constructor(
    private val settings: SettingsRepository,
) : TvSource {

    // Плейлист провайдера — кілька мегабайтів, а ТВ Ефір питає канали кожні 5 хв.
    private val cache = ConditionalCache<List<ChannelLink>>(CACHE_TTL_MS)

    override suspend fun channels(): List<ChannelLink> = withContext(Dispatchers.IO) {
        val url = settings.settings.first().tvPlaylistUrl
        if (url.isBlank()) return@withContext emptyList()
        runCatching { cache.get(url) { etag, lastModified -> download(url, etag, lastModified) } }
            // Адреса плейлиста часто несе токен провайдера, а текст винятку — усю адресу: логуємо
            // лише хост і клас помилки.
            .onFailure { Log.w("LikhtarTv", "Плейлист не завантажився: ${java.net.URI(url).host ?: "файл"} (${it.javaClass.simpleName})") }
            .getOrDefault(emptyList())
    }

    /** `null` — сервер відповів 304: те, що в кеші, досі актуальне. */
    private fun download(url: String, etag: String?, lastModified: String?): ConditionalCache.Fetched<List<ChannelLink>>? {
        // Плейлист, переданий файлом із телефона: лежить у лаунчері, мережа не потрібна.
        if (url.startsWith("file:")) {
            val file = java.io.File(java.net.URI(url.substringBefore('#')))
            return ConditionalCache.Fetched(file.inputStream().use { links(it) }, null, null)
        }
        val conditional = buildMap {
            etag?.let { put("If-None-Match", it) }
            lastModified?.let { put("If-Modified-Since", it) }
        }
        return http(url, TIMEOUT_MS, READ_TIMEOUT_MS, headers = conditional) { connection ->
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) return null
            val links = connection.inputStream.use { links(it) }
            ConditionalCache.Fetched(links, connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"))
        }
    }

    private fun links(input: java.io.InputStream) = M3uParser().parse(input).map { channel ->
        ChannelLink(
            name = channel.name,
            packageName = "",
            url = channel.url,
            category = channel.group,
            epgId = channel.id,
            logo = channel.logo,
        )
    }

    private companion object {
        const val TIMEOUT_MS = 15_000

        /** Плейлисти провайдерів бувають на кілька мегабайтів — читання довше за з'єднання. */
        const val READ_TIMEOUT_MS = 60_000

        /** Провайдер міняє список каналів рідко; через пів години — умовний запит, а не повний. */
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}

/**
 * Одне значення в пам'яті з TTL і умовним запитом (ETag / Last-Modified).
 *
 * Свіже — віддаємо без мережі. Протухле — [get] питає `fetch` з валідаторами попередньої
 * відповіді; `null` від нього означає 304, і старе значення живе ще один TTL. Кешуємо лише
 * успіх: виняток із `fetch` летить далі й кеш не чіпає, а порожній результат не зберігаємо —
 * порожній плейлист із 200 найчастіше сторінка-заглушка провайдера.
 */
internal class ConditionalCache<T : Collection<*>>(
    private val ttlMs: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    class Fetched<T>(val value: T, val etag: String?, val lastModified: String?)

    private class Entry<T>(val key: String, val fetched: Fetched<T>, val at: Long)

    @Volatile private var entry: Entry<T>? = null

    fun get(key: String, fetch: (etag: String?, lastModified: String?) -> Fetched<T>?): T {
        val old = entry?.takeIf { it.key == key }
        if (old != null && now() - old.at < ttlMs) return old.fetched.value
        val fetched = fetch(old?.fetched?.etag, old?.fetched?.lastModified)
            ?: old?.fetched
            // 304 без того, що кешували, — сервер плутає; нічого показати не можемо.
            ?: throw java.io.IOException("304 без кешу")
        if (fetched.value.isNotEmpty()) entry = Entry(key, fetched, now())
        return fetched.value
    }
}

/**
 * Хто саме віддає канали: плейлист, якщо людина його вказала, інакше вбудоване.
 *
 * Перевіряємо не лише налаштування, а й результат: якщо плейлист вказано, але він не
 * завантажився чи виявився порожнім, розділ мовчки повертається до вбудованого замість того,
 * щоб показати порожній екран. Порожній ТВ Ефір — гірша відповідь, ніж застарілий.
 */
@Singleton
class DefaultTvSource @Inject constructor(
    private val builtin: BuiltinTvSource,
    private val playlist: PlaylistTvSource,
) : TvSource {
    // Вбудований набір — українські канали під наші підписки: у публічній збірці лише плейлист людини.
    override suspend fun channels(): List<ChannelLink> =
        playlist.channels().ifEmpty { if (com.likhtar.home.core.model.Distribution.store) emptyList() else builtin.channels() }
}
