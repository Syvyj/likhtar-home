package com.likhtar.home.core.tv

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tvGroupsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "likhtar_tv_groups",
)

/** Що користувач зробив із каналами: приховав і чим відкриває. */
data class TvChannelPrefs(
    val hidden: Set<String> = emptySet(),
    /** id каналу → id застосунку (`packageName/activityName`), яким його відкривати. */
    val apps: Map<String, String> = emptyMap(),
)

/**
 * Групи каналів і користувацький стан каналів.
 *
 * Групи зберігаються **одним рядком із переносами**, а не множиною: множина в DataStore
 * невпорядкована, а користувач має право розставити свої групи так, як йому зручно.
 */
@Singleton
class TvGroupsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val groups: Flow<List<TvGroup>> = context.tvGroupsDataStore.data.map { prefs ->
        decodeGroups(prefs[KEY_GROUPS_ORDERED])
    }

    val channelPrefs: Flow<TvChannelPrefs> = context.tvGroupsDataStore.data.map { prefs ->
        TvChannelPrefs(
            hidden = prefs[KEY_HIDDEN_CHANNELS].orEmpty(),
            apps = prefs[KEY_CHANNEL_APPS].orEmpty().mapNotNull { entry ->
                val parts = entry.split(FIELD)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap(),
        )
    }

    /** Приховані — і канали каталогу, і картки Sweet.TV: набір один, ключі не перетинаються. */
    val hiddenChannels: Flow<Set<String>> =
        context.tvGroupsDataStore.data.map { it[KEY_HIDDEN_CHANNELS].orEmpty() }

    /** Довге OK на каналі в ТВ Ефірі → «Приховати»; повернути — «Налаштування → Телебачення». */
    suspend fun hideChannel(url: String) {
        context.tvGroupsDataStore.edit { it[KEY_HIDDEN_CHANNELS] = it[KEY_HIDDEN_CHANNELS].orEmpty() + url }
    }

    suspend fun clearHiddenChannels() {
        context.tvGroupsDataStore.edit { it[KEY_HIDDEN_CHANNELS] = emptySet() }
    }

    private suspend fun edit(transform: (List<TvGroup>) -> List<TvGroup>) {
        context.tvGroupsDataStore.edit { prefs ->
            val updated = transform(decodeGroups(prefs[KEY_GROUPS_ORDERED]))
            prefs[KEY_GROUPS_ORDERED] = updated.joinToString(RECORD) { group ->
                (listOf(group.id, group.name) + group.channelIds).joinToString(FIELD)
            }
        }
    }

    private fun decodeGroups(raw: String?): List<TvGroup> =
        raw.orEmpty().split(RECORD).mapNotNull { record ->
            val parts = record.split(FIELD)
            if (parts.size < 2 || parts[0].isBlank()) return@mapNotNull null
            TvGroup(
                id = parts[0],
                name = parts[1],
                channelIds = parts.drop(2).filter { it.isNotBlank() },
            )
        }

    private companion object {
        val KEY_GROUPS_ORDERED = stringPreferencesKey("tv_groups_ordered")
        val KEY_HIDDEN_CHANNELS = stringSetPreferencesKey("tv_hidden_channels")
        val KEY_CHANNEL_APPS = stringSetPreferencesKey("tv_channel_apps")

        /**
         * NUL і перенос рядка не трапляються ні в назвах груп, ні в id каналів —
         * на відміну від пробілу, який є в кожній другій назві.
         */
        const val FIELD = "\u0000"
        const val RECORD = "\n"
    }
}
