package com.likhtar.home.core.data

import android.content.Context
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Кнопки пульта: людина «ловить» свою кнопку й дає їй дію. Таблиці під конкретний пульт тут
 * немає навмисно — у кожного пульта свої коди (на G20 Netflix — `STEM_1`, на іншому — щось
 * своє), тож ключ — код, який служба побачила від цього пульта.
 */
enum class KeyPress { SHORT, LONG }

sealed interface KeyAction {
    data class App(val packageName: String) : KeyAction
    /** Розділ LIKHTAR за маршрутом ([com.likhtar.home.core.model.LikhtarSection.route]). */
    data class Section(val route: String) : KeyAction
    /** Сторінка стрімінгу в Кінозалі за ключем сервера (`netflix`, `amazon`…). */
    data class Streaming(val brand: String) : KeyAction
    /** Голосовий асистент LIKHTAR. */
    data object Assistant : KeyAction
}

data class KeyBinding(val keyCode: Int, val press: KeyPress, val action: KeyAction)

object RemoteKeys {
    /**
     * Стрілки, OK і Back не перепризначаються: інакше пульт легко стає непридатним і з
     * налаштувань не вибратись. HOME — завжди LIKHTAR.
     */
    val RESERVED = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_HOME,
    )

    /**
     * Довше — довге натискання. Не за `repeatCount`: прошивка приставки шле фантомні
     * автоповтори вже на 194 мс короткого кліку.
     */
    const val LONG_PRESS_MS = 600L

    internal fun encode(b: KeyBinding): String = "${b.keyCode}|${b.press}|" + when (val a = b.action) {
        is KeyAction.App -> "app|${a.packageName}"
        is KeyAction.Section -> "section|${a.route}"
        is KeyAction.Streaming -> "streaming|${a.brand}"
        KeyAction.Assistant -> "assistant|-"
    }

    internal fun decode(raw: String): KeyBinding? {
        val parts = raw.split('|')
        if (parts.size != 4) return null
        val code = parts[0].toIntOrNull() ?: return null
        val press = runCatching { KeyPress.valueOf(parts[1]) }.getOrNull() ?: return null
        val action = when (parts[2]) {
            "app" -> KeyAction.App(parts[3])
            "section" -> KeyAction.Section(parts[3])
            "streaming" -> KeyAction.Streaming(parts[3])
            "assistant" -> KeyAction.Assistant
            else -> return null
        }
        return KeyBinding(code, press, action)
    }
}

private val Context.remoteKeysStore: DataStore<Preferences> by preferencesDataStore(name = "likhtar_remote_keys")
private val KEY_BINDINGS = stringSetPreferencesKey("bindings")

@Singleton
class RemoteKeysRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val bindings: Flow<List<KeyBinding>> = context.remoteKeysStore.data.map { prefs ->
        prefs[KEY_BINDINGS].orEmpty().mapNotNull(RemoteKeys::decode).sortedWith(compareBy({ it.keyCode }, { it.press }))
    }

    /** [action] `null` — віддати кнопку назад системі. */
    suspend fun set(keyCode: Int, press: KeyPress, action: KeyAction?) {
        context.remoteKeysStore.edit { prefs ->
            val kept = prefs[KEY_BINDINGS].orEmpty().filterNot { raw ->
                RemoteKeys.decode(raw)?.let { it.keyCode == keyCode && it.press == press } ?: true
            }
            prefs[KEY_BINDINGS] = kept.toSet() + listOfNotNull(action?.let { RemoteKeys.encode(KeyBinding(keyCode, press, it)) })
        }
    }

    /** Прибрати кнопку з обома діями. */
    suspend fun forget(keyCode: Int) {
        set(keyCode, KeyPress.SHORT, null)
        set(keyCode, KeyPress.LONG, null)
    }
}

/**
 * «Зловити кнопку»: поки [listening], служба не виконує наступне натискання, а віддає його
 * код сюди. Служба й екран налаштувань живуть в одному процесі — їм досить синглтона.
 */
@Singleton
class RemoteKeyCatcher @Inject constructor() {
    private val listeningState = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = listeningState.asStateFlow()
    private val caughtFlow = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val caught: SharedFlow<Int> = caughtFlow.asSharedFlow()

    fun start() { listeningState.value = true }
    fun stop() { listeningState.value = false }

    fun catch(keyCode: Int) {
        listeningState.value = false
        caughtFlow.tryEmit(keyCode)
    }
}

/**
 * Коротке чи довге — за часом від DOWN. Без довгої дії коротка спрацьовує одразу на DOWN;
 * з довгою — на UP (раніше не відрізнити), довга — щойно кнопку тримають [RemoteKeys.LONG_PRESS_MS].
 * Пару DOWN/UP забираємо цілою: віддати системі лише половину — це «утримання».
 */
class RemotePressTracker {
    private var downAt = 0L
    private var longDone = false

    /** Що виконати на цю подію: `null` — нічого (подію все одно забираємо). */
    fun onEvent(
        down: Boolean,
        repeatCount: Int,
        eventTime: Long,
        short: KeyAction?,
        long: KeyAction?,
    ): KeyAction? {
        if (down) {
            if (repeatCount == 0) {
                downAt = eventTime
                longDone = false
                return if (long == null) short else null
            }
            if (long != null && !longDone && eventTime - downAt >= RemoteKeys.LONG_PRESS_MS) {
                longDone = true
                return long
            }
            return null
        }
        if (long == null || longDone) return null
        longDone = true
        return if (eventTime - downAt >= RemoteKeys.LONG_PRESS_MS) long else short
    }
}
