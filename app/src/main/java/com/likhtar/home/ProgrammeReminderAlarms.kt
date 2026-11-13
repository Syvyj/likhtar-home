package com.likhtar.home

import android.content.Context
import android.util.Log
import com.likhtar.home.core.data.ProgrammeReminders
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Показує нагадування про передачі за хвилину до початку — поверх будь-чого (служба кнопок).
 * Живе в процесі лаунчера, а список — на диску: після перезапуску процесу все стає знову.
 *
 * ponytail: таймери в процесі, не AlarmManager; приставка в глибокому сні може показати пізніше —
 * тоді й дивитись нікому.
 */
@Singleton
class ProgrammeReminderAlarms @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminders: ProgrammeReminders,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun watch() {
        scope.launch {
            reminders.reminders.collectLatest { list ->
                coroutineScope {
                    list.forEach { r ->
                        launch {
                            val wait = r.startMillis - LEAD_MS - System.currentTimeMillis()
                            if (wait > 0) delay(wait)
                            // Процес спав, поки передача давно йде, — пізно, просто прибираємо.
                            if (System.currentTimeMillis() < r.startMillis + LATE_MS) {
                                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(r.startMillis))
                                val shown = HomeKeyService.showReminder(context.getString(R.string.live_reminder_text, r.channel, time, r.title))
                                if (!shown) Log.w(TAG, "нагадування не показано: служба кнопок вимкнена")
                            }
                            reminders.remove(r.key)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "LikhtarReminders"
        const val LEAD_MS = 60_000L
        const val LATE_MS = 5 * 60_000L
    }
}
