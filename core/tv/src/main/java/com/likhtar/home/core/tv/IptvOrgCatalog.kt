package com.likhtar.home.core.tv

import com.likhtar.home.core.data.http
import android.content.Context
import android.util.JsonReader
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Каталог каналів світу від проєкту iptv-org: 250 країн і понад 41 000 каналів
 * із країною та категоріями. Ліцензія проєкту вільна, ключів не потрібно.
 *
 * **Чому файл кешується на диск, а не парситься з мережі щоразу.** `channels.json` важить
 * близько 10 МБ. Розібрати його цілком у пам'яті на приставці — це десятки мегабайтів купи
 * на рядок плюс стільки ж на дерево об'єктів, чого маленькі TV-бокси не пробачають. Тому
 * файл лягає в кеш, а читається потоково [JsonReader]'ом, який тримає в пам'яті лише
 * поточний об'єкт: із 41 000 каналів у результат потрапляють кількасот обраних країн.
 */
@Singleton
class IptvOrgCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun countries(): List<TvCountry> = withContext(Dispatchers.IO) {
        val body = runCatching { cachedText(COUNTRIES_FILE, COUNTRIES_URL) }
            .onFailure { Log.w(TAG, "Список країн недоступний", it) }
            .getOrNull() ?: return@withContext emptyList()

        runCatching {
            val array = JSONArray(body)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val code = item.optString("code").takeIf { it.length == 2 } ?: return@mapNotNull null
                if (code.uppercase() in BLOCKED_COUNTRIES) return@mapNotNull null
                TvCountry(
                    code = code,
                    // Назва українською з системи: iptv-org віддає англійську, і в
                    // налаштуваннях стояли «Afghanistan, Aland, Albania».
                    name = ukrainianName(code) ?: item.optString("name").ifBlank { code },
                    flag = item.optString("flag"),
                )
            }.sortedWith(compareBy(java.text.Collator.getInstance(UKRAINIAN)) { it.name })
        }.getOrDefault(emptyList())
    }

    /** Канали лише обраних країн. Порожній [countryCodes] означає «нічого не показуємо». */
    suspend fun channels(countryCodes: Set<String>): List<TvChannel> = withContext(Dispatchers.IO) {
        // Фільтр і тут, не лише в [countries]: код міг лишитись у збережених налаштуваннях із
        // часів, коли його ще віддавали.
        val allowed = countryCodes.filterNot { it.uppercase() in BLOCKED_COUNTRIES }.toSet()
        if (allowed.isEmpty()) return@withContext emptyList()

        val file = runCatching { cachedFile(CHANNELS_FILE, CHANNELS_URL) }
            .onFailure { Log.w(TAG, "Каталог каналів недоступний", it) }
            .getOrNull() ?: return@withContext emptyList()

        runCatching { readChannels(file, allowed) }
            .onFailure { Log.w(TAG, "Каталог каналів не читається", it) }
            .getOrDefault(emptyList())
    }

    private fun readChannels(file: File, countryCodes: Set<String>): List<TvChannel> {
        val result = mutableListOf<TvChannel>()

        JsonReader(file.bufferedReader()).use { reader ->
            reader.beginArray()
            while (reader.hasNext()) {
                readChannel(reader, countryCodes)?.let(result::add)
            }
            reader.endArray()
        }

        return result.sortedBy { it.name.lowercase() }
    }

    private fun readChannel(reader: JsonReader, countryCodes: Set<String>): TvChannel? {
        var id: String? = null
        var name: String? = null
        var country: String? = null
        var closed = false
        var nsfw = false
        val categories = mutableListOf<String>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextStringOrNull()
                "name" -> name = reader.nextStringOrNull()
                "country" -> country = reader.nextStringOrNull()
                "closed" -> closed = reader.nextStringOrNull() != null
                "is_nsfw" -> nsfw = reader.nextBoolean()
                "categories" -> {
                    reader.beginArray()
                    while (reader.hasNext()) reader.nextStringOrNull()?.let(categories::add)
                    reader.endArray()
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()

        // Закриті канали й дорослий контент до сітки не потрапляють: перші вже нічого не
        // показують, другі не мають з'являтися на домашньому екрані без окремого рішення.
        if (closed || nsfw) return null
        if (country == null || country !in countryCodes) return null
        val channelId = id ?: return null

        return TvChannel(
            id = channelId,
            name = name ?: channelId,
            country = country,
            categories = categories,
        )
    }

    private fun JsonReader.nextStringOrNull(): String? =
        if (peek() == android.util.JsonToken.NULL) {
            nextNull()
            null
        } else {
            nextString().takeIf { it.isNotBlank() }
        }

    private fun cachedText(name: String, url: String): String =
        cachedFile(name, url).readText()

    /**
     * Файл із кешу, якщо він свіжий, інакше — завантажений заново. Каталог каналів змінюється
     * повільно, тож тиждень — розумний компроміс між свіжістю й трафіком приставки.
     */
    private fun cachedFile(name: String, url: String): File {
        val file = File(context.cacheDir, name)
        val fresh = file.exists() &&
            System.currentTimeMillis() - file.lastModified() < CACHE_TTL_MS &&
            file.length() > 0
        if (fresh) return file

        http(url, TIMEOUT_MS, headers = mapOf("User-Agent" to USER_AGENT)) { connection ->
            val temporary = File(context.cacheDir, "$name.part")
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            // Пишемо через тимчасовий файл: обірваний посеред завантаження кеш виглядав би
            // як валідний і ламав би розбір до самого закінчення TTL.
            if (file.exists()) file.delete()
            temporary.renameTo(file)
        }
        return file
    }

    private companion object {
        const val TAG = "LikhtarTv"
        const val USER_AGENT = "LIKHTAR-TV-launcher/0.1 (Android TV)"
        const val TIMEOUT_MS = 20_000
        const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val COUNTRIES_URL = "https://iptv-org.github.io/api/countries.json"
        const val CHANNELS_URL = "https://iptv-org.github.io/api/channels.json"
        const val COUNTRIES_FILE = "iptv-org-countries.json"
        const val CHANNELS_FILE = "iptv-org-channels.json"

        /**
         * Країни, яких у переліках немає взагалі — ні в каналах, ні у виборі доступності.
         * Продуктове рішення; технічної причини тут немає, тому й винесено в одне місце.
         */
        val BLOCKED_COUNTRIES = setOf("RU")
    }
}

private val UKRAINIAN = java.util.Locale("uk")

/**
 * «UA» → «Україна»; `null`, коли код не з ISO-переліку: на такий система вертає
 * «Невідомий регіон», і він ставав би назвою.
 */
internal fun ukrainianName(code: String): String? {
    val iso = code.uppercase()
    if (iso !in ISO_COUNTRIES) return null
    return java.util.Locale("", iso).getDisplayCountry(UKRAINIAN)
        .takeIf { it.isNotBlank() && !it.equals(iso, ignoreCase = true) }
}

private val ISO_COUNTRIES = java.util.Locale.getISOCountries().toSet()
