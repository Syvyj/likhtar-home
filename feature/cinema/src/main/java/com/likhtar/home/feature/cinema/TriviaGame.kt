package com.likhtar.home.feature.cinema

import com.likhtar.home.core.catalog.TriviaDay
import com.likhtar.home.core.catalog.TriviaRepository
import com.likhtar.home.core.catalog.TriviaStatement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Що на панелі гри зараз. [inRound] — питання з раунду дня, а не «ще питання». */
data class TriviaUi(
    val day: TriviaDay = TriviaDay(),
    val shown: TriviaStatement? = null,
    val answered: Boolean? = null,
    val inRound: Boolean = false,
)

/**
 * Хід гри для екрана: [withRound] — Ліхтарик (спершу раунд дня, далі — ще питання), без нього —
 * герой Кінозалу (одразу випадкові). Стан раунду й бачене — у [TriviaRepository].
 */
class TriviaGame(
    private val repo: TriviaRepository,
    private val scope: CoroutineScope,
    private val withRound: Boolean,
) {
    private val ui = MutableStateFlow(TriviaUi())
    val state: StateFlow<TriviaUi> = ui.asStateFlow()

    /** Перечитати (вхід на екран): опівночі раунд новий. Питання, на яке не відповіли, лишається. */
    fun load() = scope.launch {
        val day = repo.load()
        val cur = ui.value
        if (cur.shown != null && cur.answered == null && (!cur.inRound || cur.day.date == day.date)) {
            ui.update { it.copy(day = day) }
            return@launch
        }
        show(day)
    }

    fun answer(truth: Boolean) = scope.launch {
        val cur = ui.value
        val s = cur.shown ?: return@launch
        if (cur.answered != null) return@launch
        if (cur.inRound) repo.answerRound(truth == s.truth) else repo.markSeen(s)
        ui.update { it.copy(answered = truth, day = repo.day.value) }
    }

    fun next() = scope.launch { show(repo.day.value) }

    private suspend fun show(day: TriviaDay) {
        val round = withRound && !day.done && day.current != null
        val s = if (round) day.current else repo.random()
        ui.value = TriviaUi(day = day, shown = s, answered = null, inRound = round)
    }
}
