package com.likhtar.home.core.designsystem

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Повернення фокуса на ряд після Back — спільне для Кінозалу, Реаліті й «Ігор» (було копіями по екранах;
 * у core/designsystem — «Ігри» з feature/apps не бачили його й тримали свою латку).
 *
 * [index] — ряд у фокусі; saveable, бо сторінка тайтла знищує композицію розділу, і зі
 * звичайним remember повернення викидало на початок. [pin] — лічильник, а не індекс:
 * «Відкрити всі» не міняє ні ряду, ні картки, і без нього пришпилювання не спрацьовувало.
 * [tile] — плитка (стрімінг, бренд, жанр), з якої пішли: `railItem` пам'ятає лише картки.
 * [pending] живе рівно одну композицію — щойно фокус повернувся, гасне. [landed] — чи
 * фокус СПРАВДІ став: `requestFocus()` нічого не повертає, а `runCatching` доводить лише
 * те, що вузол причеплений.
 */
@Stable
class RailRestore(
    indexState: MutableState<Int>,
    tileState: MutableState<String?>,
    pendingAtStart: Boolean,
    val focus: FocusRequester,
) {
    var index by indexState
    var pin by mutableIntStateOf(0)
    var tile by tileState
    var pending by mutableStateOf(pendingAtStart)
    var landed by mutableStateOf(false)

    /**
     * Спершу ставимо потрібний ряд у вікно, і лише потім просимо фокус — картка існує лише
     * після того, як LazyColumn скомпонує ряд.
     *
     * Просимо КОЖЕН кадр, а не до першого «успіху»: на нульовому кадрі вузол причеплений,
     * але ще не фокусабельний, запит тихо гине, і розділ лишався без фокуса.
     * Кожен запит тягне bringIntoView, тож ряд повертаємо на місце ПІСЛЯ циклу.
     * [fallback] — саме на випадок, коли картки не знайшлося (ряд зник із каталогу):
     * безумовний виклик забирав фокус у щойно відновленої картки.
     */
    suspend fun restore(rails: LazyListState, fallback: suspend () -> Unit) {
        if (!pending) return
        rails.scrollToItem(index)
        repeat(FOCUS_FRAMES) {
            withFrameNanos {}
            if (landed) return@repeat
            runCatching { focus.requestFocus() }
        }
        rails.centerItem(index, topUntil = 0)
        pending = false
        tile = null
        if (!landed) fallback()
    }
}

/**
 * Точка повернення в ряду — плитка стрімінгу чи жанру, «Відкрити всі»: з неї пішли на іншу
 * сторінку — на неї ж і стає фокус після Back. Одна на всі ряди розділу: досі кожен
 * ряд тримав власну копію (TileRail, GenreRail, CollectionRail), і в жанрах бракувало
 * докрутки ряду — дальня плитка ще не скомпонована, фокусу нікуди стати.
 *
 * [key] — унікальний на розділ, з префіксом ряду («genre:drama», «all:Нові фільми»).
 * Перед переходом — [leave].
 */
fun RailRestore.leave(key: String) {
    tile = key
}

fun Modifier.returnPoint(restore: RailRestore, key: String, rowIndex: Int, onFocus: () -> Unit = {}): Modifier =
    then(if (key == restore.tile) Modifier.focusRequester(restore.focus) else Modifier)
        .onFocusChanged {
            if (!it.isFocused) return@onFocusChanged
            restore.index = rowIndex
            restore.pin += 1
            if (key == restore.tile) restore.landed = true
            onFocus()
        }

/** Ряд докручується до точки повернення: [indexOf] — її позиція в ряду, -1 — її тут немає. */
@Composable
fun RailRestore.ScrollRailTo(rail: LazyListState, indexOf: (String?) -> Int) {
    LaunchedEffect(pending, tile) {
        val at = indexOf(tile)
        if (pending && at >= 0) rail.scrollToItem(at)
    }
}

/** [pending] — чи це повернення, за збереженою плиткою: у рядах і є що відновлювати. */
@Composable
fun rememberRailRestore(
    tileState: MutableState<String?>,
    focus: FocusRequester = remember { FocusRequester() },
    pending: (tile: String?) -> Boolean,
): RailRestore {
    val index = rememberSaveable { mutableStateOf(0) }
    return remember { RailRestore(index, tileState, pending(tileState.value), focus) }
}
