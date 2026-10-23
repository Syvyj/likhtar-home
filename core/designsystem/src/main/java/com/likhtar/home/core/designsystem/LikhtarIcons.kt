package com.likhtar.home.core.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Іконки доку Головної.
 *
 * Мальовані кодом, а не векторними ресурсами: у проєкті немає жодного `drawable`-вектора,
 * усе графічне — Canvas (`AppsGridIcon`, `SkyIcon`, `SkyIcon` погоди). Тримаємо той самий
 * підхід, щоб не заводити другий спосіб робити те саме.
 *
 * Усі фігури описані на сітці 24×24 і масштабуються під запитаний розмір, тому один і той
 * самий набір однаково лягає і в плитку доку, і в дрібніший контекст.
 *
 * Товщина лінії теж масштабується: на TV іконку видно з трьох метрів, і константа в dp
 * на великій плитці виглядала б волосиною, а на дрібній — кляксою.
 */
object LikhtarIcons {

    /**
     * Ліхтар LIKHTAR HOME — майстер-знак (фірмова картинка). Растр 512 px заввишки — з запасом і для завантажувача, і для кнопки.
     *
     * [onLight] — знак на світлому (сфокусована кнопка чи плитка): білий корпус там
     * зливався з тлом, лишалось саме віконце. Підкладаємо м'яку темну тінь-ореол.
     */
    @Composable
    fun Lantern(size: Dp, modifier: Modifier = Modifier, onLight: Boolean = false) =
        Image(
            painter = painterResource(R.drawable.likhtar_lantern),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .then(
                    if (onLight) {
                        Modifier.drawBehind {
                            drawOval(
                                brush = Brush.radialGradient(
                                    listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                                    center = center,
                                    radius = this.size.minDimension * 0.62f,
                                ),
                                topLeft = Offset(this.size.width * 0.12f, 0f),
                                size = Size(this.size.width * 0.76f, this.size.height),
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        )

    /** Кінозал — хлопавка. */
    @Composable
    fun Cinema(tint: Color, size: Dp, modifier: Modifier = Modifier) =
        IconCanvas(size, modifier) { u, w ->
            val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
            // Корпус.
            drawRoundRect(
                color = tint,
                topLeft = Offset(3f * u, 9.5f * u),
                size = Size(18f * u, 10.5f * u),
                cornerRadius = CornerRadius(2f * u, 2f * u),
                style = stroke,
            )
            // Верхня планка під кутом.
            val slate = Path().apply {
                moveTo(3.2f * u, 9.3f * u)
                lineTo(5.1f * u, 4.6f * u)
                lineTo(21.2f * u, 6.6f * u)
                lineTo(20.8f * u, 9.3f * u)
                close()
            }
            drawPath(slate, tint, style = stroke)
            // Смуги на планці: дві коротких діагоналі, як на справжній хлопавці.
            listOf(9.6f to 5.2f, 14.6f to 5.8f).forEach { (x, y) ->
                drawLine(
                    color = tint,
                    start = Offset(x * u, y * u),
                    end = Offset((x - 1.6f) * u, 9.0f * u),
                    strokeWidth = w,
                    cap = StrokeCap.Round,
                )
            }
        }

    /** ТБ Ефір — телевізор з антеною. */
    @Composable
    fun LiveTv(tint: Color, size: Dp, modifier: Modifier = Modifier) =
        IconCanvas(size, modifier) { u, w ->
            val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawRoundRect(
                color = tint,
                topLeft = Offset(2.8f * u, 8.2f * u),
                size = Size(18.4f * u, 12.2f * u),
                cornerRadius = CornerRadius(2.6f * u, 2.6f * u),
                style = stroke,
            )
            // Антена: дві лінії від верхнього краю екрана вгору.
            drawLine(tint, Offset(12f * u, 8.0f * u), Offset(8.2f * u, 3.9f * u), w, StrokeCap.Round)
            drawLine(tint, Offset(12f * u, 8.0f * u), Offset(15.8f * u, 3.9f * u), w, StrokeCap.Round)
        }

    /** Смарт дім — будинок із Wi-Fi усередині. */
    @Composable
    fun SmartHome(tint: Color, size: Dp, modifier: Modifier = Modifier) =
        IconCanvas(size, modifier) { u, w ->
            val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val house = Path().apply {
                moveTo(2.9f * u, 11.2f * u)
                lineTo(12f * u, 3.6f * u)
                lineTo(21.1f * u, 11.2f * u)
                moveTo(5.1f * u, 10.2f * u)
                lineTo(5.1f * u, 20.3f * u)
                lineTo(18.9f * u, 20.3f * u)
                lineTo(18.9f * u, 10.2f * u)
            }
            drawPath(house, tint, style = stroke)
            // Wi-Fi: дві дуги й крапка, центр — трохи нижче середини стіни.
            val cx = 12f * u
            val cy = 17.4f * u
            listOf(4.6f, 2.6f).forEach { r ->
                drawArc(
                    color = tint,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - r * u, cy - r * u),
                    size = Size(2 * r * u, 2 * r * u),
                    style = stroke,
                )
            }
            drawCircle(tint, radius = w * 0.75f, center = Offset(cx, cy - 0.2f * u))
        }

    /** Пошук — лупа. */
    @Composable
    fun Search(tint: Color, size: Dp, modifier: Modifier = Modifier) =
        IconCanvas(size, modifier) { u, w ->
            drawCircle(
                color = tint,
                radius = 6.4f * u,
                center = Offset(10.4f * u, 10.4f * u),
                style = Stroke(width = w),
            )
            drawLine(
                color = tint,
                start = Offset(15.1f * u, 15.1f * u),
                end = Offset(20.2f * u, 20.2f * u),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
        }

    /**
     * Шеврон униз — «нижче є ще».
     *
     * Мальований, а не гліфом `⌄`: у шрифті ця закарлючка сидить у своєму знакомісці низько,
     * і поруч із підписом розʼїжджається по висоті, скільки не вирівнюй текстові прямокутники.
     * На сітці 24×24 вона рівно по центру, тож рядок «стрілка — підпис — стрілка» сходиться
     * сам.
     */
    @Composable
    fun ChevronDown(tint: Color, size: Dp, modifier: Modifier = Modifier) =
        IconCanvas(size, modifier) { u, w ->
            drawLine(
                color = tint,
                start = Offset(6.5f * u, 9.5f * u),
                end = Offset(12f * u, 15f * u),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = tint,
                start = Offset(12f * u, 15f * u),
                end = Offset(17.5f * u, 9.5f * u),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
        }

    /** Застосунки — сітка 2×2, єдина заповнена іконка набору. */
    @Composable
    fun Apps(tint: Color, size: Dp, modifier: Modifier = Modifier) =
        IconCanvas(size, modifier) { u, _ ->
            val side = 7.6f * u
            val gap = 2.4f * u
            val start = (24f * u - side * 2 - gap) / 2f
            listOf(start, start + side + gap).forEach { x ->
                listOf(start, start + side + gap).forEach { y ->
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(x, y),
                        size = Size(side, side),
                        cornerRadius = CornerRadius(1.9f * u, 1.9f * u),
                    )
                }
            }
        }

    /**
     * Погода на Головній — лінійний набір у тому ж стилі, що й док.
     *
     * Окремий від [SkyIcon]: той малює м'які заливні хмари для бічної панелі, а тут
     * потрібен тонкий контур, який стоїть в одному ряду з рештою іконок екрана.
     *
     * [night] розводить ясну погоду на сонце й місяць. У моделі погоди ознаки ночі немає,
     * тому її рахує сам екран за годиною прогнозу — інакше о 21:00 світило б сонце.
     */
    @Composable
    fun Sky(
        sky: com.likhtar.home.core.model.Sky,
        night: Boolean,
        tint: Color,
        size: Dp,
        modifier: Modifier = Modifier,
        // Погодний символ у віджеті великий і мусить лишатися тонким: та сама товщина,
        // що й у дрібних значків доку, зробила б із нього жирну пляму.
        weight: Float = 1f,
    ) =
        IconCanvas(size, modifier, weight) { u, w ->
            val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (sky) {
                com.likhtar.home.core.model.Sky.CLEAR ->
                    if (night) drawMoon(tint, u, stroke) else drawSun(tint, u, w, stroke)
                com.likhtar.home.core.model.Sky.CLOUDY -> drawCloud(tint, u, stroke)
                com.likhtar.home.core.model.Sky.RAIN -> {
                    drawCloud(tint, u, stroke)
                    listOf(9.2f, 12f, 14.8f).forEach { x ->
                        drawLine(
                            color = tint,
                            start = Offset(x * u, 17.8f * u),
                            end = Offset((x - 0.9f) * u, 20.8f * u),
                            strokeWidth = w,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }

    /** Налаштування — шестерня. */
    @Composable
    fun Settings(tint: Color, size: Dp, modifier: Modifier = Modifier) =
        IconCanvas(size, modifier) { u, w ->
            val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val cx = 12f * u
            val cy = 12f * u
            // Кільце корпусу й отвір усередині: без отвору шестерня читається як ґудзик.
            drawCircle(tint, radius = 6.2f * u, center = Offset(cx, cy), style = stroke)
            drawCircle(tint, radius = 2.4f * u, center = Offset(cx, cy), style = stroke)
            // Зубці — короткі й товсті, впритул до кільця. Довгі тонкі промені робили
            // з шестерні сонце: рівно це й було видно на екрані.
            repeat(8) { i ->
                val a = Math.toRadians(i * 45.0)
                val cos = Math.cos(a).toFloat()
                val sin = Math.sin(a).toFloat()
                drawLine(
                    color = tint,
                    start = Offset(cx + 5.9f * u * cos, cy + 5.9f * u * sin),
                    end = Offset(cx + 8.4f * u * cos, cy + 8.4f * u * sin),
                    strokeWidth = w * 1.7f,
                    cap = StrokeCap.Butt,
                )
            }
        }
}

private fun DrawScope.drawSun(tint: Color, u: Float, w: Float, stroke: Stroke) {
    drawCircle(tint, radius = 4.6f * u, center = Offset(12f * u, 12f * u), style = stroke)
    // Промені короткі й із помітним відступом від кола: саме цей проміжок робить символ
    // «легким», а не схожим на сонце з дитячого малюнка.
    repeat(8) { i ->
        val a = Math.toRadians(i * 45.0)
        val cos = Math.cos(a).toFloat()
        val sin = Math.sin(a).toFloat()
        drawLine(
            color = tint,
            start = Offset(12f * u + 7.4f * u * cos, 12f * u + 7.4f * u * sin),
            end = Offset(12f * u + 9.8f * u * cos, 12f * u + 9.8f * u * sin),
            strokeWidth = w,
            cap = StrokeCap.Round,
        )
    }
}

/** Місяць — серп: коло з вирізом, зроблений однією дугою, а не двома колами. */
private fun DrawScope.drawMoon(tint: Color, u: Float, stroke: Stroke) {
    val moon = Path().apply {
        moveTo(19.2f * u, 15.4f * u)
        cubicTo(15.2f * u, 17.2f * u, 10.4f * u, 15.4f * u, 8.8f * u, 11.4f * u)
        cubicTo(7.6f * u, 8.4f * u, 8.4f * u, 5.4f * u, 10.4f * u, 3.6f * u)
        cubicTo(5.2f * u, 4.4f * u, 2.4f * u, 9.8f * u, 4.6f * u, 14.6f * u)
        cubicTo(6.8f * u, 19.4f * u, 13.2f * u, 20.8f * u, 17f * u, 18.2f * u)
        cubicTo(18f * u, 17.5f * u, 18.7f * u, 16.5f * u, 19.2f * u, 15.4f * u)
        close()
    }
    drawPath(moon, tint, style = stroke)
}

private fun DrawScope.drawCloud(tint: Color, u: Float, stroke: Stroke) {
    val cloud = Path().apply {
        moveTo(7.4f * u, 16.6f * u)
        cubicTo(4.6f * u, 16.6f * u, 3f * u, 14.6f * u, 3.6f * u, 12.4f * u)
        cubicTo(4.1f * u, 10.6f * u, 5.9f * u, 9.6f * u, 7.6f * u, 9.9f * u)
        cubicTo(8.2f * u, 6.9f * u, 11.4f * u, 5.2f * u, 14.2f * u, 6.4f * u)
        cubicTo(16.6f * u, 7.4f * u, 17.8f * u, 9.9f * u, 17.2f * u, 12.2f * u)
        cubicTo(19.6f * u, 12.2f * u, 21f * u, 14.4f * u, 20f * u, 16f * u)
        cubicTo(19.4f * u, 16.9f * u, 18.5f * u, 16.6f * u, 17.6f * u, 16.6f * u)
        close()
    }
    drawPath(cloud, tint, style = stroke)
}

/**
 * Полотно іконки: віддає лямбді масштаб сітки 24×24 (`u`) і товщину лінії (`w`).
 *
 * Товщина взята від розміру, а не константою: 2.1/24 — та пропорція, на якій лінія
 * читається і в плитці доку (40dp), і в дрібному контекстному рядку (22dp).
 */
@Composable
private fun IconCanvas(
    size: Dp,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    draw: DrawScope.(unit: Float, stroke: Float) -> Unit,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 24f
        draw(u, u * 2.1f * weight)
    }
}
