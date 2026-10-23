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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * Мітка української доріжки: еквалайзер і «UA».
 *
 * Замість прапора — саме звукова хвиля: мітка каже не «країна», а «є що слухати цією
 * мовою». Прапор на кадрі серіалу читався ще й як прапор у сюжеті, чого ми не мали на увазі.
 *
 * Смужки мальовані, а не гліфи чи ресурс: у проєкті вся графіка — Canvas, і п'ять
 * прямокутників тут дешевші за будь-який асет. Висоти навмисно нерівні (4/9/6/11/5), бо
 * рівні читалися б як шкала гучності, а не як звук.
 *
 * Скло без `blur`: RenderEffect є лише з API 31, а на приставці з гігабайтом розмиття під
 * кожною карткою ряду коштує помітно більше, ніж дає. Тому напівпрозора підкладка, як і в
 * сусідній мітці тривалості.
 */
@Composable
fun VoiceBadge(
    modifier: Modifier = Modifier,
    label: String = "UA",
    /**
     * Крупніша версія для рядка міток сторінки тайтла: там сусідні мітки 11sp, і в 9sp
     * головна мітка ряду виходила найдрібнішою.
     */
    large: Boolean = false,
) {
    val k = if (large) 1.25f else 1f
    Row(
        modifier = modifier
            .badgeChrome()
            .padding(horizontal = 6.dp * k, vertical = 3.dp * k),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp * k),
    ) {
        // .badge .wave прототипів: чотири смужки 3 px через 2 px — 6, 12, 16, 10 px заввишки.
        Canvas(modifier = Modifier.size(width = 7.dp * k, height = 8.dp * k)) {
            val bar = 1.5.dp.toPx() * k
            val step = bar + 1.dp.toPx() * k
            val radius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            listOf(3f, 6f, 8f, 5f).forEachIndexed { index, heightDp ->
                val height = heightDp.dp.toPx() * k
                drawRoundRect(
                    color = LikhtarColors.Amber,
                    topLeft = Offset(index * step, (size.height - height) / 2f),
                    size = Size(bar, height),
                    cornerRadius = radius,
                )
            }
        }
        Text(
            text = label,
            // 600 18 px Manrope, білий.
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = LikhtarType.Body,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (large) 11.sp else 9.sp,
            ),
            color = Color.White,
        )
    }
}


/**
 * Мітка якості поруч з «UA»: та сама підкладка й шрифт, без смужок звуку.
 *
 * Одна мітка на «4K · Dolby Vision», а не дві: на дивані вони читаються як одна
 * властивість — яка картинка ввімкнеться.
 */
@Composable
fun QualityBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = LikhtarType.MonoSmall.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 9.sp,
        ),
        color = BadgeInk,
        modifier = modifier
            .badgeChrome()
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/**
 * Підпис картки — «вийде 3 листопада», «Netflix · 9 жовтня»: та сама плашка, що мітки UA й
 * якості, у верхньому куті, текст бурштином, як «Вийде …» на великих картках анонсів.
 */
@Composable
fun NoteBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.replaceFirstChar { it.uppercase() },
        // .note прототипів: 500 17 px JetBrains Mono, --glow.
        style = LikhtarType.MonoSmall.copy(fontWeight = FontWeight.Medium, fontSize = 8.5.sp),
        color = LikhtarColors.Glow,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier
            .badgeChrome()
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** Плашка мітки — одна на UA, якість і підпис картки: темне скло, тонка світла рамка. */
fun Modifier.badgeChrome(): Modifier =
    // .badge / .note / .q прототипів: rgba(15,13,11,.72), скруглення 10 px, без рамки.
    clip(RoundedCornerShape(5.dp))
        .background(BadgeFill)

private val BadgeFill = LikhtarColors.BadgeFloor.copy(alpha = 0.72f)
private val BadgeInk = LikhtarColors.ChipInk
