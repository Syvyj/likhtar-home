package com.likhtar.home.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * Мітка метаданих з іконкою й кольором свого виду.
 *
 * Однакові сірі плашки поруч із жовтими смужками «UA» читались вимкненими . Колір тут — не прикраса, а вид факту: якість одна, рік інший, статус
 * третій, і око знаходить потрібне, не читаючи. Фон лишається темним і напівпрозорим, як
 * у «UA», — колір несуть іконка й обводка, а не заливка, інакше ряд став би строкатим.
 *
 * Іконки мальовані Canvas, як і смужки звуку: у проєкті вся графіка — Canvas.
 */
enum class ChipKind(val tint: Color) {
    QUALITY(ChipColors.Quality),
    QUALITY_4K(ChipColors.Quality4K),
    YEAR(ChipColors.Year),
    SERIES(ChipColors.Series),
    MOVIE(ChipColors.Movie),
    RUNTIME(ChipColors.Runtime),
    AGE(LikhtarColors.TileOrange),
    RATING(ChipColors.Rating),
    STATUS_ON(LikhtarColors.TileGreen),
    STATUS_DONE(ChipColors.StatusDone),
    STATUS_CUT(LikhtarColors.TileRed),
    PLAIN(Color.White),
}

@Composable
fun MetaChip(text: String, kind: ChipKind = ChipKind.PLAIN, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    val shape = RoundedCornerShape(LikhtarDimens.CornerSmall)
    val plain = kind == ChipKind.PLAIN
    Row(
        modifier = modifier
            .clip(shape)
            .background(LikhtarColors.ChipFloor.copy(alpha = 0.69f))
            .border(1.dp, if (plain) Color.White.copy(alpha = 0.14f) else kind.tint.copy(alpha = 0.45f), shape)
            .padding(start = if (plain) 7.dp else 5.dp, top = 3.dp, end = 7.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (!plain) {
            Canvas(Modifier.size(12.dp)) { drawChipIcon(kind) }
        }
        Text(
            text = text,
            style = LikhtarType.MonoSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
            color = if (kind == ChipKind.RATING) kind.tint else LikhtarColors.ChipInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun DrawScope.drawChipIcon(kind: ChipKind) {
    val c = kind.tint
    val w = size.width
    val h = size.height
    val line = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
    when (kind) {
        // Екран: рамка з ніжкою.
        ChipKind.QUALITY, ChipKind.QUALITY_4K -> {
            drawRoundRect(c, Offset(0f, h * 0.12f), Size(w, h * 0.62f), CornerRadius(2.dp.toPx()), style = line)
            drawLine(c, Offset(w * 0.32f, h * 0.92f), Offset(w * 0.68f, h * 0.92f), line.width, StrokeCap.Round)
        }
        // Календар: рамка й верхня смуга.
        ChipKind.YEAR -> {
            drawRoundRect(c, Offset(0f, h * 0.15f), Size(w, h * 0.8f), CornerRadius(2.dp.toPx()), style = line)
            drawLine(c, Offset(0f, h * 0.42f), Offset(w, h * 0.42f), line.width)
            drawLine(c, Offset(w * 0.3f, 0f), Offset(w * 0.3f, h * 0.28f), line.width, StrokeCap.Round)
            drawLine(c, Offset(w * 0.7f, 0f), Offset(w * 0.7f, h * 0.28f), line.width, StrokeCap.Round)
        }
        // Телевізор з антенкою.
        ChipKind.SERIES -> {
            drawRoundRect(c, Offset(0f, h * 0.3f), Size(w, h * 0.66f), CornerRadius(2.dp.toPx()), style = line)
            drawLine(c, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.3f, 0f), line.width, StrokeCap.Round)
            drawLine(c, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.7f, 0f), line.width, StrokeCap.Round)
        }
        // Плівка: рамка з перфорацією.
        ChipKind.MOVIE -> {
            drawRoundRect(c, Offset(w * 0.1f, 0f), Size(w * 0.8f, h), CornerRadius(1.5.dp.toPx()), style = line)
            for (i in 0..2) {
                val y = h * (0.2f + i * 0.3f)
                drawCircle(c, 0.9.dp.toPx(), Offset(w * 0.28f, y))
                drawCircle(c, 0.9.dp.toPx(), Offset(w * 0.72f, y))
            }
        }
        // Годинник.
        ChipKind.RUNTIME -> {
            drawCircle(c, w * 0.46f, center, style = line)
            drawLine(c, center, Offset(w * 0.5f, h * 0.22f), line.width, StrokeCap.Round)
            drawLine(c, center, Offset(w * 0.72f, h * 0.55f), line.width, StrokeCap.Round)
        }
        // Щит віку.
        ChipKind.AGE -> {
            val p = Path().apply {
                moveTo(w * 0.5f, 0f); lineTo(w, h * 0.2f); lineTo(w * 0.85f, h * 0.75f)
                lineTo(w * 0.5f, h); lineTo(w * 0.15f, h * 0.75f); lineTo(0f, h * 0.2f); close()
            }
            drawPath(p, c, style = line)
        }
        // Зірка.
        ChipKind.RATING -> {
            val p = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) w * 0.5f else w * 0.22f
                val a = Math.toRadians((-90 + i * 36).toDouble())
                val x = center.x + r * Math.cos(a).toFloat()
                val y = center.y + r * Math.sin(a).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, c)
        }
        // Статус — крапка свого кольору.
        ChipKind.STATUS_ON, ChipKind.STATUS_DONE, ChipKind.STATUS_CUT -> drawCircle(c, w * 0.3f, center)
        ChipKind.PLAIN -> Unit
    }
}
