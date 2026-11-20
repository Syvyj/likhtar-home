package com.likhtar.home.feature.cinema

import com.likhtar.home.core.designsystem.pinTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.likhtar.home.core.designsystem.centerItem
import com.likhtar.home.core.designsystem.glideBy
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
import com.likhtar.home.core.designsystem.RailRestore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester

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
            // Знизу лише .shelf padding-bottom (60 px; було 34 — після +20 % карток): останні ряди вгору не дотягуються й
            // стоять біля низу — під плитками стрімінгів не зяє порожнеча (0d2c41e).
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = com.likhtar.home.core.designsystem.LikhtarDimens.ScreenPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 30.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
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
