package com.likhtar.home.core.catalog

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

private val Context.studioSubscriptionsStore: DataStore<Preferences> by preferencesDataStore(name = "likhtar_studios")

/** Студії й канали, на які підписались: їхні сторінки — блоком «Мої студії» у стрімінгах. */
@Singleton
class StudioSubscriptions @Inject constructor(@ApplicationContext private val context: Context) {
    val items: Flow<List<StudioRef>> = context.studioSubscriptionsStore.data.map { decode(it[KEY].orEmpty()) }

    /** Повертає новий стан: true — підписані. */
    suspend fun toggle(studio: StudioRef): Boolean {
        var added = false
        context.studioSubscriptionsStore.edit { prefs ->
            val current = decode(prefs[KEY].orEmpty())
            val without = current.filterNot { it.key == studio.key }
            added = without.size == current.size
            prefs[KEY] = encode(if (added) without + studio else without)
        }
        return added
    }

    private companion object {
        val KEY = stringPreferencesKey("studios")

        fun encode(list: List<StudioRef>) = JSONArray(
            list.map { JSONObject().put("id", it.id).put("name", it.name).put("logo", it.logoPath ?: "").put("network", it.network) },
        ).toString()

        fun decode(raw: String): List<StudioRef> = runCatching {
            val a = JSONArray(raw)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                StudioRef(o.getInt("id"), o.getString("name"), o.optString("logo").ifBlank { null }, o.optBoolean("network"))
            }
        }.getOrDefault(emptyList())
    }
}
