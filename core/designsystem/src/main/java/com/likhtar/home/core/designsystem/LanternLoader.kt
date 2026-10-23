package com.likhtar.home.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Ліхтар, що світить, — наш індикатор очікування.
 *
 * Знак дихає й світить теплим колом, навколо нього пливуть дві світлові орбіти — задня
 * половина кожної йде за ліхтарем, передня перед ним, — і мерехтять іскри. Періоди різні,
 * щоб рух не сходився в такт і не читався механічним.
 *
 * Усе — одне полотно на кадр, без blur і RenderEffect (його немає до API 31, а на
 * приставці з гігабайтом розмиття дорожче, ніж дає): світло — радіальні градієнти, слід
 * орбіти — десятки коротких відрізків із наростаючою яскравістю.
 */
@Composable
fun LanternLoader(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
) {
    val transition = rememberInfiniteTransition(label = "lantern")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATHE_MS), RepeatMode.Reverse),
        label = "breathe",
    )
    val float by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(FLOAT_MS), RepeatMode.Reverse),
        label = "float",
    )
    // Фаза орбіт і іскор: 0…1 за оберт, лінійно й без кінця.
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(ORBIT_MS, easing = LinearEasing)),
        label = "spin",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Позаду: світло і задні половини орбіт.
        Canvas(modifier = Modifier.size(size)) {
            val c = Offset(this.size.width / 2f, this.size.height * GLOW_CENTER_Y)
            val r = this.size.minDimension * (GLOW_MIN + (GLOW_MAX - GLOW_MIN) * breathe) / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(GlowColor.copy(alpha = 0.34f + 0.3f * breathe), GlowColor.copy(alpha = 0f)),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
            Orbits.forEach { drawOrbit(it, spin, front = false) }
        }
        LikhtarIcons.Lantern(
            size = size * LANTERN_FRACTION,
            modifier = Modifier.graphicsLayer {
                translationY = -FLOAT_TRAVEL_DP * float * density
                val scale = 0.985f + 0.015f * breathe
                scaleX = scale
                scaleY = scale
            },
        )
        // Спереду: передні половини орбіт і іскри.
        Canvas(modifier = Modifier.size(size)) {
            Orbits.forEach { drawOrbit(it, spin, front = true) }
            drawSparks(spin)
        }
    }
}

/**
 * Ліхтар по центру з підписом, що змінюється, — екран очікування.
 *
 * [title] — чого чекаємо («Кінозал»); `null` — лише ліхтар і фрази. Фрази змінюються
 * кожні [PHRASE_MS]: одна застигла фраза на довгому завантаженні читається як «зависло».
 */
@Composable
fun LanternLoader(
    title: String?,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    phrases: List<String> = emptyList(),
) {
    val defaults = stringArrayResource(R.array.ds_loader_phrases).toList()
    val lines = remember(subtitle, phrases, defaults) {
        (listOfNotNull(subtitle) + phrases.ifEmpty { defaults }).distinct()
    }
    var index by remember(lines) { mutableIntStateOf(0) }
    LaunchedEffect(lines) {
        while (lines.size > 1) {
            delay(PHRASE_MS)
            index = (index + 1) % lines.size
        }
    }
    // Точно по центру — сам ліхтар, а не вся група: тексти висять під ним, а зверху стоїть
    // порожнеча тієї ж висоти.
    val textBlock = if (title != null) TextBlockWithTitle else TextBlockPhraseOnly
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(textBlock))
        LanternLoader()
        Column(
            modifier = Modifier.height(textBlock),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Top),
        ) {
        title?.let {
            Text(text = it, style = LikhtarType.SectionTitle, color = LikhtarColors.TextPrimary, textAlign = TextAlign.Center)
        }
        AnimatedContent(
            targetState = lines.getOrNull(index).orEmpty(),
            transitionSpec = {
                (fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 3 }) togetherWith
                    (fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 3 })
            },
            label = "phrase",
        ) { line ->
            Text(text = line, style = LikhtarType.Body1, color = LikhtarColors.TextPrimary, textAlign = TextAlign.Center)
        }
        }
    }
}

/** Висота блоку текстів під ліхтарем (і порожнечі над ним). */
private val TextBlockWithTitle = 84.dp
private val TextBlockPhraseOnly = 48.dp

/** Екран очікування: ліхтар із фразами рівно по центру, без заголовка вгорі. */
@Composable
fun LanternLoaderScreen(
    modifier: Modifier = Modifier,
    title: String? = null,
    phrases: List<String> = emptyList(),
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LanternLoader(title = title, phrases = phrases)
    }
}

/** Орбіта: еліпс навколо ліхтаря (частки ширини), нахил, швидкість (цілі оберти за цикл — інакше стрибок на зламі циклу) і зсув фази. */
private class Orbit(val rx: Float, val ry: Float, val tilt: Float, val speed: Float, val phase: Float, val width: Float)

private val Orbits = listOf(
    Orbit(rx = 0.46f, ry = 0.13f, tilt = -14f, speed = 1f, phase = 0f, width = 3.4f),
    Orbit(rx = 0.40f, ry = 0.10f, tilt = 10f, speed = -1f, phase = 0.4f, width = 2.6f),
)

