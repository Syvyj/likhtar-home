package com.likhtar.home.feature.cinema

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.likhtar.home.core.designsystem.centerItem
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester

/**
 * Повернення фокуса на ряд після Back — спільне для Кінозалу й Реаліті (було двома копіями).
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
internal class RailRestore(
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
        repeat(com.likhtar.home.core.designsystem.FOCUS_FRAMES) {
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
internal fun RailRestore.leave(key: String) {
    tile = key
}

internal fun Modifier.returnPoint(restore: RailRestore, key: String, rowIndex: Int, onFocus: () -> Unit = {}): Modifier =
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
internal fun RailRestore.ScrollRailTo(rail: LazyListState, indexOf: (String?) -> Int) {
    LaunchedEffect(pending, tile) {
        val at = indexOf(tile)
        if (pending && at >= 0) rail.scrollToItem(at)
    }
}

/** [pending] — чи це повернення, за збереженою плиткою: у рядах і є що відновлювати. */
@Composable
internal fun rememberRailRestore(
    tileState: MutableState<String?>,
    focus: FocusRequester = remember { FocusRequester() },
    pending: (tile: String?) -> Boolean,
): RailRestore {
    val index = rememberSaveable { mutableStateOf(0) }
    return remember { RailRestore(index, tileState, pending(tileState.value), focus) }
}

/**
 * Колонка рядів розділу — одна для Кінозалу й Реаліті: до неї обидва
 * екрани тримали дослівні копії тих самих п'яти ефектів, і правка в одному не доходила до
 * другого.
 *
 * - Ряд у фокусі — по центру: зверху шматок попереднього, знизу наступного. Ключ — лише
 *   ряд і лічильник пришпилювання ([RailRestore.pin] росте на кожен рух фокуса), а
 *   `centerItem` нічого не робить, коли ряд уже на місці: зайвий `scrollToItem` під пошуком
 *   фокуса й зривав каскад (↑ давало п'ять переходів).
 * - Колонка сама фокус не доганяє ([NoBringIntoView]): з обома разом перехід ішов двома
 *   ривками. Ряди всередині мають свій spec.
 * - [ready] — дані приїхали: повертаємо фокус на картку, з якої пішли на сторінку тайтла;
 *   не знайшлося — [onRestoreFallback].
 * - Вийшли з рядів — колонка на початок. Нові дані ([dataKey]) поза рядами й поза
 *   поверненням — теж; у рядах перезбирання (позначка) лишає людину на місці.
 * - Позначена й схована картка фокус не губить — це робить сам LikhtarRail.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun androidx.compose.foundation.layout.ColumnScope.RailsColumn(
    restore: RailRestore,
    inRails: Boolean,
    ready: Boolean,
    onRestoreFallback: suspend (LazyListState) -> Unit,
    dataKey: Any? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val rails = androidx.compose.foundation.lazy.rememberLazyListState()
    // [data-scroll] прототипів: ряд у фокусі стає вгорі колонки, попередні ховаються під
    // меню. Лише коли ряд не на місці — інакше прокрутка під пошуком фокуса дає каскад.
    LaunchedEffect(restore.index, restore.pin, inRails) {
        if (inRails) rails.pinTop(restore.index)
    }
    LaunchedEffect(ready) {
        if (ready) restore.restore(rails) { onRestoreFallback(rails) }
    }
    LaunchedEffect(inRails) { if (!inRails) rails.scrollToItem(0) }
    LaunchedEffect(dataKey) { if (!restore.pending && !inRails) rails.scrollToItem(0) }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides com.likhtar.home.core.designsystem.NoBringIntoView,
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = rails,
            // Знизу лише .shelf padding-bottom (60 px; було 34): останні ряди вгору не дотягуються й
            // стоять біля низу — під плитками стрімінгів не зяє порожнеча.
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = com.likhtar.home.core.designsystem.LikhtarDimens.ScreenPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 30.dp),
            // .shelf: padding-bottom 60 px.
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(30.dp),
            content = content,
        )
    }
}

/**
 * Back на сторінці розділу з рядами — однаковий у Кінозалі й Реаліті: із рядів перший тап
 * згортає їх до слайдера ([onCollapseRails]), зі сторінки другий веде у верхнє меню, а
 * третій (уже з меню) обробляє навігація застосунку — вихід із розділу.
 *
 * [enabled] — екран може вимкнути, поки відкрите щось своє поверх (радник у Кінозалі).
 */
@Composable
internal fun SectionBackHandler(
    inRails: Boolean,
    contentHasFocus: Boolean,
    menuFocus: FocusRequester,
    onCollapseRails: () -> Unit,
    enabled: Boolean = true,
) {
    androidx.activity.compose.BackHandler(enabled = enabled && (inRails || contentHasFocus)) {
        if (inRails) onCollapseRails() else runCatching { menuFocus.requestFocus() }
    }
}

/** Поставити ряд [index] угорі колонки (анімацією .35s, як у прототипі), якщо він ще не там. */
internal suspend fun androidx.compose.foundation.lazy.LazyListState.pinTop(index: Int) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (info != null && kotlin.math.abs(info.offset) <= com.likhtar.home.core.designsystem.PIN_TOLERANCE_PX) return
    if (index == 0 && firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= com.likhtar.home.core.designsystem.PIN_TOLERANCE_PX) return
    animateScrollToItem(index)
}
