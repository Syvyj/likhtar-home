package com.likhtar.home.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Мінімалістична векторна іконка мікрофона у стилі лаунчера. */
@Composable
fun MicrophoneIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val strokeW = 1.5.dp.toPx()
        val capW = w * 0.38f
        val capH = h * 0.52f
        val capTop = h * 0.08f
        drawRoundRect(
            color = color,
            topLeft = Offset(cx - capW / 2f, capTop),
            size = Size(capW, capH),
            cornerRadius = CornerRadius(capW / 2f, capW / 2f),
        )
        val arcRadius = w * 0.32f
        val arcTop = capTop + capH * 0.28f
        val arcRect = Rect(cx - arcRadius, arcTop, cx + arcRadius, arcTop + arcRadius * 2f)
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
        val stemBottom = h * 0.92f
        drawLine(color, Offset(cx, arcRect.bottom), Offset(cx, stemBottom), strokeW, StrokeCap.Round)
        val baseW = w * 0.44f
        drawLine(color, Offset(cx - baseW / 2f, stemBottom), Offset(cx + baseW / 2f, stemBottom), strokeW, StrokeCap.Round)
    }
}

/**
 * Значки пристроїв розумного дому — тією ж лінією, що й значки доку: сітка 24, штрих 1.8.
 * Мікрофон вище переїхав сюди з Ліхтарика, бо голос тепер є й у Смарт домі.
 */
object DeviceIcons {
    @Composable
    fun Bulb(tint: Color, size: Dp, modifier: Modifier = Modifier) = Icon(size, modifier) { u, s ->
        val bulb = Path().apply {
            moveTo(9f * u, 16.5f * u)
            cubicTo(9f * u, 14f * u, 5.5f * u, 12.5f * u, 5.5f * u, 9f * u)
            cubicTo(5.5f * u, 5.4f * u, 8.4f * u, 2.8f * u, 12f * u, 2.8f * u)
            cubicTo(15.6f * u, 2.8f * u, 18.5f * u, 5.4f * u, 18.5f * u, 9f * u)
            cubicTo(18.5f * u, 12.5f * u, 15f * u, 14f * u, 15f * u, 16.5f * u)
            close()
        }
        drawPath(bulb, tint, style = s)
        drawLine(tint, Offset(9.5f * u, 19.6f * u), Offset(14.5f * u, 19.6f * u), s.width, StrokeCap.Round)
        drawLine(tint, Offset(10.6f * u, 22f * u), Offset(13.4f * u, 22f * u), s.width, StrokeCap.Round)
    }

    @Composable
    fun Power(tint: Color, size: Dp, modifier: Modifier = Modifier) = Icon(size, modifier) { u, s ->
        drawArc(
            color = tint, startAngle = -60f, sweepAngle = 300f, useCenter = false,
            topLeft = Offset(4f * u, 4.5f * u), size = Size(16f * u, 16f * u), style = s,
        )
        drawLine(tint, Offset(12f * u, 2.6f * u), Offset(12f * u, 11.5f * u), s.width, StrokeCap.Round)
    }

    @Composable
    fun Fan(tint: Color, size: Dp, modifier: Modifier = Modifier) = Icon(size, modifier) { u, s ->
        // Обідок і три лопаті від осі — читається з дивана, на відміну від кривих лопатей.
        drawCircle(tint, radius = 9.5f * u, center = Offset(12f * u, 12f * u), style = s)
        listOf(-90.0, 30.0, 150.0).forEach { deg ->
            val r = Math.toRadians(deg)
            drawLine(
                tint,
                Offset(12f * u, 12f * u),
                Offset((12f + 6f * Math.cos(r).toFloat()) * u, (12f + 6f * Math.sin(r).toFloat()) * u),
                s.width * 1.8f,
                StrokeCap.Round,
            )
        }
    }

    @Composable
    fun Printer(tint: Color, size: Dp, modifier: Modifier = Modifier) = Icon(size, modifier) { u, s ->
        // Рама 3D-принтера, портал і сопло над столом.
        drawRoundRect(tint, Offset(3.5f * u, 3f * u), Size(17f * u, 18f * u), CornerRadius(2f * u), style = s)
        drawLine(tint, Offset(3.5f * u, 8f * u), Offset(20.5f * u, 8f * u), s.width, StrokeCap.Round)
        drawLine(tint, Offset(12f * u, 8f * u), Offset(12f * u, 12f * u), s.width, StrokeCap.Round)
        drawLine(tint, Offset(7f * u, 17f * u), Offset(17f * u, 17f * u), s.width, StrokeCap.Round)
    }

    @Composable
    fun Alert(tint: Color, size: Dp, modifier: Modifier = Modifier) = Icon(size, modifier) { u, s ->
        val tri = Path().apply {
            moveTo(12f * u, 3f * u)
            lineTo(21.5f * u, 20f * u)
            lineTo(2.5f * u, 20f * u)
            close()
        }
        drawPath(tri, tint, style = s)
        drawLine(tint, Offset(12f * u, 9.5f * u), Offset(12f * u, 14f * u), s.width, StrokeCap.Round)
        drawCircle(tint, radius = s.width * 0.7f, center = Offset(12f * u, 17f * u))
    }

    @Composable
    private fun Icon(size: Dp, modifier: Modifier, draw: DrawScope.(u: Float, stroke: Stroke) -> Unit) {
        Canvas(modifier = modifier.size(size)) {
            val u = this.size.minDimension / 24f
            draw(u, Stroke(width = u * 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
