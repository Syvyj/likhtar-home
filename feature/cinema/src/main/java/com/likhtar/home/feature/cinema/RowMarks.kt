package com.likhtar.home.feature.cinema

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.likhtar.home.core.catalog.TitleMark
import com.likhtar.home.core.catalog.TitleMarksRepository
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.TitleKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Довге OK на картці ряду — «Позначити». Одна реалізація для всіх сторінок з рядами
 * (Кінозал, стрімінги, «Суто українське», Реаліті); сторінка дає лише [onDone] —
 * що зробити після (перезібрати ряди, сказати «Позначено»). ok = false — сервер не відповів.
 */
class RowMarks(
    private val marks: TitleMarksRepository,
    private val scope: CoroutineScope,
    private val onDone: suspend (item: CatalogItem, ok: Boolean) -> Unit,
) {
    data class Target(val item: CatalogItem, val mark: TitleMark? = null)

    private val state = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = state.asStateFlow()

    fun open(item: CatalogItem) {
        state.value = Target(item)
        scope.launch {
            val mark = marks.markOf(item)
            if (state.value?.item?.id == item.id) state.value = Target(item, mark)
        }
    }

    fun close() {
        state.value = null
    }

    fun apply(choice: MarkChoice) {
        val item = state.value?.item ?: return
        close()
        scope.launch {
            val saved = marks.setMark(item, choice.mark, choice.scope, choice.reason, choice.rating, choice.season, choice.episode)
            onDone(item, saved != null)
        }
    }

    fun clear() {
        val item = state.value?.item ?: return
        close()
        scope.launch { onDone(item, marks.clearMark(item)) }
    }
}

/** Картка, яку можна позначити: будь-який твір каталогу, не колекція й не плитка. */
internal fun CatalogItem.markable(): Boolean = tmdbId != 0 && opens.isBlank()

@Composable
internal fun RowMarkPanel(marks: RowMarks) {
    val target by marks.target.collectAsState()
    target?.let { t ->
        MarkPanel(
            title = t.item.title,
            isTv = t.item.kind == TitleKind.TV,
            mark = t.mark,
            season = 0,
            resumeSeason = 0,
            resumeEpisode = 0,
            genres = emptyList(),
            franchise = null,
            onChoice = marks::apply,
            onClear = marks::clear,
            onDismiss = marks::close,
            armed = false,
        )
    }
}
