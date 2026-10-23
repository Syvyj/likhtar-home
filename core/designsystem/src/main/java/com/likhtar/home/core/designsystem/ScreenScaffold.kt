package com.likhtar.home.core.designsystem

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
     * Модифікатор САМОГО вмісту, без меню. Сюди — `onFocusChanged` для «фокус у
     * вмісті»: повішений на [modifier], він бачив і меню, і BACK з меню знову просив
     * фокус у меню — з розділу не можна було вийти на Головну.
     */
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Лише перше завантаження: «оновити» після повернення з тайтла теж вмикає loading, і
    // фокус вистрибував у меню, гублячи повернення на картку.
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(loading) {
        if (loading && firstLoad) menuFocusRequester.requestWithRetry()
        if (!loading) firstLoad = false
    }
    // Власного фону немає навмисно: під усіма розділами лежать спільні шпалери
    // (`WallpaperBackground` у навігації), і суцільна заливка тут їх би сховала.
    Column(modifier = modifier.fillMaxSize()) {
        LikhtarTopMenu(
            current = section,
            onSelect = onSelectSection,
            menuFocusRequester = menuFocusRequester,
            contentFocusRequester = contentFocusRequester,
        )
        Column(modifier = contentModifier.fillMaxWidth().weight(1f), content = content)
    }
}
