package com.likhtar.home.core.tv

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Канал, який відкривається посиланням у конкретному застосунку.
 *
 * [packageName] обов'язковий: без нього Sweet.TV перехопить будь-яке http-посилання й
 * відкриє його у власному вбудованому браузері замість плеєра.
 */
data class ChannelLink(
    val name: String,
    val packageName: String,
    val url: String,
    /** Наш власний рядок: «Українське ТБ», «Кіно»… Порожня — канал без рядка. */
    val category: String = "",
    /** Id цього ж каналу в каталозі MEGOGO — звідки береться «зараз в ефірі». */
    val epgId: String = "",
    /** Постер шоу: у нього немає телепрограми, а hero без кадру порожній. */
    val image: String = "",
    /** Логотип каналу з каталогу MEGOGO — свого Sweet.TV назовні не дає. */
    val logo: String = "",
    /** Прозорий знак з epg.one — під темне тло, без плашки. Порожньо — є лише [logo]. */
    val mark: String = "",
    /** Варіації франшизи («Голі й налякані XL»…) — вони живуть у бічній панелі. */
    val variants: List<ChannelVariant> = emptyList(),
)

/** Варіація шоу для хронології в бічній панелі. */
data class ChannelVariant(
    val title: String,
    val year: String,
    val description: String,
    val meta: String,
    val app: String,
    val url: String,
)

/**
 * Прив'язки «канал → застосунок» посиланнями.
 *
 * Проміжний крок до повноцінної прив'язки: у TIF цих каналів немає (OTT-застосунки туди
 * нічого не пишуть), а веб-сторінка каналу в Sweet.TV чи MEGOGO є завжди. Список лежить
 * у файлі, а не в коді, щоб перевіряти нові адреси без перезбирання:
 *
 * `adb push channel-links.json /sdcard/Android/data/com.likhtar.home/files/channel-links.json`
 *
 * Файл із зовнішньої теки перекриває запечений у APK.
 *
 * **Запечений лежить лише у флейворі `personal`** (`app/src/personal/assets`), бо це особисті
 * підписки, а не список каналів: у стор-збірці чужі діплінки на чиїсь підписки не
 * мають лежати взагалі. Тому в `play` цей шлях чесно віддає порожньо, і джерело каналів там
 * має вказати сама людина — див. `docs/plan-tv-sources.md`.
 */
@Singleton
class ChannelLinksRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // ТВ Ефір питає прив'язки кожні 5 хв, а файл міняється лише через `adb push`: тримаємо
    // розібране в пам'яті, доки в зовнішнього файлу ті самі mtime і розмір. Запечений в APK
    // не змінюється взагалі — для нього ключ 0/0 (зовнішнього файлу немає).
    @Volatile private var cached: Pair<Pair<Long, Long>, List<ChannelLink>>? = null

    suspend fun links(): List<ChannelLink> = withContext(Dispatchers.IO) {
        val external = externalFile()?.takeIf { it.exists() }
        val key = (external?.lastModified() ?: 0L) to (external?.length() ?: 0L)
        cached?.takeIf { it.first == key }?.let { return@withContext it.second }
        read(external).also { cached = key to it }
    }

    private fun read(external: File?): List<ChannelLink> {
        val raw = external?.readText()
            ?: runCatching { context.assets.open(ASSET).bufferedReader().use { it.readText() } }
                .getOrNull()
            ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val name = json.optString("name")
                val packageName = json.optString("package")
                val url = json.optString("url")
                if (name.isBlank() || packageName.isBlank() || url.isBlank()) return@mapNotNull null
                ChannelLink(
                    name = name,
                    packageName = packageName,
                    url = url,
                    category = json.optString("category"),
                    epgId = json.optString("epg"),
                    image = json.optString("image"),
                    logo = json.optString("logo"),
                    variants = json.optJSONArray("variants")?.let { array ->
                        (0 until array.length()).mapNotNull { position ->
                            val item = array.optJSONObject(position) ?: return@mapNotNull null
                            ChannelVariant(
                                title = item.optString("title"),
                                year = item.optString("year"),
                                description = item.optString("desc"),
                                meta = item.optString("meta"),
                                app = item.optString("app"),
                                url = item.optString("url"),
                            )
                        }
                    }.orEmpty(),
                )
            }
        }.onFailure { Log.w(TAG, "Не вдалося прочитати прив'язки каналів", it) }
            .getOrDefault(emptyList())
    }

    private fun externalFile(): File? =
        context.getExternalFilesDir(null)?.let { File(it, ASSET) }

    private companion object {
        const val TAG = "LikhtarTv"
        const val ASSET = "channel-links.json"
    }
}
