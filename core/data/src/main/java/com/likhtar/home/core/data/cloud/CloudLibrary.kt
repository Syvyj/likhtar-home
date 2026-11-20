package com.likhtar.home.core.data.cloud

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Моя бібліотека хмарних ігор (макет: S): які сервіси є, Game Pass, списки «Граю зараз» і
 * «Улюблені», «У мене є». Лаунчер ні в що не логіниться — «мої» складаються з того, що можна
 * знати чесно (docs/cloud-games-design.md, «Лише мої»).
 */
data class CloudLibrary(
    val services: Set<CloudService> = CloudService.entries.toSet(),
    val gamePass: Boolean = false,
    /** Ключі ігор; найсвіжіший — першим. */
    val playing: List<String> = emptyList(),
    val favorites: List<String> = emptyList(),
    val owned: Set<String> = emptySet(),
    /** Перший запуск («Які сервіси в тебе є?») пройдено. */
    val setupDone: Boolean = false,
) {
    /** Гру видно, якщо вона є хоч в одному підключеному сервісі. */
    fun visible(g: CloudGame): Boolean = g.rom != null || g.services(services).isNotEmpty()

    /** ROM на приставці — завжди «мій». */
    fun isMine(g: CloudGame): Boolean = visible(g) &&
        (g.rom != null || g.free || (gamePass && CloudService.XBOX in services && !g.sources[CloudService.XBOX].isNullOrEmpty()) || g.key in owned)

    /** Запуск піднімає гру на початок «Граю зараз». */
    fun played(key: String) = copy(playing = listOf(key) + (playing - key))

    fun togglePlaying(key: String) = copy(playing = toggle(playing, key))
    fun toggleFavorite(key: String) = copy(favorites = toggle(favorites, key))
    fun toggleOwned(key: String) = copy(owned = if (key in owned) owned - key else owned + key)

    private fun toggle(list: List<String>, key: String) = if (key in list) list - key else listOf(key) + list
}

fun writeLibrary(lib: CloudLibrary): String = JSONObject()
    .put("svc", lib.services.joinToString("") { it.code.toString() })
    .put("gp", lib.gamePass)
    .put("now", JSONArray(lib.playing))
    .put("fav", JSONArray(lib.favorites))
    .put("own", JSONArray(lib.owned.toList()))
    .put("setup", lib.setupDone)
    .toString()

fun readLibrary(json: String): CloudLibrary {
    val o = JSONObject(json)
    fun list(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    return CloudLibrary(
        services = o.optString("svc", "bgx").mapNotNull(CloudService::of).toSet(),
        gamePass = o.optBoolean("gp"),
        playing = list("now"),
        favorites = list("fav"),
        owned = list("own").toSet(),
        setupDone = o.optBoolean("setup"),
    )
}

/** Бібліотека на приставці; змінюється одразу, зберігається в SharedPreferences. */
@Singleton
class CloudLibraryRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs get() = context.getSharedPreferences("cloud_games", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(
        runCatching { prefs.getString(KEY, null)?.let(::readLibrary) }.getOrNull() ?: CloudLibrary(),
    )
    val library: StateFlow<CloudLibrary> = current.asStateFlow()

    fun update(change: (CloudLibrary) -> CloudLibrary) {
        val next = change(current.value)
        current.value = next
        prefs.edit { putString(KEY, writeLibrary(next)) }
    }

    private companion object {
        const val KEY = "library"
    }
}
