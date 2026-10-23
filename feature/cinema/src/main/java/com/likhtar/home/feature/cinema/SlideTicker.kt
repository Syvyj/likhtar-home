package com.likhtar.home.feature.cinema

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Автогортання слайдера — однакове в Кінозалі, Реаліті й ТВ Ефірі.
 *
 * Слайд стоїть вісім секунд (встигнути прочитати два рядки опису), а вибраний стрілкою —
 * двадцять: звичні вісім перегортали його з-під носа. Поки [paused] —
 * людина в рядах чи в раднику — таймер іде, але не гортає.
 */
internal class SlideTicker(
    private val scope: CoroutineScope,
    private val paused: () -> Boolean,
    private val onTick: () -> Unit,
) {
    private var job: Job? = null

    fun restart(manual: Boolean = false) {
        job?.cancel()
        job = scope.launch {
            if (manual) delay(MANUAL_PAUSE_MS - AUTO_SLIDE_MS)
            while (true) {
                delay(AUTO_SLIDE_MS)
                if (!paused()) onTick()
            }
        }
    }

    private companion object {
        const val AUTO_SLIDE_MS = 8_000L
        const val MANUAL_PAUSE_MS = 20_000L
    }
}
