package com.likhtar.home.core.designsystem

import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Чи перекрито вміст оверлеєм (бічна панель, діалог).
 *
 * Оверлей малюється поверх, але для пошуку фокуса він сусід, а не батько: без цього
 * прапорця ↓ з останнього рядка панелі «провалювався» на плитки під нею. Екран з
 * оверлеєм обгортає свій вміст `CompositionLocalProvider(LocalFocusBlocked provides true)`,
 * і всі [FocusableSurface] під ним перестають брати фокус.
 */
val LocalFocusBlocked = compositionLocalOf { false }

/**
 * `focusProperties`, що вимикається разом із [LocalFocusBlocked].
 *
 * Блокування деактивує вузли, а деактивований вузол зникає з реєстру свого
 * `FocusRequester`. Якщо на нього хтось і далі вказує напрямком, Compose кидає
 * «FocusRequester is not initialized» **просто в dispatchKeyEvent** — застосунок падає
 * від звичайного натискання. Ловилося саме так: довге натискання відкривало меню,
 * сітка під ним деактивувалася, і наступна клавіша вбивала процес.
 */
@Composable
fun Modifier.focusDirections(block: FocusProperties.() -> Unit): Modifier =
    if (LocalFocusBlocked.current) this else this.focusProperties(block)

/**
 * Безпечний перехід фокуса на ціль, якої може не бути на екрані.
 *
 * `focusProperties { down = requester }` валить застосунок із
 * `FocusRequester is not initialized` прямо в `dispatchKeyEvent`, щойно ціль не приєднана —
 * а лінивий список приєднує свої елементи лише поки вони видимі. Спіймано на приставці:
 * повернення з «Відкрити всі» лишало Кінозал прокрученим, шапка була не скомпонована, і
 * перше ж натискання клало лаунчер.
 *
 * Тут запит робиться руками й у [runCatching]: не вийшло — фокус іде звичайним пошуком,
 * і найгірше, що буде, це «нічого не сталося».
 */
fun Modifier.focusDown(target: FocusRequester?): Modifier {
    if (target == null) return this
    return this.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown) {
            false
        } else {
            // Не вдалося (ціль ще не приєднана) — false, і фокус іде звичайним пошуком.
            target.tryFocus()
        }
    }
}

/** Те саме, що [focusDown], але для ↑: ціль може бути ще не приєднана, і це не має падати. */
fun Modifier.focusUp(target: FocusRequester?): Modifier {
    if (target == null) return this
    return this.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) {
            false
        } else {
            target.tryFocus()
        }
    }
}

/**
 * Базова фокусована поверхня LIKHTAR: клік/довге натискання з пульта, фокус-кільце
 * акцентним кольором і легке збільшення. Індикацію дотику навмисно вимкнено —
 * єдиний візуальний стан це фокус, а не hover чи press.
 *
 * Фокус — це збільшення й підсвітка самої поверхні, без кольорового кільця навколо:
 * картка трохи виростає, світлішає й отримує тонкий світлий кант. Бірюзова обводка
 * читалась як службова рамка й забивала кадр; світло працює тихіше й на будь-якому тлі.
 *
 * [content] отримує актуальний стан фокуса, щоб змінювати колір тексту всередині картки.
 */
