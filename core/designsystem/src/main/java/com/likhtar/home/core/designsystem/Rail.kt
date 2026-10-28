package com.likhtar.home.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset

/**
 * Горизонтальний рядок карток.
 *
 * Існує заради однієї речі: LazyRow обрізає себе по власних межах уздовж осі прокрутки,
 * а фокус-кільце LIKHTAR малюється **зовні** картки — тому в звичайному LazyRow кільце
 * першої та останньої картки зрізалося б рівно навпіл. Рядок розширюється на товщину
 * кільця в обидва боки й тим самим відступом усередині повертає картки на місце,
 * тож зовні верстка не змінюється. Поперек осі прокрутки Compose і так дозволяє
 * малювати за межами, тому вертикально нічого робити не треба.
 *
 * Друге правило — однакове для кожного ряду лаунчера: вхід згори чи знизу — на першу
 * картку, а пішов фокус із ряду — ряд повертається на початок. Екранам нічого для цього робити не треба.
 *
 * Третє: убік з ряду фокус не виходить. Біля краю ряду ← чи → нікуди не веде — інакше
 * платформа шукала найближчий вузол по всьому екрану й кидала на верхнє меню (→ з єдиної
 * плитки «Ігор» у Застосунках) чи на кнопки настрою в Пошуку.
 * Вкладки тайтла на цьому й тримаються: `moveFocus` на краю тепер чесно каже «нікуди».
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun LikhtarRail(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    gap: Dp = LikhtarDimens.RailGap,
    content: LazyListScope.() -> Unit,
) {
    // Сфокусована картка не притискається до краю екрана: за нею лишається смуга, у якій
    // видно початок наступної. Без цього людина, дійшовши вправо, не має жодної ознаки, що
    // ряд триває, — картка просто впирається в рамку телевізора.
    CompositionLocalProvider(LocalBringIntoViewSpec provides SafeZone) {
        val first = remember { FocusRequester() }
        val keeper = remember { FocusKeeper() }
        // Картку з фокусом прибрали з ряду (позначили переглянутою — і вона сховалась): фокус
        // на сусідню на тому ж місці. Без цього він зникав зовсім, пульт мовчав до Back, а
        // розділ, вирішивши, що з рядів пішли, забував, куди повертатись.
        LaunchedEffect(keeper.lost) {
            val pos = keeper.lost ?: return@LaunchedEffect
            withFrameNanos { }
            val count = state.layoutInfo.totalItemsCount
            if (count > 0) {
                keeper.target = pos.coerceAtMost(count - 1)
                keeper.landed = false
                // Просимо щокадру, поки картка сама не скаже, що фокус у неї: «запит не впав»
                // ще не значить «фокус став» — на щойно скомпонованій картці він тихо гине.
                repeat(FOCUS_FRAMES) {
                    if (keeper.landed) return@repeat
                    runCatching { keeper.refocus.requestFocus() }
                    withFrameNanos { }
                }
            }
            keeper.target = -1
            keeper.lost = null
        }
        // По колу: з останньої картки «вправо» — на першу, з першої «вліво» — на останню.
        val wrap = remember { RowWrap() }
        var wrapTo by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(wrapTo) {
            val to = wrapTo ?: return@LaunchedEffect
            state.scrollToItem(to)
            keeper.target = to
            keeper.landed = false
            repeat(FOCUS_FRAMES) {
                if (keeper.landed) return@repeat
                runCatching { keeper.refocus.requestFocus() }
                withFrameNanos { }
            }
            keeper.target = -1
            wrapTo = null
        }
        val edges = remember(state) { RailEdges(wrap, count = { state.layoutInfo.totalItemsCount }, jump = { wrapTo = it }) }
        var left by remember { mutableStateOf(false) }
        var hadFocus by remember { mutableStateOf(false) }
        LaunchedEffect(left) {
            if (left) {
                state.scrollToItem(0)
                left = false
            }
        }
        LazyRow(
            state = state,
            modifier = modifier
                .bleedHorizontally(LikhtarDimens.FocusBleed)
                .onPreviewKeyEvent(wrap::onKey)
                .focusGroup()
                .focusProperties {
                    enter = { direction ->
                        if (direction == FocusDirection.Up || direction == FocusDirection.Down) first else FocusRequester.Default
                    }
                    exit = { direction ->
                        if (direction == FocusDirection.Left || direction == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default
                    }
                }
                // Лише коли фокус справді БУВ у ряду: onFocusChanged кличеться й при появі ряду
                // (без фокуса), і ряд, щойно прокручений до плитки повернення, одразу
                // відмотувався назад — фокус не мав куди стати.
                .onFocusChanged {
                    if (it.hasFocus) hadFocus = true else if (hadFocus) {
                        hadFocus = false
                        left = true
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(horizontal = LikhtarDimens.FocusBleed),
            content = { FirstMarked(this, first, keeper, edges).content() },
        )
    }
}

/**
 * Скільки ряд прокручує, щойно фокус пішов за межу видимого.
 *
 * Стандартна поведінка доводить картку рівно до краю й зупиняється. Ми додаємо запас
 * [SafeZoneFraction] від ширини ряду з того боку, куди рухаємось, — тоді за сфокусованою
 * карткою завжди визирає наступна.
 */
