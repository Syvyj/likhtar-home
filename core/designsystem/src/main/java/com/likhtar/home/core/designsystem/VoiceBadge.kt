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
    @Suppress("UNUSED_PARAMETER") large: Boolean = false,
) {
    // Один стиль плашок: контур, «УКР» — бурштином; на кадрі — темна підкладка.
    OutlineBadge(
        text = if (label == "UA") androidx.compose.ui.res.stringResource(R.string.ds_badge_ukr) else label,
        amber = true,
        filled = true,
        modifier = modifier,
    )
}

/**
 * Мітка якості поруч з «УКР»: той самий контур, світлий.
 *
 * Одна мітка на «4K · Dolby Vision», а не дві: на дивані вони читаються як одна
 * властивість — яка картинка ввімкнеться.
 */
@Composable
fun QualityBadge(label: String, modifier: Modifier = Modifier) {
    OutlineBadge(text = label, filled = true, modifier = modifier)
}

/** Підпис картки — «вийде 3 листопада», «Netflix · 9 жовтня»: та сама плашка, що УКР і якість. */
@Composable
fun NoteBadge(text: String, modifier: Modifier = Modifier) {
    OutlineBadge(text = text.replaceFirstChar { it.uppercase() }, filled = true, modifier = modifier)
}