@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(LikhtarDimens.CornerLarge),
    background: Color = LikhtarColors.Surface,
    focusedBackground: Color = LikhtarColors.Surface,
    borderColor: Color = LikhtarColors.Border,
    /** Колір зовнішнього кільця фокуса (теплий білий за замовчуванням; бурштин у режимі переміщення). */
    focusRingColor: Color = LikhtarColors.GlassActive,
    focusedScale: Float = 1.10f,
    /** Підйом сфокусованої поверхні. Для плиток Головної він замінює кільце фокуса. */
    focusedLift: Dp = 0.dp,
    /**
     * Від якої точки поверхня росте. Центр — для всього, що має запас з обох боків.
     *
     * Високій картці з текстом центр не годиться: половина зросту йде вгору, а там уже
     * стоїть заголовок сцени. `TransformOrigin(0.5f, 0f)` пускає весь зріст униз, у
     * порожнє місце під рядом.
     */
    focusOrigin: TransformOrigin = TransformOrigin.Center,
    /**
     * Миттєве перемикання заливки замість плавного.
     *
     * Потрібне щільним спискам: колір тексту й іконок усередині каллер міняє **миттєво**
     * за прапорцем `focused`, а заливка доти перетікала 110 мс. Поки вона на півдорозі,
     * білий значок лежить на майже білому тлі — на швидкому русі рядками це читалося
     * як легке блимання.
     */
    instantFill: Boolean = false,
    /** Наскільки заливка світлішає під фокусом; темним панелям (ТВ Ефір) — менше. */
    focusLighten: Float = FocusLighten,
    /** Примусово тримати поверхню в активному/підсвіченому стані (напр. переміщення плитки). */
    highlighted: Boolean = false,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val blocked = LocalFocusBlocked.current
    val reduceMotion = LocalReduceMotion.current
    val highContrast = LocalHighContrast.current
    val effectiveBg = if (highContrast && background.alpha > 0f) background.copy(alpha = 1f) else background
    val effectiveFocusedBg = if (highContrast && focusedBackground.alpha > 0f) focusedBackground.copy(alpha = 1f) else focusedBackground
    val tapFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var okPressed by remember { mutableStateOf(false) }
    val active = focused || highlighted
    val tiltX = remember { mutableFloatStateOf(0f) }
    val tiltY = remember { mutableFloatStateOf(0f) }
    val grow = remember { Animatable(0f) }
    val pressDip = remember { Animatable(1f) }
    var contentFocused by remember { mutableStateOf(false) }
    LaunchedEffect(active, reduceMotion) {
        if (active) {
            contentFocused = true
            if (reduceMotion) grow.snapTo(1f) else grow.animateTo(1f, tween(FocusGrowMs, easing = ModernFocusEase))
        } else {
            okPressed = false
            tiltX.floatValue = 0f
            tiltY.floatValue = 0f
            if (!reduceMotion) delay(ExitHoldMs)
            contentFocused = false
            if (reduceMotion) grow.snapTo(0f) else grow.animateTo(0f, tween(FocusGrowMs, easing = ModernFocusEase))
        }
    }
    LaunchedEffect(okPressed, reduceMotion) {
        if (reduceMotion) {
            pressDip.snapTo(1f)
        } else {
            pressDip.animateTo(if (okPressed) 0.965f else 1f, tween(90, easing = ModernFocusEase))
        }
    }

    Box(
        propagateMinConstraints = true,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { canFocus = !blocked }
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.keyCode in CENTER_KEY_CODES) {
                    if (ev.type == KeyEventType.KeyDown) okPressed = true
                    else if (ev.type == KeyEventType.KeyUp) okPressed = false
                }
                false
            }
            .clickHandling(onClick = onClick, onLongClick = onLongClick, tapFocus = tapFocus)
            .pointerInput(reduceMotion) {
                if (reduceMotion) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull() ?: continue
                        if (pointer.type != PointerType.Mouse) continue
                        when (event.type) {
                            PointerEventType.Exit -> {
                                tiltX.floatValue = 0f
                                tiltY.floatValue = 0f
                            }
                            PointerEventType.Move -> if (size.width > 0 && size.height > 0) {
                                tiltX.floatValue = -(pointer.position.y / size.height * 2f - 1f).coerceIn(-1f, 1f) * 3f
                                tiltY.floatValue = (pointer.position.x / size.width * 2f - 1f).coerceIn(-1f, 1f) * 3f
                            }
                            else -> Unit
                        }
                    }
                }
            }
            .zIndex(if (active) 1f else 0f)
            // Кільце фокуса — ПОЗА шаром картки: шар знову обрізає вміст сам (clip = true), і другого
            // шару обрізки на кожну картку не треба. Масштаб і підйом кільця — ті самі, що в шару.
            .drawWithContent {
                drawContent()
                val p = grow.value
                if (p > 0f) {
                    val s = (1f + (focusedScale - 1f) * p) * pressDip.value
                    val pivot = Offset(size.width * focusOrigin.pivotFractionX, size.height * focusOrigin.pivotFractionY)
                    val liftPx = focusedLift.toPx() * p
                    withTransform({
                        translate(top = -liftPx)
                        scale(s, s, pivot)
                    }) {
                        drawFocusRing(shape, focusRingColor, p, highlighted)
                    }
                }
            }
            // Масштаб, підйом і 3D-нахил — ПІСЛЯ вузла фокуса, а не перед ним: інакше пошук фокуса
            // бачив зсунутий прямокутник і плутав напрямки D-pad.
            .graphicsLayer {
                val p = grow.value
                val s = (1f + (focusedScale - 1f) * p) * pressDip.value
                scaleX = s
                scaleY = s
                translationY = -focusedLift.toPx() * p
                rotationX = if (reduceMotion) 0f else tiltX.floatValue * p
                rotationY = if (reduceMotion) 0f else tiltY.floatValue * p
                cameraDistance = 850.dp.toPx()
                // Прозора поверхня (обгортка над чужою плашкою) тіні не кидає: інакше тінь читається
                // як друга сіра рамка навколо (поле пошуку Ліхтарика).
                shadowElevation = if (background.alpha == 0f && focusedBackground.alpha == 0f) 0f else 6.dp.toPx() * p
                transformOrigin = focusOrigin
                this.shape = shape
                // Обрізка тут же, а не окремим `.clip(shape)`: той ставив другий шар на кожну картку.
                clip = true
            }
            .drawBehind {
                val p = if (instantFill) (if (active) 1f else 0f) else grow.value
                val outline = shape.createOutline(size, layoutDirection, this)
                val base = lerp(effectiveBg, effectiveFocusedBg, p)
                drawOutline(outline, color = lerp(base, Color.White, focusLighten * p))
                if (borderColor.alpha > 0f) {
                    drawOutline(
                        outline = outline,
                        color = borderColor,
                        style = Stroke(width = LikhtarDimens.BorderWidth.toPx()),
                    )
                }
            },
    ) {
        // Внутрішній Box отримує розмір від зовнішнього (propagateMinConstraints = true на зовнішньому),
        // але сам НЕ форсує minConstraints дітям — інакше Modifier.align(Alignment.Center) усередині
        // карток (OpenAllCard, BrandTile тощо) розтягувалося на всю висоту й прилипало до верху.
        Box {
            content(contentFocused || highlighted)
        }
    }
}