/**
 * Слід орбіти: від голови назад на [TRAIL] оберту, яскравість гасне до хвоста. Точка
 * «позаду» ліхтаря — верхня половина еліпса (sin < 0), її малює задній шар; передній — решту.
 */
private fun DrawScope.drawOrbit(o: Orbit, spin: Float, front: Boolean) {
    val cx = size.width / 2f
    val cy = size.height * 0.56f
    val rx = size.width * o.rx
    val ry = size.width * o.ry
    val tilt = o.tilt * PI.toFloat() / 180f
    val head = (spin * o.speed + o.phase) * 2f * PI.toFloat()
    fun point(a: Float): Offset {
        val x = rx * cos(a)
        val y = ry * sin(a)
        return Offset(cx + x * cos(tilt) - y * sin(tilt), cy + x * sin(tilt) + y * cos(tilt))
    }
    val dir = if (o.speed >= 0) -1f else 1f
    val step = TRAIL * 2f * PI.toFloat() / SEGMENTS
    for (i in 0 until SEGMENTS) {
        val a0 = head + dir * step * i
        val a1 = head + dir * step * (i + 1)
        if ((sin(a0) >= 0f) != front) continue
        val fade = 1f - i / SEGMENTS.toFloat()
        drawLine(
            color = TrailColor.copy(alpha = fade * (0.35f + 0.65f * fade)),
            start = point(a0),
            end = point(a1),
            strokeWidth = o.width * density * (0.4f + 0.6f * fade),
            cap = StrokeCap.Round,
        )
    }
    if ((sin(head) >= 0f) == front) {
        val p = point(head)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White, TrailColor.copy(alpha = 0f)), center = p, radius = 7f * density),
            radius = 7f * density,
            center = p,
        )
    }
}

/** Іскри: сталі місця навколо ліхтаря, мерехтять і повільно підіймаються. */
private fun DrawScope.drawSparks(spin: Float) {
    Sparks.forEach { (x, y, phase) ->
        val t = (spin * 2f + phase) % 1f
        val alpha = sin(t * PI.toFloat()).coerceAtLeast(0f)
        val p = Offset(size.width * x, size.height * (y - 0.08f * t))
        drawCircle(TrailColor.copy(alpha = 0.85f * alpha), radius = (1.2f + 1.3f * alpha) * density, center = p)
    }
}

private val Sparks = listOf(
    Triple(0.16f, 0.50f, 0.0f), Triple(0.82f, 0.42f, 0.3f), Triple(0.28f, 0.74f, 0.55f),
    Triple(0.72f, 0.70f, 0.15f), Triple(0.10f, 0.30f, 0.7f), Triple(0.90f, 0.62f, 0.85f),
    Triple(0.36f, 0.22f, 0.45f), Triple(0.62f, 0.84f, 0.65f), Triple(0.22f, 0.60f, 0.9f),
    Triple(0.78f, 0.26f, 0.05f),
)

private const val BREATHE_MS = 1_800
private const val FLOAT_MS = 2_400
private const val ORBIT_MS = 3_600
private const val PHRASE_MS = 2_200L
private const val FLOAT_TRAVEL_DP = 4f
private const val LANTERN_FRACTION = 0.46f
private const val GLOW_CENTER_Y = 0.54f
private const val GLOW_MIN = 0.7f
private const val GLOW_MAX = 0.92f
private const val TRAIL = 0.34f
private const val SEGMENTS = 36

/** Тепле світло ліхтаря — з логотипа. */
private val GlowColor = LikhtarColors.LanternFlame
private val TrailColor = LikhtarColors.LanternTrail

/**
 * Чи тримати лоадер: доки вантажиться І доки не пройшов мінімальний час.
 *
 * Без цього на кешованих даних екран блимав анімацією на пів кадру, а ряди «доїжджали»
 * на очах — на телевізорі це читається як ривок, а не як швидкість.
 *
 * Плата чесна: коли дані вже в пам'яті, ми свідомо чекаємо. Тому [minMs] — одне число в
 * одному місці: якщо відчуватиметься млявим, зменшувати треба тут.
 */
@Composable
fun rememberLoaderHold(loading: Boolean, minMs: Long = LOADER_MIN_MS): Boolean {
    // Saveable, а не remember: розділ іде з композиції, коли поверх відкривають картку
    // фільму, і після Back прапорець народжувався знову — лоадер ще дві секунди
    // перекривав уже готову сторінку, і Кінозал виглядав так, ніби вантажиться наново.
    var held by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (!held) return@LaunchedEffect
        delay(minMs)
        held = false
    }
    return loading || held
}

/** Скільки лоадер тримається щонайменше. Дві секунди. */
const val LOADER_MIN_MS = 2_000L

/**
 * Повноекранне «готую» поверх екрана, поки картка «Продовжити» шукає джерело. Одне на всі
 * екрани: Головна, Кінозал, Реаліті, історія вмикають однаково й показують те саме.
 */
@Composable
fun PreparingScrim(
    visible: Boolean,
    title: String = stringResource(R.string.ds_preparing_title),
    subtitle: String = stringResource(R.string.ds_preparing_subtitle),
) {
    if (!visible) return
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .then(Modifier.background(LikhtarColors.BackgroundDeep.copy(alpha = 0.82f))),
        contentAlignment = Alignment.Center,
    ) {
        LanternLoader(title = title, subtitle = subtitle)
    }
}
