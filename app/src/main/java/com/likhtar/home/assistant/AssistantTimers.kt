package com.likhtar.home.assistant

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.likhtar.home.HomeKeyService
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Таймер сну й нагадування асистента. Живуть у процесі лаунчера — домашній екран тримається
 * завжди.
 *
 * ponytail: таймери не переживають перезапуску процесу (оновлення APK, ребут); знадобиться —
 * AlarmManager із приймачем.
 */
object AssistantTimers {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepJob: Job? = null

    /** Вимкнути (приспати) приставку через [minutes]; 0 — зараз. Новий таймер заміняє старий. */
    fun sleepIn(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = scope.launch {
            delay(minutes.coerceAtLeast(0) * 60_000L)
            Log.i(TAG, "таймер сну")
            HomeKeyService.sleepNow()
        }
    }

    /**
     * Нагадати [text] о [at]: «21:00» — найближчі 21:00 (сьогодні чи завтра), «+30» — через
     * 30 хвилин. Не розібрали час — `false`.
     */
    fun remind(at: String, text: String): Boolean {
        val delayMs = delayUntil(at) ?: return false
        scope.launch {
            delay(delayMs)
            Log.i(TAG, "нагадування: $text")
            HomeKeyService.showReminder(text)
        }
        return true
    }

    internal fun delayUntil(at: String, now: ZonedDateTime = ZonedDateTime.now()): Long? {
        val s = at.trim()
        if (s.startsWith("+")) return s.drop(1).trim().toLongOrNull()?.times(60_000)
        val m = Regex("""(\d{1,2})(?:[:.](\d{2}))?""").find(s) ?: return null
        val hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (hour > 23 || minute > 59) return null
        var target = now.with(LocalTime.of(hour, minute)).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return java.time.Duration.between(now, target).toMillis()
    }

    /** Гучність приставки: up | down | mute. Через HDMI-CEC система передає її телевізору. */
    fun volume(context: Context, how: String) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val direction = when (how) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> return
        }
        // Крок системи дрібний: на «гучніше» — два кроки.
        repeat(if (direction == AudioManager.ADJUST_TOGGLE_MUTE) 1 else 2) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }
    }

    private const val TAG = "LikhtarAssistant"
}