/** Зовнішнє кільце фокуса (і тонка внутрішня лінія) навколо картки форми [shape]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFocusRing(shape: Shape, color: Color, p: Float, highlighted: Boolean) {
    // Прозоре кільце — без кільця. `Transparent.copy(alpha = p)` — це ЧОРНИЙ із прозорістю p:
    // поверхні з вимкненим кільцем (поле пошуку, плитки) малювали чорну рамку.
    if (color.alpha == 0f) return
    val strokeWidth = if (highlighted) 2.dp.toPx() else 1.5.dp.toPx()
    val gap = 1.5.dp.toPx()
    val outset = gap + strokeWidth / 2f
    val innerWidth = 0.5.dp.toPx()
    val corner = (shape as? CornerBasedShape)?.topStart?.toPx(size, this)?.coerceAtMost(size.minDimension / 2f)
    if (corner != null) {
        drawRoundRect(
            color = color.copy(alpha = color.alpha * 0.91f * p),
            topLeft = Offset(innerWidth / 2f, innerWidth / 2f),
            size = Size(size.width - innerWidth, size.height - innerWidth),
            cornerRadius = CornerRadius((corner - innerWidth / 2f).coerceAtLeast(0f)),
            style = Stroke(width = innerWidth),
        )
        drawRoundRect(
            color = color.copy(alpha = color.alpha * p),
            topLeft = Offset(-outset, -outset),
            size = Size(size.width + 2 * outset, size.height + 2 * outset),
            cornerRadius = CornerRadius(corner + outset),
            style = Stroke(width = strokeWidth),
        )
    } else {
        drawOutline(outline = shape.createOutline(size, layoutDirection, this), color = color.copy(alpha = color.alpha * p), style = Stroke(width = strokeWidth))
    }
}

/**
 * Коли наведення миші чи аеромиші переводить фокус (free-my-tv `HomeView` hover):
 *  - лише на ІНШИЙ елемент — рука, що тремтить над карткою, не тягне фокус назад після пульта;
 *  - лише коли вказівник справді зрушив — інакше ряд, що проїхав під нерухомим вказівником,
 *    віддав би фокус наступній картці, та прокрутила б ряд, і так по колу;
 *  - лише на картку, видну цілком: частково видна змусила б ряд прокрутитись.
 */
