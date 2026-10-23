package com.likhtar.home.core.designsystem

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
            // requestFocus() тут повертає Unit і кидає виняток, коли ціль не приєднана, —
            // тому «вдалося» це саме відсутність винятку.
            runCatching { target.requestFocus() }.isSuccess
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
            runCatching { target.requestFocus() }.isSuccess
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
    // Кільця фокуса більше немає (фокус показують масштаб, тінь і світло), але параметр
    // лишається: його досі передають екрани у feature/*, і чіпати їх звідси не можна.
    @Suppress("UNUSED_PARAMETER") focusRingColor: Color = LikhtarColors.Accent,
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
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val blocked = LocalFocusBlocked.current
    var focused by remember { mutableStateOf(false) }
    /**
     * Ріст картки під фокусом анімується, кольори — ні.
     *
     * Анімацію тут уже прибирали, і не даремно: одна спільна clock на 80 мс змушувала
     * **перезбиратись** усі видимі картки під час швидких повторів D-pad, і ввід ставав у
     * чергу за декоративними кадрами. Причина була не в анімації, а в тому, що значення
     * читалося під час композиції — `.scale()` бере його саме там.
     *
     * Тут те саме значення читається всередині лямбди `graphicsLayer`, тобто у фазі
     * відмальовки. Композиція не запускається взагалі, перемальовується лише шар картки.
     * Заливка йде тим самим шляхом — у `drawBehind`, — і теж не коштує композиції.
     */
    val grow = remember { Animatable(0f) }
    /**
     * Вихід із фокуса не є дзеркалом входу.
     *
     * Симетричний `tween` в обидві сторони давав рівномірне просідання, і на приставці воно
     * читалось як механічне: картка «падає» назад у ряд з тією самою швидкістю, з якою
     * росла. Реальний рух складніший — і саме його ми й повторюємо:
     *
     *   1. коротке утримання ([ExitHoldMs]) — масштаб іще мить тримає збільшений стан,
     *      тобто відпускає картку не в той самий кадр, у якому пішов фокус;
     *   2. основне просідання — швидке;
     *   3. останні відсотки — м'яко сідають на місце.
     *
     * Кроки 2 і 3 дає критично затухаюча пружина: overshoot нульовий (жодного «відскоку»
     * на TV бути не має), але швидкість спадає нерівномірно, як у фізичного руху. Вхід
     * лишається `tween`: там рівномірність доречна — картка мусить піднятись рівно за той
     * час, за який око встигає перевести погляд.
     */
    /**
     * Прапорець для вмісту: кольори тексту й значків живуть у каллері й міняються стрибком.
     *
     * На вході стрибок збігається з початком росту, а на виході мусить збігтися з початком
     * просідання — інакше вміст «відпускає» фокус на два кадри раніше за саму картку, і
     * рух розшаровується: біла плитка ще тут, а підпис уже ні. Тому та сама пауза
     * [ExitHoldMs], що тримає масштаб.
     */
    var contentFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            contentFocused = true
            grow.animateTo(1f, tween(FocusGrowMs, easing = FastOutSlowInEasing))
        } else {
            delay(ExitHoldMs)
            contentFocused = false
            grow.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = ExitStiffness,
                ),
            )
        }
    }

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { canFocus = !blocked }
            .clickHandling(onClick = onClick, onLongClick = onLongClick)
            // Масштаб і підйом — ПІСЛЯ вузла фокуса, а не перед ним: інакше пошук фокуса
            // бачив зсунутий угору й збільшений прямокутник, сусіди виходили «нижче», і ↓
            // їздив уздовж ряду — у доці Головної, рядах Застосунків (на приставці).
            .graphicsLayer {
                val p = grow.value
                val s = 1f + (focusedScale - 1f) * p
                scaleX = s
                scaleY = s
                translationY = -focusedLift.toPx() * p
                // Тіні під фокусом немає: на приставці вона коштувала ~8 мс
                // на найповільніших кадрах, а на теплому тлі картку й так виділяють масштаб,
                // підйом і світлішання заливки.
                transformOrigin = focusOrigin
                this.shape = shape
                // Обрізка тут же, а не окремим `.clip(shape)`: той ставив другий шар на
                // кожну картку, а RenderThread приставки й так ледь вкладається в кадр.
                clip = true
            }
            // Заливка і рамка ведуть той самий прогрес, що й масштаб.
            //
            // Заливка перемикалась стрибком, і на виході лишався кадр, де плитка вже
            // скляна, а ще збільшена — на приставці це читалось як сходинка. `instantFill`
            // при цьому не працював: обидві гілки того `if` давали однакове миттєве
            // значення, тобто «плавної» гілки просто не було.
            //
            // Рамка ж не анімувалась зовсім: вона малювалась окремим модифікатором зі
            // сталим кольором, і на темному тлі **проявлялась** уже після того, як фон
            // згас. Виходило дві події замість одної: «зник фон» і «повернувся бордер».
            // Тому вона тут же, у фазі відмальовки, і гасне в міру росту фокуса: під
            // фокусом сфокусоване виділяється масштабом, тінню й світлом, а не контуром —
            // у жодному ряду Apple TV обведення немає.
            .drawBehind {
                val p = if (instantFill) (if (focused) 1f else 0f) else grow.value
                val outline = shape.createOutline(size, layoutDirection, this)
                val base = lerp(background, focusedBackground, p)
                // Підсвітка: та сама заливка, зведена до світла, а не окремий шар.
                drawOutline(outline, color = lerp(base, Color.White, focusLighten * p))
                if (borderColor.alpha > 0f) {
                    // Рамка НЕ бере участі в анімації фокуса.
                    //
                    // Спершу вона гасла під фокусом і поверталась на виході — і на
                    // напівпрозорих поверхнях це давало саме те, від чого ми тікали: у
                    // картки погоди `background` і `focusedBackground` однакові, фон під
                    // фокусом не змінюється, тому єдиною видимою подією ставала поява
                    // бордера після згасання підсвітки. Крива (корінь степеня) цього не
                    // лікувала: вона міняла лише те, КОЛИ друга подія трапиться.
                    //
                    // Тепер межа поверхні стала — вона частина форми, а не сигнал. Фокус
                    // показують масштаб, підйом, тінь і світлішання заливки, і всі вони
                    // ведуть один прогрес, тобто рух один.
                    drawOutline(
                        outline = outline,
                        color = borderColor,
                        style = Stroke(width = LikhtarDimens.BorderWidth.toPx()),
                    )
                }
            },
    ) {
        content(contentFocused)
    }
}

/** Обробка кліку поверхні: [dpadClick] (уся історія багів — у його доці) + дотик. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.clickHandling(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier = this
    .dpadClick(
        onClick = { onClick(); true },
        onLongClick = onLongClick?.let { long -> { long(); true } },
    )
    .focusable()
    .pointerInput(onClick, onLongClick) {
        detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick?.invoke() })
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
                pressed.value = true
                // Прошивки, які прапорець таки шлють, відкривають меню одразу.
                if (onLongClick != null && native.isLongPress && !fired.value) {
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

private const val LONG_PRESS_MS = 500L

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

