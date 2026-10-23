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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.recentHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "likhtar_recent_history",
)

/** Канал, відкритий із лаунчера: рівно те, що потрібно намалювати картку й повторити запуск. */
data class RecentChannel(
    val url: String,
    val name: String,
    val image: String = "",
    val packageName: String = "",
)

/**
 * Локальні історії Головної: недавні застосунки, канали й пошукові запити.
 *
 * Три списки в одному сховищі, а не три репозиторії з трьома DataStore: у них однакова
 * механіка — «поклади наверх, прибери дублікат, обріж хвіст» — і різняться вони лише
 * тим, як запис перетворюється на рядок.
 *
 * Порядок тут значущий, тому це не `stringSet`, як у [AppStateRepository], а один рядок
 * із записами через `\n`. Множина порядку не тримає, а саме він і є весь сенс «недавніх».
 *
 * Дані не залишають пристрій і нікуди не синхронізуються: це історія на цій приставці.
 */
@Singleton
class RecentHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SearchHistory {
    /** Ідентифікатори застосунків, найсвіжіший — першим. */
    val apps: Flow<List<String>> = context.recentHistoryDataStore.data
        .map { it[KEY_APPS].decodeList() }

    val channels: Flow<List<RecentChannel>> = context.recentHistoryDataStore.data
        .map { prefs -> prefs[KEY_CHANNELS].decodeList().mapNotNull(::decodeChannel) }

    val searches: Flow<List<String>> = context.recentHistoryDataStore.data
        .map { it[KEY_SEARCHES].decodeList() }

    /**
     * Хости джерел для профілю, найсвіжіший — першим.
     *
     * Не «закріплене джерело», а саме історія: людина дивиться то з торента, то з
     * медіасервера, і «останнє, чим грав» відповідає на це краще, ніж один прибитий вибір.
     * Порядок цього списку і є порядок, у якому джерела показуються далі.
     *
     * Профіль у ключі, бо смак у джерелах різний: хто дивиться з торента в 4K і хто з
     * медіасервера «щоб не чекало» — це різні люди на тій самій приставці, і спільна
     * історія переставляла список під того, хто вмикав останнім.
     */
    fun sources(profile: String): Flow<List<String>> = context.recentHistoryDataStore.data
        .map { it[sourcesKey(profile)].decodeList() }

    /**
     * Озвучки, якими людина вмикала, найсвіжіша — першою.
     *
     * Окремо від [sources], бо це різні питання: джерело — це ЧИМ грати (торент,
     * медіасервер), озвучка — ЧИЮ роботу слухати. Людина, яка звикла до однієї студії,
     * хоче її і завтра, і на наступній серії, і байдуже, з торента вона прийде чи з
     * балансера. Прибитого «улюбленого» не заводимо: смак у серіалах різний, а
     * «останнє, чим вмикав» відповідає на це саме без жодного налаштування.
     */
    fun voices(profile: String): Flow<List<String>> = context.recentHistoryDataStore.data
        .map { it[voicesKey(profile)].decodeList() }

    suspend fun rememberVoice(voice: String, profile: String) {
        val trimmed = voice.trim()
        if (trimmed.isEmpty()) return
        push(voicesKey(profile), trimmed) { it.equals(trimmed, ignoreCase = true) }
    }

    /**
     * Підмінити пам'ять озвучок чи джерел тим, що каже профіль на сервері.
     *
     * Сервер — правда, приставка — копія: так за іншим телевізором вмикається та сама
     * студія. `kind` — `voice` або `source`.
     */
    suspend fun replaceRecent(kind: String, profile: String, values: List<String>) {
        val key = if (kind == "voice") voicesKey(profile) else sourcesKey(profile)
        context.recentHistoryDataStore.edit { prefs ->
            prefs[key] = values.filter { it.isNotBlank() }.distinct().take(LIMIT).joinToString(RECORD)
        }
    }

    suspend fun rememberApp(appId: String) = push(KEY_APPS, appId)

    suspend fun rememberSource(host: String, profile: String) =
        push(sourcesKey(profile), host)

    suspend fun rememberChannel(channel: RecentChannel) = push(
        key = KEY_CHANNELS,
        // Поля через `` (unit separator): у назвах каналів трапляються і `|`, і `·`,
        // а цей символ у людському тексті не зустрічається.
        entry = listOf(channel.url, channel.name, channel.image, channel.packageName)
            .joinToString(FIELD),
        // Один канал — один запис, хоч би скільки разів його вмикали.
        sameAs = { it.substringBefore(FIELD) == channel.url },
    )

    override suspend fun remember(query: String) = rememberSearch(query)

    override fun recent(): Flow<List<String>> = searches

    override suspend fun clear() {
        context.recentHistoryDataStore.edit { it.remove(KEY_SEARCHES) }
    }

    suspend fun rememberSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY) return
        push(KEY_SEARCHES, trimmed) { it.equals(trimmed, ignoreCase = true) }
    }

    private suspend fun push(
        key: Preferences.Key<String>,
        entry: String,
        sameAs: (String) -> Boolean = { it == entry },
    ) {
        if (entry.isBlank()) return
        context.recentHistoryDataStore.edit { prefs ->
            val kept = prefs[key].decodeList().filterNot(sameAs)
            prefs[key] = (listOf(entry) + kept).take(LIMIT).joinToString(RECORD)
        }
    }

    private fun String?.decodeList(): List<String> =
        this?.split(RECORD)?.filter { it.isNotBlank() }.orEmpty()

    private fun decodeChannel(raw: String): RecentChannel? {
        val parts = raw.split(FIELD)
        val url = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        return RecentChannel(
            url = url,
            name = parts.getOrNull(1).orEmpty().ifBlank { url },
            image = parts.getOrNull(2).orEmpty(),
            packageName = parts.getOrNull(3).orEmpty(),
        )
    }

    private companion object {
        val KEY_APPS = stringPreferencesKey("recent_apps")
        val KEY_CHANNELS = stringPreferencesKey("recent_channels")
        val KEY_SEARCHES = stringPreferencesKey("recent_searches")
        /**
         * `default` лишається на старому ключі — інакше єдиний профіль, який був до
         * профілів, втратив би свою історію на першому ж запуску.
         */
        fun voicesKey(profile: String): Preferences.Key<String> =
            stringPreferencesKey(
                if (profile.isBlank() || profile == "default") "recent_voices"
                else "recent_voices_$profile",
            )

        fun sourcesKey(profile: String): Preferences.Key<String> =
            if (profile.isBlank() || profile == "default") KEY_SOURCES
            else stringPreferencesKey("recent_sources_$profile")

        val KEY_SOURCES = stringPreferencesKey("recent_sources")

        const val RECORD = "\n"
        const val FIELD = ""

        /** Рядок Головної показує щонайбільше пів десятка карток; більше зберігати нема сенсу. */
        const val LIMIT = 12

        /** Один-два символи — це не запит, а слід від набору. */
        const val MIN_QUERY = 2
    }
}

/**
 * Запис пошукового запиту в історію — окремим вузьким контрактом.
 *
 * Потрібен рівно для того, щоб `SearchViewModel` лишався звичайним JVM-класом: сам
 * [RecentHistoryRepository] тримає `Context` заради DataStore, і його не сконструювати в
 * unit-тесті без Robolectric. Ширшої мети в цього інтерфейсу немає — Хілт зв'язує його з
 * тим самим репозиторієм.
 */
fun interface SearchHistory {
    suspend fun remember(query: String)

    /** Недавні запити, найсвіжіший першим. */
    fun recent(): Flow<List<String>> = flowOf(emptyList())

    suspend fun clear() {}
}
