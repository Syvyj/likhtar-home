package com.likhtar.home.core.data

/** Медіасесія чужого застосунку, як її бачить плашка «Що грає». [state] — `PlaybackState.STATE_*`. */
data class MediaSnapshot(val pkg: String, val state: Int, val title: String, val artist: String)

private const val STATE_PAUSED = 2
private const val STATE_PLAYING = 3
private const val STATE_BUFFERING = 6
private const val STATE_CONNECTING = 8
private val PLAYING_STATES = setOf(STATE_PLAYING, STATE_BUFFERING, STATE_CONNECTING)

/** Скільки після останнього «грає» плашка лишається на паузі — щоб із неї можна було продовжити. */
private const val PAUSED_KEEP_MS = 30 * 60_000L

/**
 * Яку сесію показати у верхньому меню (free-my-tv `NowPlaying`, lounge): першу, що грає
 * (буферизація теж — інакше рядок блимає); якщо не грає ніхто — ту, що стала на паузу недавно
 * і яку ми бачили граючою ([playedAt]: пакет → коли востаннє грав), щоб пауза з плашки не
 * прибирала саму плашку. Давні паузи (YouTube тримає сесію годинами) — ні.
 */
fun pickNowPlaying(sessions: List<MediaSnapshot>, playedAt: Map<String, Long>, now: Long): MediaSnapshot? {
    val named = sessions.filter { it.title.isNotBlank() }
    return named.firstOrNull { it.state in PLAYING_STATES }
        ?: named.firstOrNull { it.state == STATE_PAUSED && playedAt[it.pkg]?.let { now - it <= PAUSED_KEEP_MS } == true }
}

/** Чи сесія зараз грає (для `playedAt`). */
fun MediaSnapshot.isPlaying(): Boolean = state in PLAYING_STATES

/**
 * Доступ до сповіщень для служби-слухача (без нього `MediaSessionManager.getActiveSessions` не дає
 * чужих сесій). Видаємо самі через ADB телевізора — лише вже схвалений ключ, без питань на
 * екрані (`cmd notification allow_listener`, як у free-my-tv). Немає ключа — плашки просто немає.
 */
@javax.inject.Singleton
class NotificationListenerGrant @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    fun granted(component: android.content.ComponentName): Boolean =
        android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            .orEmpty().split(':').any { android.content.ComponentName.unflattenFromString(it) == component }

    /** Лише з фонового потоку: ADB — мережа. */
    fun ensure(component: android.content.ComponentName): Boolean {
        if (granted(component)) return true
        runCatching {
            com.likhtar.home.core.adb.LocalAdb.connect(context, 5_000, offerKey = false).use { adb ->
                adb.shell("cmd notification allow_listener '${component.flattenToString()}'")
            }
        }.onFailure { android.util.Log.w("LikhtarNowPlaying", "доступ до сповіщень не видано: $it") }
        return granted(component)
    }
}
