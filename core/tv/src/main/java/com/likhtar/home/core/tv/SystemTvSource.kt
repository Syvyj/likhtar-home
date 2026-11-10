package com.likhtar.home.core.tv

import android.content.Context
import android.media.tv.TvContract
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Канали, які застосунки самі реєструють в Android TV (TvProvider, TIF): ТВ-тюнер, IPTV-плеєри з
 * TV input, сервіси з інтеграцією в Live-вкладку Google TV. Без таблиць під країну: що
 * застосунок оголосив, те й видно. Рядки домашнього екрана (`TYPE_PREVIEW`) — не канали.
 *
 * Читати дозволяє `READ_TV_LISTINGS`, який лаунчер уже просить для «Продовжити перегляд»; без
 * нього провайдер віддає порожньо. Відкриваємо адресою каналу — її бере системний ТВ-застосунок.
 */
@Singleton
class SystemTvSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : TvSource {
    override suspend fun channels(): List<ChannelLink> = withContext(Dispatchers.IO) {
        runCatching { query() }.onFailure { Log.w(TAG, "TvProvider", it) }.getOrDefault(emptyList())
    }

    private fun query(): List<ChannelLink> {
        val projection = arrayOf(
            TvContract.Channels._ID,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_PACKAGE_NAME,
            TvContract.Channels.COLUMN_TYPE,
            TvContract.Channels.COLUMN_INPUT_ID,
            TvContract.Channels.COLUMN_APP_LINK_INTENT_URI,
        )
        val all = context.contentResolver.query(TvContract.Channels.CONTENT_URI, projection, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) add(Row(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty(), c.getString(5).orEmpty()))
            }
        }.orEmpty()
        Log.i(TAG, "каналів у TvProvider: ${all.size}, за типом ${all.groupingBy { it.type }.eachCount()}, канали ${all.filter { it.type != TvContract.Channels.TYPE_PREVIEW }.map { "${it.name} (${it.input})" }}")
        return all
            .filter { it.type != TvContract.Channels.TYPE_PREVIEW && it.name.isNotBlank() && it.pkg != context.packageName }
            .map { r ->
                // Посилання застосунку на сам канал — першим: на Google TV системного ТВ-застосунку,
                // що відкриває адресу каналу, немає (перевірено на приставці), лишився б застосунок
                // без каналу. Без посилання — адреса каналу (Android TV з Live Channels).
                val url = r.appLink.ifBlank { TvContract.buildChannelUri(r.id).toString() }
                ids[url] = r.id
                val logo = TvContract.buildChannelLogoUri(r.id)
                ChannelLink(
                    name = r.name,
                    packageName = r.pkg,
                    url = url,
                    // Логотипа може не бути — тоді без нього, а не порожня плашка.
                    logo = if (runCatching { context.contentResolver.openAssetFileDescriptor(logo, "r")?.use { true } }.getOrNull() == true) logo.toString() else "",
                )
            }
    }

    /**
     * Програма каналів з TvProvider (що застосунок сам записав): зараз і далі на [HOURS] год.
     * Канал застосунку без програми ТБ Ефір не показує, тож без цього системні канали не видно.
     */
    suspend fun schedules(links: List<ChannelLink>): Map<String, ChannelSchedule> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        links.mapNotNull { link ->
            val id = ids[link.url] ?: return@mapNotNull null
            val programmes = runCatching { programmes(id, now) }.onFailure { Log.w(TAG, "програма каналу $id", it) }.getOrDefault(emptyList())
            val live = programmes.firstOrNull { it.startMillis <= now && now < it.endMillis } ?: return@mapNotNull null
            val upcoming = programmes.filter { it.startMillis >= live.endMillis }
            link.url to ChannelSchedule(
                live = NowOnAir(
                    title = live.title, description = live.description, image = live.image,
                    startMillis = live.startMillis, endMillis = live.endMillis,
                    nextTitle = upcoming.firstOrNull()?.title.orEmpty(), nextStartMillis = upcoming.firstOrNull()?.startMillis ?: 0L,
                    upcoming = upcoming,
                ),
                upcoming = upcoming,
            )
        }.toMap()
    }

    private fun programmes(channelId: Long, now: Long): List<EpgProgramme> {
        val uri = TvContract.buildProgramsUriForChannel(channelId, now - HOUR_MS, now + HOURS * HOUR_MS)
        val projection = arrayOf(
            TvContract.Programs.COLUMN_TITLE,
            TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
            TvContract.Programs.COLUMN_POSTER_ART_URI,
            TvContract.Programs.COLUMN_THUMBNAIL_URI,
        )
        return context.contentResolver.query(uri, projection, null, null, TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val title = c.getString(0).orEmpty()
                    if (title.isNotBlank()) add(EpgProgramme(title, c.getLong(1), c.getLong(2), c.getString(3).orEmpty(), c.getString(5) ?: c.getString(4)))
                }
            }
        }.orEmpty()
    }

    /** Адреса каналу в ТБ Ефірі → id у TvProvider (для програми). */
    private val ids = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private data class Row(val id: Long, val name: String, val pkg: String, val type: String, val input: String, val appLink: String)

    private companion object {
        const val TAG = "LikhtarSystemTv"
        const val HOURS = 6
        const val HOUR_MS = 60 * 60 * 1000L
    }
}
