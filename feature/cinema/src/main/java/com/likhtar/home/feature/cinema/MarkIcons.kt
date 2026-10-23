package com.likhtar.home.feature.cinema

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Знаки «Позначити» — замість емодзі: та сама сітка 24×24 і лінія, що в LikhtarIcons. */
enum class MarkIcon { WATCHED, SKIP, HIDE, LIKE, OK, DISLIKE, CLEAR, BELL, BOOKMARK }

@Composable
fun MarkGlyph(icon: MarkIcon, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 2.1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(vararg pts: Float) = Path().apply {
            moveTo(pts[0] * u, pts[1] * u)
            for (i in 2 until pts.size step 2) lineTo(pts[i] * u, pts[i + 1] * u)
        }
        // Серце: дві дуги й вістря — основа для оцінок.
        fun heart() = Path().apply {
            moveTo(12f * u, 20f * u)
            cubicTo(4f * u, 14.5f * u, 2.5f * u, 10.5f * u, 4.2f * u, 7.4f * u)
            cubicTo(6f * u, 4.2f * u, 10.2f * u, 4.4f * u, 12f * u, 7.6f * u)
            cubicTo(13.8f * u, 4.4f * u, 18f * u, 4.2f * u, 19.8f * u, 7.4f * u)
            cubicTo(21.5f * u, 10.5f * u, 20f * u, 14.5f * u, 12f * u, 20f * u)
            close()
        }
        when (icon) {
            MarkIcon.WATCHED -> {
                drawCircle(tint, radius = 9f * u, style = stroke)
                drawPath(p(7.8f, 12.3f, 10.8f, 15.2f, 16.4f, 9.2f), tint, style = stroke)
            }
            MarkIcon.SKIP -> {
                drawPath(p(4.5f, 6.5f, 10.5f, 12f, 4.5f, 17.5f), tint, style = stroke)
                drawPath(p(11f, 6.5f, 17f, 12f, 11f, 17.5f), tint, style = stroke)
                drawLine(tint, Offset(19.8f * u, 6.5f * u), Offset(19.8f * u, 17.5f * u), 2.1f * u, StrokeCap.Round)
            }
            MarkIcon.HIDE -> {
                // Око, перекреслене навскіс.
                val eye = Path().apply {
                    moveTo(2.8f * u, 12f * u)
                    quadraticTo(12f * u, 3f * u, 21.2f * u, 12f * u)
                    quadraticTo(12f * u, 21f * u, 2.8f * u, 12f * u)
                    close()
                }
                drawPath(eye, tint, style = stroke)
                drawCircle(tint, radius = 2.6f * u, style = stroke)
                drawLine(tint, Offset(4.5f * u, 19.5f * u), Offset(19.5f * u, 4.5f * u), 2.1f * u, StrokeCap.Round)
            }
            MarkIcon.LIKE -> drawPath(heart(), tint, style = Fill)
            MarkIcon.OK -> drawPath(heart(), tint, style = stroke)
            MarkIcon.DISLIKE -> {
                drawPath(heart(), tint, style = stroke)
                // Тріщина через серце.
                drawPath(p(12f, 7.6f, 10.4f, 11f, 13.4f, 13.2f, 11.6f, 17.4f), tint, style = stroke)
            }
            MarkIcon.BOOKMARK -> drawPath(p(6.5f, 3.5f, 17.5f, 3.5f, 17.5f, 20.5f, 12f, 16f, 6.5f, 20.5f).apply { close() }, tint, style = stroke)
            MarkIcon.BELL -> {
                // Дзвіночок радара: купол, обідок і язичок.
                val bell = Path().apply {
                    moveTo(5f * u, 17f * u)
                    lineTo(19f * u, 17f * u)
                    cubicTo(17.4f * u, 15.4f * u, 17.4f * u, 13.6f * u, 17.4f * u, 10.6f * u)
                    cubicTo(17.4f * u, 7.2f * u, 15f * u, 4.6f * u, 12f * u, 4.6f * u)
                    cubicTo(9f * u, 4.6f * u, 6.6f * u, 7.2f * u, 6.6f * u, 10.6f * u)
                    cubicTo(6.6f * u, 13.6f * u, 6.6f * u, 15.4f * u, 5f * u, 17f * u)
                    close()
                }
                drawPath(bell, tint, style = stroke)
                drawLine(tint, Offset(10.4f * u, 20.2f * u), Offset(13.6f * u, 20.2f * u), 2.1f * u, StrokeCap.Round)
            }
            MarkIcon.CLEAR -> {
                // Стрілка «назад» по колу — повернути як було.
                drawArc(
                    tint, startAngle = -60f, sweepAngle = 285f, useCenter = false,
                    topLeft = Offset(4f * u, 4f * u), size = Size(16f * u, 16f * u), style = stroke,
                )
                drawPath(p(15.2f, 2.8f, 16.4f, 6.6f, 12.6f, 7.6f), tint, style = stroke)
            }
        }
    }
}
