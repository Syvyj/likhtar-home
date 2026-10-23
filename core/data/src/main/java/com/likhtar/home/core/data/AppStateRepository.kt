package com.likhtar.home.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.likhtar.home.core.model.AppCategory
import com.likhtar.home.core.model.AppStates
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "likhtar_app_state",
)

/**
 * Локальний стан застосунків: приховані, заблоковані, закріплені в Кінозалі та ТВ Ефірі.
 *
 * Свідоме відхилення від плану: замість Room тут DataStore. Це чотири множини рядків без
 * запитів і зв'язків — реляційна база не дала б нічого, крім схеми й міграцій. Room
 * лишається для Фаз 3–4, де з'явиться справжній кеш контенту та телепрограми.
 */
@Singleton
class AppStateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val states: Flow<AppStates> = context.appStateDataStore.data.map { prefs ->
        AppStates(
            hidden = prefs[KEY_HIDDEN].orEmpty(),
            locked = prefs[KEY_LOCKED].orEmpty(),
            pinnedToCinema = prefs[KEY_CINEMA].orEmpty(),
            pinnedToLiveTv = prefs[KEY_LIVE_TV].orEmpty(),
            pinnedToHome = prefs[KEY_HOME].orEmpty(),
            categories = prefs[KEY_CATEGORIES].orEmpty().mapNotNull(::parseCategory).toMap(),
        )
    }

    /**
     * Ручна категорія застосунку. `null` повертає його до системної.
     *
     * Лежить тим самим типом, що й решта стану — множиною рядків `id=CATEGORY`. Окрема
     * структура тут не потрібна: запис один на застосунок, і читається він цілком.
     */
    suspend fun setCategory(appId: String, category: AppCategory?) {
        context.appStateDataStore.edit { prefs ->
            val without = prefs[KEY_CATEGORIES].orEmpty().filterNot { it.substringBefore('=') == appId }
            prefs[KEY_CATEGORIES] = if (category == null) {
                without.toSet()
            } else {
                without.toSet() + "$appId=${category.name}"
            }
        }
    }

    private fun parseCategory(entry: String): Pair<String, AppCategory>? {
        val id = entry.substringBefore('=', missingDelimiterValue = "")
        val category = AppCategory.fromName(entry.substringAfter('=', missingDelimiterValue = ""))
        return if (id.isBlank() || category == null) null else id to category
    }

    suspend fun toggleHidden(appId: String) = toggle(KEY_HIDDEN, appId)

    suspend fun toggleLocked(appId: String) = toggle(KEY_LOCKED, appId)

    suspend fun togglePinnedToCinema(appId: String) = toggle(KEY_CINEMA, appId)

    suspend fun togglePinnedToLiveTv(appId: String) = toggle(KEY_LIVE_TV, appId)

    suspend fun togglePinnedToHome(appId: String) = toggle(KEY_HOME, appId)

    private suspend fun set(key: Preferences.Key<Set<String>>, appId: String, present: Boolean) {
        context.appStateDataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            prefs[key] = if (present) current + appId else current - appId
        }
    }

    private suspend fun toggle(key: Preferences.Key<Set<String>>, appId: String) {
        context.appStateDataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            prefs[key] = if (appId in current) current - appId else current + appId
        }
    }

    private companion object {
        val KEY_HIDDEN = stringSetPreferencesKey("hidden")
        val KEY_LOCKED = stringSetPreferencesKey("locked")
        val KEY_CINEMA = stringSetPreferencesKey("pinned_cinema")
        val KEY_LIVE_TV = stringSetPreferencesKey("pinned_livetv")
        val KEY_HOME = stringSetPreferencesKey("pinned_home")
        val KEY_CATEGORIES = stringSetPreferencesKey("categories")
    }
}
