package com.likhtar.home.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** Нагадати про передачу: канал, назва й коли починається. */
data class ProgrammeReminder(val channelUrl: String, val channel: String, val title: String, val startMillis: Long) {
    /** Одна передача — одне нагадування. */
    val key: String get() = "$channelUrl@$startMillis"
}

private val Context.reminderStore: DataStore<Preferences> by preferencesDataStore(name = "likhtar_programme_reminders")

/**
 * Нагадування про передачі ТБ Ефіру. На диску, а не в пам'яті: процес лаунчера переживає не все
 * (оновлення, перезавантаження), а нагадування на вечір ставлять зранку. Показ — у застосунку
 * (служба кнопок малює його поверх будь-чого, плеєра теж).
 */
@Singleton
class ProgrammeReminders @Inject constructor(@ApplicationContext private val context: Context) {
    val reminders: Flow<List<ProgrammeReminder>> = context.reminderStore.data.map { decodeReminders(it[KEY]) }

    /** Поставити, а якщо вже стоїть — зняти. Те, що давно почалось, заодно прибираємо. */
    suspend fun toggle(reminder: ProgrammeReminder) = edit { list ->
        if (list.any { it.key == reminder.key }) list.filterNot { it.key == reminder.key } else list + reminder
    }

    suspend fun remove(key: String) = edit { list -> list.filterNot { it.key == key } }

    private suspend fun edit(change: (List<ProgrammeReminder>) -> List<ProgrammeReminder>) {
        context.reminderStore.edit { prefs ->
            val now = System.currentTimeMillis()
            prefs[KEY] = encodeReminders(change(decodeReminders(prefs[KEY])).filter { it.startMillis > now - STALE_MS })
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("reminders")

        /** Передача, що почалась годину тому, — нагадувати пізно. */
        const val STALE_MS = 60 * 60 * 1000L
    }
}

internal fun encodeReminders(list: List<ProgrammeReminder>): String = JSONArray().apply {
    list.forEach { put(JSONObject().put("u", it.channelUrl).put("c", it.channel).put("t", it.title).put("s", it.startMillis)) }
}.toString()

internal fun decodeReminders(text: String?): List<ProgrammeReminder> = runCatching {
    val array = JSONArray(text ?: return emptyList())
    (0 until array.length()).map { i ->
        array.getJSONObject(i).let { ProgrammeReminder(it.getString("u"), it.getString("c"), it.getString("t"), it.getLong("s")) }
    }
}.getOrDefault(emptyList())
