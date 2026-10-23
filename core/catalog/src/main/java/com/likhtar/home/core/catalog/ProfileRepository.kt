package com.likhtar.home.core.catalog

import android.util.Log
import com.likhtar.home.core.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Хто дивиться: свій прогрес, свої закладки, свої поради. */
data class Profile(
    val id: String,
    val name: String,
    val kids: Boolean,
)

/**
 * Профілі живуть на сервері, активний — на приставці.
 *
 * Це зручність, а не безпека: жодної автентифікації тут немає й не планується, сервер
 * стоїть у домашній мережі. PIN-коди прийдуть окремо разом із виходом назовні.
 *
 * Профіль «default» («Дім») існує завжди — усе, що надіслане без `profile`, падає в нього,
 * тому мігрувати можна поступово.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val config: CatalogConfig,
    private val settings: SettingsRepository,
) {
    /** Активний профіль приставки. Спільний для всіх, хто до неї підходить. */
    val activeId: Flow<String> = settings.settings.map { it.activeProfileId }

    suspend fun setActive(id: String) = settings.setActiveProfile(id)

    suspend fun all(): List<Profile> = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl() ?: return@withContext emptyList()
        runCatching {
            val body = httpGet("$base/api/profiles", TIMEOUT_MS) ?: return@runCatching emptyList()
            val array = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val id = json.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Profile(
                    id = id,
                    name = json.optString("name").ifBlank { id },
                    kids = json.optBoolean("kids"),
                )
            }
        }.onFailure { Log.w(TAG, "Профілі не читаються", it) }.getOrDefault(emptyList())
    }

    /** Повертає створений профіль або `null`, якщо сервер не прийняв. */
    suspend fun create(name: String, kids: Boolean): Profile? = withContext(Dispatchers.IO) {
        val base = config.homeOnlyUrl() ?: return@withContext null
        val trimmed = name.trim().ifBlank { return@withContext null }
        // Id робимо ми: беремо час у 36-й системі — короткий, унікальний і не залежить від
        // того, як людина назве профіль (кирилиця в id нікому не потрібна).
        val profile = Profile(id = "p" + System.currentTimeMillis().toString(36), name = trimmed, kids = kids)
        runCatching {
            val payload = JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("kids", profile.kids)
                .toString()
            if (httpPostJson("$base/api/profiles", payload, TIMEOUT_MS).ok) profile else null
        }.onFailure { Log.w(TAG, "Профіль не створився", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "LikhtarProfiles"
        const val TIMEOUT_MS = 10_000
    }
}