class HoverFocusGate {
    private var last: Any? = null
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    var lastMs = Long.MIN_VALUE / 2
        private set

    /**
     * [atMs] — час події (uptime). Після фокуса від вказівника [SETTLE_MS] наступні картки його не
     * перехоплюють: прокрутка ряду вбік ще доїжджає, і під рухомим вказівником пропливали б сусіди.
     */
    fun entered(target: Any, x: Float, y: Float, fullyVisible: Boolean, atMs: Long): Boolean {
        if (target === last || !fullyVisible) return false
        if (atMs - lastMs < SETTLE_MS) return false
        if (!lastX.isNaN() && kotlin.math.abs(x - lastX) < MIN_MOVE_PX && kotlin.math.abs(y - lastY) < MIN_MOVE_PX) return false
        last = target
        lastX = x
        lastY = y
        lastMs = atMs
        return true
    }

    private companion object {
        const val MIN_MOVE_PX = 4f
        const val SETTLE_MS = 200L
    }
}

/** Один на весь лаунчер: вказівник теж один. */
private val hoverGate = HoverFocusGate()

/**
 * Фокус щойно поставив вказівник — колонка рядів не підтягує ряд ([pinTop], [centerItem]): під
 * нерухомим вказівником опинявся наступний ряд, він брав фокус, і сторінка їхала ряд за рядом.
 * Сторінку миша гортає колесом.
 */
internal fun focusedByPointer(): Boolean = android.os.SystemClock.uptimeMillis() - hoverGate.lastMs < 300

/** Наведення миші → фокус за правилами [HoverFocusGate]; клік миші — це тап у [touchFocusable]. */
@Composable
private fun Modifier.hoverFocus(requester: FocusRequester): Modifier {
    val target = remember { Any() }
    val coords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { coords[0] = it }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    // Не лише вхід: картка, що підпливла під вказівник, поки сторінка доїжджала (пауза
                    // гейта), бере фокус від першого руху всередині, а не лише після виходу й повторного входу.
                    if ((event.type != androidx.compose.ui.input.pointer.PointerEventType.Enter &&
                            event.type != androidx.compose.ui.input.pointer.PointerEventType.Move) ||
                        change.type != androidx.compose.ui.input.pointer.PointerType.Mouse
                    ) continue
                    val c = coords[0]?.takeIf { it.isAttached } ?: continue
                    val visible = c.boundsInWindow()
                    val full = visible.width >= c.size.width - 1 && visible.height >= c.size.height - 1
                    val at = c.localToWindow(change.position)
                    if (hoverGate.entered(target, at.x, at.y, full, change.uptimeMillis)) runCatching { requester.requestFocus() }
                }
            }
        }
}

