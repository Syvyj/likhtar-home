package com.likhtar.home.core.catalog

import android.util.Log
import com.likhtar.home.core.data.RecentHistoryRepository
import com.likhtar.home.core.data.SettingsRepository
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Пам'ять озвучок і джерел — у профілі на сервері, а приставка тримає копію.
 *
 * «Продовжити перегляд» і «У списку» вже жили в профілі, тож за іншим телевізором серія
 * продовжувалась, але з першою-ліпшою озвучкою: пам'ять «вмикали DniproFilm» лишалась
 * на старій приставці. Тепер вона там, де й решта профілю.
 *
 * Той самий порядок, що в [WatchlistRepository]: на старті тягнемо з сервера, кожен вибір
 * відправляємо одразу. Код, що ЧИТАЄ пам'ять, не змінився — він і далі питає
 * [RecentHistoryRepository], просто там тепер копія профілю.
 */
@Singleton
class ProfilePrefsRepository @Inject constructor(
    private val config: CatalogConfig,
    private val settings: SettingsRepository,
    private val history: RecentHistoryRepository,
) {
    suspend fun sync() = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl() ?: return@withContext
        val profile = settings.settings.first().activeProfileId
        for (kind in KINDS) {
            runCatching {
                val body = httpGet(
                    "$base/api/prefs?profile=${encode(profile)}&kind=$kind", TIMEOUT_MS,
                ) ?: return@runCatching
                val array = JSONObject(body).optJSONArray("values") ?: return@runCatching
                val server = array.toStringList()
                val local = if (kind == "voice") history.voices(profile).first() else history.sources(profile).first()
                if (server.isEmpty()) {
                    // Перший запуск після оновлення: у профілі порожньо, а на приставці вже
                    // є пам'ять — віддаємо її серверу від найстаршого, щоб порядок зберігся.
                    local.asReversed().forEach { remember(kind, it) }
                } else {
                    history.replaceRecent(kind, profile, server + local)
                }
            }.onFailure { Log.w(TAG, "пам'ять «$kind» не синхронізувалась", it) }
        }
    }

    /** Вибір озвучки чи джерела — одразу в профіль. `kind`: `voice` | `source`. */
    suspend fun remember(kind: String, value: String) = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl() ?: return@withContext
        if (value.isBlank()) return@withContext
        runCatching {
            val payload = JSONObject()
                .put("profile_id", settings.settings.first().activeProfileId)
                .put("kind", kind)
                .put("value", value)
                .toString()
            httpPostJson("$base/api/prefs", payload, TIMEOUT_MS)
        }.onFailure { Log.w(TAG, "пам'ять «$kind» не відправилась", it) }
        Unit
    }

    private fun encode(v: String) = URLEncoder.encode(v, "UTF-8")

    private companion object {
        const val TAG = "LikhtarPrefs"
        const val TIMEOUT_MS = 5_000
        val KINDS = listOf("voice", "source")
    }
}
