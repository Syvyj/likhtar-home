package com.likhtar.home.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import com.likhtar.home.core.model.LikhtarSection

/**
 * Каркас екрана з верхнім меню. Використовують усі розділи, крім Головної
 * (у неї власна ambient-композиція без меню).
 */
@Composable
fun LikhtarScreenScaffold(
    section: LikhtarSection,
    onSelectSection: (LikhtarSection) -> Unit,
    menuFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester? = null,
    /**
     * Екран вантажиться і фокусу взяти нічим: фокус — на поточному розділі в меню.
     * Інакше система віддавала його першому пункту, «Головна», і OK під час завантаження
     * кидав на Головну (ТВ Ефір, Реаліті, Кінозал).
     */
    loading: Boolean = false,
    /**
     * Показувати верхню шапку. Коли користувач спустився в ряди (`inRails`), шапка плавно
     * ховається (як tab bar у tvOS), а ↑ / Back повертають її на екран.
     */
    showTopBar: Boolean = true,
    /**
     * Модифікатор САМОГО вмісту, без меню. Сюди — `onFocusChanged` для «фокус у
     * вмісті»: повішений на [modifier], він бачив і меню, і BACK з меню знову просив
     * фокус у меню — з розділу не можна було вийти на Головну.
     */
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    // Лише перше завантаження: «оновити» після повернення з тайтла теж вмикає loading, і
    // фокус вистрибував у меню, гублячи повернення на картку.
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(loading, showTopBar) {
        if (loading && firstLoad && showTopBar) menuFocusRequester.requestWithRetry()
        if (!loading) firstLoad = false
    }
    // Власного фону немає навмисно: під усіма розділами лежать спільні шпалери
    // (`WallpaperBackground` у навігації), і суцільна заливка тут їх би сховала.
    // Back як у tvOS: з вмісту — спершу на вкладку розділу в меню, а вже з меню — далі (на Головну).
    // Екрани зі своїми рівнями (ряди Кінозалу → герой) оголошують BackHandler у вмісті — він
    // пізніший і має перевагу.
    var contentHasFocus by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = contentHasFocus && showTopBar) {
        runCatching { menuFocusRequester.requestFocus() }
    }
    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showTopBar,
            enter = if (reduceMotion) EnterTransition.None
            else fadeIn(tween(220, easing = FastOutSlowInEasing)) + expandVertically(tween(260, easing = FastOutSlowInEasing)),
            exit = if (reduceMotion) ExitTransition.None
            else fadeOut(tween(160, easing = FastOutSlowInEasing)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing)),
        ) {
            LikhtarTopMenu(
                current = section,
                onSelect = onSelectSection,
                menuFocusRequester = menuFocusRequester,
                contentFocusRequester = contentFocusRequester,
            )
        }
        Column(
            modifier = contentModifier.onFocusChanged { contentHasFocus = it.hasFocus }.fillMaxWidth().weight(1f),
            content = content,
        )
    }
}