/** Обробка кліку поверхні: [dpadClick] (уся історія багів — у його доці) + дотик. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.clickHandling(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    tapFocus: FocusRequester,
): Modifier = this
    .dpadClick(
        onClick = { onClick(); true },
        onLongClick = onLongClick?.let { long -> { long(); true } },
    )
    .touchFocusable(tapFocus, onTap = onClick, onLongPress = onLongClick)
    .hoverFocus(tapFocus)

/**
 * `focusable()`, який розуміє й дотик (телефон, сенсорний екран): тап спершу ставить фокус —
 * як пульт, — і лише тоді [onTap]. Без цього все, що вибирається фокусом (розділи налаштувань,
 * вкладки тайтла, дні погоди), на дотик не перемикалось: фокус від тапу сам не з'являється.
 */
fun Modifier.touchFocusable(
    /** Свій на кожен елемент (`remember { FocusRequester() }`): ним тап і ставить фокус. */
    requester: FocusRequester,
    onTap: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
): Modifier = focusRequester(requester)
    .focusable()
    .pointerInput(onTap, onLongPress) {
        detectTapGestures(
            onTap = { runCatching { requester.requestFocus() }; onTap() },
            onLongPress = { runCatching { requester.requestFocus() }; onLongPress?.invoke() },
        )
    }

/**
 * Клік і утримання центральної кнопки пульта, пораховані **самі**, без `clickable`:
 * він у цій версії Compose клікає на KeyUp без перевірки власного KeyDown (див. нижче).
 *
 * Чому не покладаємось на систему: `FLAG_LONG_PRESS` Android робить із автоповторів
 * клавіші, а деякі пульти повторів не шле взагалі — знято
 * `getevent`'ом: між `KEY_SELECT DOWN` і `KEY_SELECT UP` рівно 1226 мс і жодної події
 * між ними. Тому прапорця не буває ніколи, і меню дій не відкривалося.
 *
 * Запасний шлях «порахувати різницю на KeyUp» теж не годиться: він спрацьовує лише коли
 * кнопку **відпустили**, тобто меню з'являлося б із запізненням на всю тривалість
 * утримання. Тепер таймер: 500 мс — і меню відкрите, поки палець ще на кнопці.
 *
 * Спільний для [FocusableSurface] і кореня [CatalogPage] — тонкі відмінності між ними
 * параметризовані, а не зрівняні:
 *
 * @param onClick короткий клік; повертає, чи подію спожито (корінь каталогу віддає
 *   неспожитий «enter» далі, поверхня завжди споживає).
 * @param onLongClick утримання; повертає, чи воно справді спрацювало — якщо ні,
 *   відпускання все одно зробить короткий клік.
 * @param preview true — перехоплювати на фазі preview (корінь-контейнер мусить бачити
 *   кнопку раніше за нащадків); false — звичайна bubbling-фаза самої поверхні.
 * @param consumeStray чи споживати KeyUp без власного KeyDown. Клікати від чужого
 *   імені не можна ніколи (баг «категорія відкриває Головну»); але корінь каталогу
 *   такий KeyUp глушить, а поверхня віддає далі — раптом його чекає предок.
 */
