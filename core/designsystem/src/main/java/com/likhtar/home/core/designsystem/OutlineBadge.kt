package com.likhtar.home.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * Плашка вік / якість / озвучка — один стиль на всіх екранах (як в Apple TV): тонкий контур і
 * текст того ж кольору; [amber] — українська озвучка («УКР»), решта — світлі.
 */
@Composable
fun OutlineBadge(
    text: String,
    modifier: Modifier = Modifier,
    amber: Boolean = false,
    /**
     * На кадрі картки: темне скло під контуром (інакше контур губиться на світлому) і дрібніший
     * шрифт — розмір героя на картці вдвічі меншій виглядав грубо.
     */
    filled: Boolean = false,
) {
    if (text.isBlank()) return
    val color = if (amber) LikhtarColors.Glow else LikhtarColors.WarmStone
    Text(
        text = text,
        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Bold, fontSize = if (filled) 9.sp else 11.5.sp),
        color = color,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier
            .then(if (filled) Modifier.background(LikhtarColors.BadgeFloor.copy(alpha = 0.72f), RoundedCornerShape(3.dp)) else Modifier)
            .border(1.dp, if (filled) color.copy(alpha = 0.7f) else color, RoundedCornerShape(if (filled) 3.dp else 4.dp))
            .padding(horizontal = if (filled) 4.5.dp else 6.dp, vertical = if (filled) 0.5.dp else 1.5.dp),
    )
}
