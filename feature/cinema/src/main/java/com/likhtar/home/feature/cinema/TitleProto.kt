package com.likhtar.home.feature.cinema

import androidx.annotation.DrawableRes
import com.likhtar.home.core.designsystem.LikhtarColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.LikhtarType

/**
 * Мітка сторінки тайтла (.chip прототипу): 44 px заввишки, скруглення 12 px, темне скло
 * .62 з тонкою рамкою, 21 px / 600. [mono] — цифри (.chip.num, JetBrains Mono 20 px / 500);
 * [icon] — значок Solar бурштином; [dot] — бурштинова крапка статусу; [amber] — бурштиновий
 * підпис і рамка («нова · 15 жовт.», «вийде 22 жовт.»).
 */
@Composable
internal fun ProtoChip(
    text: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    @DrawableRes icon: Int? = null,
    dot: Boolean = false,
    amber: Boolean = false,
    height: Dp = 22.dp,
    fontSize: TextUnit = if (mono) 10.sp else 10.5.sp,
) {
    if (text.isBlank()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(ChipFill)
            .border(0.75.dp, if (amber) AmberLine else LikhtarColors.Faint, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = LikhtarColors.Glow, modifier = Modifier.size(11.dp))
        if (dot) Box(Modifier.size(4.5.dp).clip(CircleShape).background(LikhtarColors.Amber))
        Text(
            text,
            style = TextStyle(
                fontFamily = if (mono) LikhtarType.Mono else LikhtarType.Body,
                fontWeight = if (mono) FontWeight.Medium else FontWeight.SemiBold,
                fontSize = fontSize,
            ),
            color = if (amber) LikhtarColors.Glow else LikhtarColors.TextPrimary,
            maxLines = 1,
        )
    }
}

/**
 * «UA» у мітках сторінки (.chips .badge): хвиля бурштином, тло бурштину .16 і рамка .45 —
 * головна мітка ряду, тому тепліша за решту.
 */
@Composable
internal fun ProtoUaChip(label: String = "UA", height: Dp = 22.dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(LikhtarColors.Amber.copy(alpha = 0.16f))
            .border(0.75.dp, AmberLine, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp),
    ) {
        Canvas(Modifier.size(width = 7.dp, height = 8.dp)) {
            val bar = 1.5.dp.toPx()
            val step = bar + 1.dp.toPx()
            listOf(3f, 6f, 8f, 5f).forEachIndexed { i, h ->
                val hp = h.dp.toPx()
                drawRoundRect(LikhtarColors.Amber, Offset(i * step, (size.height - hp) / 2f), Size(bar, hp), CornerRadius(1.dp.toPx()))
            }
        }
        Text(label, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp), color = Color.White, maxLines = 1)
    }
}

private val ChipFill = LikhtarColors.BackgroundDeep.copy(alpha = 0.62f)
private val AmberLine = LikhtarColors.Amber.copy(alpha = 0.45f)
