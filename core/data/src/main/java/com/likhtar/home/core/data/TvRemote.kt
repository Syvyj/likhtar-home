package com.likhtar.home.core.data

import android.content.Context
import android.util.Log
import com.likhtar.home.core.adb.LocalAdb
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val REMOTE_KEYS = mapOf(
    "up" to "DPAD_UP", "down" to "DPAD_DOWN", "left" to "DPAD_LEFT", "right" to "DPAD_RIGHT",
    "ok" to "DPAD_CENTER", "back" to "BACK", "menu" to "MENU",
    "playpause" to "MEDIA_PLAY_PAUSE", "rewind" to "MEDIA_REWIND", "forward" to "MEDIA_FAST_FORWARD",
    "volup" to "VOLUME_UP", "voldown" to "VOLUME_DOWN", "mute" to "VOLUME_MUTE",
)

private const val TEXT_MAX = 200

/**
 * Кнопка сторінки «Пульт» → команда ADB. `cmd input`, а не `input`: той щоразу запускає Java-процес
 * (сотні мілісекунд на приставці), а `cmd` — виклик служби. «Дім» сюди не входить: системний HOME
 * через ADB повз службу кнопок відкрив би Google TV, а не Ліхтар.
 */
fun remoteKeyCommand(button: String): String? = REMOTE_KEYS[button]?.let { "cmd input keyevent KEYCODE_$it" }

/**
 * Текст на телевізор (пароль Wi‑Fi, пошук у чужому застосунку). `input text` уміє лише ASCII, пробіл —
 * як `%s`; усе в одинарних лапках, апостроф — `'\''`, тож shell нічого не виконає.
 */
fun remoteTextCommand(text: String): String? {
    if (text.isEmpty() || text.any { it.code !in 0x20..0x7E }) return null
    val escaped = text.take(TEXT_MAX).replace(" ", "%s").replace("'", "'\\''")
    return "cmd input text '$escaped'"
}

/**
 * Пульт для всього телевізора з телефона (Nottilo, lounge): кнопки й текст ідуть через ADB телевізора —
 * лише вже схвалений ключ, без питань на екрані. Одне з'єднання на всі натискання, по черзі.
 */
@Singleton
class TvRemote @Inject constructor(@ApplicationContext private val context: Context) {
    private val queue = Executors.newSingleThreadExecutor { Thread(it, "tv-remote").apply { isDaemon = true } }
    private var adb: LocalAdb? = null

    /** Натиснути кнопку; `false` — ADB телевізора недоступний (не дозволено чи вимкнено). */
    fun press(button: String): Boolean = when (button) {
        "home" -> run("am start -a android.intent.action.MAIN -c android.intent.category.HOME -n ${context.packageName}/com.likhtar.home.MainActivity")
        else -> remoteKeyCommand(button)?.let(::run) ?: false
    }

    /** Надрукувати текст у поле, що зараз у фокусі; `false` — не ASCII чи ADB недоступний. */
    fun type(text: String): Boolean = remoteTextCommand(text)?.let(::run) ?: false

    private fun run(command: String): Boolean = output(command) != null

    /**
     * Виконати команду ADB телевізора й повернути вивід; `null` — ADB недоступний. Та сама черга й
     * те саме з'єднання для пульта й HDMI-CEC ([TvCec]): одне на весь лаунчер.
     */
    fun output(command: String): String? = runCatching {
        queue.submit<String?> {
            repeat(2) { attempt ->
                runCatching {
                    val link = adb ?: LocalAdb.connect(context, QUIET_MS, offerKey = false).also { adb = it }
                    return@submit link.shell(command)
                }.onFailure {
                    runCatching { adb?.close() }
                    adb = null
                    if (attempt == 1) Log.w(TAG, "ADB телевізора недоступний ($it)")
                }
            }
            null
        }.get(WAIT_S, TimeUnit.SECONDS)
    }.getOrNull()

    private companion object {
        const val TAG = "LikhtarRemote"
        const val QUIET_MS = 5_000
        const val WAIT_S = 8L
    }
}