@Composable
fun Modifier.dpadClick(
    onClick: () -> Boolean,
    onLongClick: (() -> Boolean)? = null,
    preview: Boolean = false,
    consumeStray: Boolean = false,
): Modifier {
    val scope = rememberCoroutineScope()
    val longPress = remember { mutableStateOf<Job?>(null) }
    val fired = remember { mutableStateOf(false) }
    // Чи саме ця поверхня бачила натискання. Без цього чужий KeyUp читається як клік:
    // екран міняється по KeyDown (наприклад, відкривається сітка), фокус переходить на
    // інший елемент, і відпускання тієї самої кнопки прилітає вже туди. Так натискання
    // на картці категорії кликало перший пункт верхнього меню — і лаунчер ішов на Головну.
    val pressed = remember { mutableStateOf(false) }

    val handler: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = handler@{ event ->
        val native = event.nativeKeyEvent
        if (native.keyCode !in CENTER_KEY_CODES) return@handler false

        when (event.type) {
            KeyEventType.KeyDown -> {
                // Повтор утримання, почате на іншому елементі (тримали OK на картці — відкрилось
                // меню — повтори йдуть уже в пункт меню), не наш: інакше пункт вважав би натискання
                // своїм, і відпускання клікнуло б його. Як `tilePress` у lounge.
                if (!ownsPress(native.repeatCount, pressed.value)) return@handler true
                pressed.value = true
                // Прошивки, які прапорець таки шлють, відкривають меню одразу.
                // Прапорець — лише якщо кнопку справді тримають: прошивка цієї приставки ставить його
                // й на фантомні повтори короткого натискання, і меню відкривалось від звичайного OK.
                if (onLongClick != null && native.isLongPress && !fired.value &&
                    native.eventTime - native.downTime >= LONG_PRESS_MS
                ) {
                    longPress.value?.cancel()
                    longPress.value = null
                    android.util.Log.d(LONG_PRESS_TAG, "via FLAG_LONG_PRESS")
                    fired.value = onLongClick()
                } else if (native.repeatCount == 0) {
                    // Свіже натискання: скидаємо слід попереднього. Якщо KeyUp того
                    // утримання з'їв оверлей, `fired` лишався б true — і перший клік
                    // після закриття меню мовчки провалювався б.
                    fired.value = false
                    longPress.value?.cancel()
                    longPress.value = if (onLongClick == null) null else scope.launch {
                        delay(LONG_PRESS_MS)
                        longPress.value = null
                        android.util.Log.d(LONG_PRESS_TAG, "via timer")
                        fired.value = onLongClick()
                    }
                }
                true
            }

            KeyEventType.KeyUp -> {
                // Відпускання без свого натискання — не наше: клавішу почали на
                // іншому елементі, і клікати від його імені ми не маємо права.
                if (!pressed.value) return@handler consumeStray
                pressed.value = false
                longPress.value?.cancel()
                longPress.value = null
                val consumed = if (!fired.value) onClick() else true
                fired.value = false
                consumed
            }

            else -> consumeStray
        }
    }

    return if (preview) this.onPreviewKeyEvent(handler) else this.onKeyEvent(handler)
}

/** Утримання OK: 650 мс — коротке, але «довге» натискання людини не стає довгим OK. */
private const val LONG_PRESS_MS = 650L

/** Тег діагностики пультів: `adb logcat -s LikhtarLongPress` показує, який шлях спрацював. */
private const val LONG_PRESS_TAG = "LikhtarLongPress"

private val CENTER_KEY_CODES = setOf(
    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
    AndroidKeyEvent.KEYCODE_ENTER,
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
)

/** Наскільки світлішає сфокусована поверхня. Більше — і скло перестає бути склом. */
private const val FocusLighten = 0.16f

/**
 * Скільки картка росте під фокусом.
 *
 * На телевізорі відстані фізично більші, ніж на телефоні, тому рух має бути трохи
 * повільнішим за мобільні 150 мс — інакше він не читається, а лише блимає.
 */
private const val FocusGrowMs = 200
private val ModernFocusEase = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/**
 * Скільки картка тримає збільшений стан після втрати фокуса.
 *
 * Два кадри при 60 Гц. Менше — і утримання не читається взагалі, більше — і фокус
 * виглядає «липким»: сусідня картка вже підсвітилась, а ця досі велика.
 */
private const val ExitHoldMs = 24L

/**
 * Жорсткість пружини просідання. Разом із критичним затуханням дає ~170 мс: основна
 * частина шляху проходиться за перші дві третини часу, решта дотягується м'яко.
 */
private const val ExitStiffness = 550f

/** Тінь під сфокусованою карткою: підіймає її над рядом, не малюючи навколо неї лінії. */

/** Чи це натискання OK цього елемента: свіже — так; повтор — лише якщо утримання почалось тут. */
internal fun ownsPress(repeatCount: Int, pressedBefore: Boolean): Boolean = repeatCount == 0 || pressedBefore