@OptIn(ExperimentalFoundationApi::class)
private val SafeZone = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * SafeZoneFraction
        val leading = offset - margin
        val trailing = offset + size + margin - containerSize
        return when {
            leading < 0f -> leading
            trailing > 0f -> trailing
            else -> 0f
        }
    }
}

/** Чверть ряду — приблизно одна картка запасу на типовій сітці Кінозалу. */
private const val SafeZoneFraction = 0.25f

/** Дає вузлу [amount] додаткового простору зліва й справа, не займаючи його в розкладці. */
private fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = extra * 2))
    layout((placeable.width - extra * 2).coerceAtLeast(0), placeable.height) {
        placeable.place(-extra, 0)
    }
}

/**
 * Обгортка рядка, що чіпляє [first] до першого елемента, хоч би як екран його додав
 * (`item`, `items`, `itemsIndexed` — усі зводяться до цих двох викликів). Запит фокуса на
 * обгортці віддає фокус першій фокусованій картці всередині.
 */
private class FirstMarked(
    private val scope: LazyListScope,
    private val first: FocusRequester,
    private val keeper: FocusKeeper,
    private val edges: RailEdges,
) : LazyListScope by scope {
    /** Позиція наступного елемента в ряду: `item` і `items` можуть чергуватись. */
    private var next = 0

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        val pos = next++
        val first = first
        val keeper = keeper
        val edges = edges
        scope.item(key, contentType) { RailSlot(pos, first, keeper, edges) { content() } }
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        val start = next
        next += count
        val first = first
        val keeper = keeper
        val edges = edges
        scope.items(count, key, contentType) { index -> RailSlot(start + index, first, keeper, edges) { itemContent(index) } }
    }
}

/** Перша картка — вхід у ряд; кожна — точка, куди повернути фокус, якщо сусідню прибрали. */
@Composable
private fun RailSlot(pos: Int, first: FocusRequester, keeper: FocusKeeper, edges: RailEdges, content: @Composable () -> Unit) {
    var focusedHere by remember { mutableStateOf(false) }
    // Прибрану картку Compose спершу позбавляє фокуса, а тоді викидає. Тож «щойно втратила
    // фокус і за мить зникла» — це картку прибрали з ряду. Прокрутка так не робить: вона
    // викидає картку, з якої фокус пішов давно (вийшли з ряду — він відмотується на початок).
    DisposableEffect(Unit) {
        onDispose {
            if (keeper.blurred == pos && android.os.SystemClock.uptimeMillis() - keeper.blurredAt < REMOVED_WITHIN_MS) keeper.lost = pos
        }
    }
    Box(
        Modifier
            // Крайня картка: окреме «вліво/вправо» за край — на протилежний кінець ряду.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val count = edges.count()
                val to = when {
                    count < 2 -> null
                    event.key == Key.DirectionLeft && pos == 0 -> count - 1
                    event.key == Key.DirectionRight && pos == count - 1 -> 0
                    else -> null
                } ?: return@onPreviewKeyEvent false
                if (edges.wrap.deliberate) edges.jump(to)
                true
            }
            .then(if (pos == 0) Modifier.focusRequester(first) else Modifier)
            .then(if (pos == keeper.target) Modifier.focusRequester(keeper.refocus) else Modifier)
            .onFocusChanged {
                if (focusedHere && !it.hasFocus) {
                    keeper.blurred = pos
                    keeper.blurredAt = android.os.SystemClock.uptimeMillis()
                } else if (it.hasFocus) {
                    keeper.blurred = -1
                    if (pos == keeper.target) keeper.landed = true
                }
                focusedHere = it.hasFocus
            },
    ) { content() }
}

/** Куди повернути фокус ряду, коли картку з ним прибрали: [lost] — її місце, [target] — сусідня. */
@Stable
private class FocusKeeper {
    val refocus = FocusRequester()
    var lost by mutableStateOf<Int?>(null)
    var target by mutableStateOf(-1)
    var landed = false
    /** Картка, з якої фокус щойно пішов, і коли. */
    var blurred = -1
    var blurredAt = 0L
}

/** Скільки між «фокус пішов» і «картку викинули» вважати одним прибиранням. */
private const val REMOVED_WITHIN_MS = 300L

/**
 * Перехід ряду по колу лише на окреме натискання: утримана стрілка (і фантомні повтори, що
 * шле прошивка приставки на короткий клік) зупиняються на краю, а не крутять ряд без кінця.
 * Рахуємо час між двома останніми натисканнями «вліво/вправо»: перше привело фокус на край,
 * друге — те, що хоче за край.
 */
class RowWrap {
    private var previous = 0L
    private var last = 0L

    fun onKey(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)) {
            previous = last
            last = android.os.SystemClock.uptimeMillis()
        }
        return false
    }

    val deliberate: Boolean get() = last - previous > WRAP_PAUSE_MS
}

/** Ряд для крайніх карток: скільки в ньому карток і як перейти на іншу за індексом. */
private class RailEdges(val wrap: RowWrap, val count: () -> Int, val jump: (Int) -> Unit)

/** Пауза на краю, після якої «ще раз вправо» — це вже бажання перейти на початок. */
private const val WRAP_PAUSE_MS = 350L
