package com.likhtar.home.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.scrollBy
import kotlin.math.abs

/**
 * Прокрутка колонок рядів на ТВ — спільна для Кінозалу, Реаліті, Пошуку й Застосунків.
 *
 * Колонка сама не доганяє фокус ([NoBringIntoView]), а ряд у фокусі ставить [centerItem]:
 * з обома разом перехід між рядами йшов двома ривками — «тряска».
 */
const val PIN_TOLERANCE_PX = 2

/**
 * Поставити елемент колонки по центру вікна. Перший ряд — разом із головою сторінки.
 *
 * Лише коли він не на місці: прокрутка під пошуком фокуса дає каскад переходів.
 */
suspend fun LazyListState.centerItem(index: Int, topUntil: Int = 1) {
    if (index <= topUntil) {
        if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset > PIN_TOLERANCE_PX) scrollToItem(0)
        return
    }
    var info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (info == null) {
        scrollToItem(index)
        info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    }
    val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    var delta = info.offset - (viewport - info.size) / 2
    // Кінець колонки по центр не тягнемо: останній ряд піднімався до середини, і під ним
    // зяяла порожнеча. Низ останнього елемента — біля низу вікна, з третиною
    // нижнього запасу колонки.
    val last = layoutInfo.visibleItemsInfo.lastOrNull()
    if (last != null && last.index == layoutInfo.totalItemsCount - 1) {
        val edge = last.offset + last.size - (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding / 3)
        delta = minOf(delta, maxOf(edge, 0))
    }
    if (abs(delta) > PIN_TOLERANCE_PX) scrollBy(delta.toFloat())
}

/**
 * Сітка: крутимо, лише коли картка не влазить, і рівно на стільки. Типова поведінка ТВ
 * тримає фокус на третині екрана — і перша ж картка зсувала голову франшизи з лого.
 */
@OptIn(ExperimentalFoundationApi::class)
val MinimalBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset < 0f -> offset
        offset + size > containerSize -> offset + size - containerSize
        else -> 0f
    }
}

/** Колонка сама не доганяє фокус — це робить пришпилення ряду. */
@OptIn(ExperimentalFoundationApi::class)
val NoBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
