package com.likhtar.home.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Кнопка прототипів (.btn у shared.css): пігулка 76 px, поле 36 px, підпис 26 px / 600,
 * значок 32 px бурштином. Фокус — тепла біла заливка, темний підпис, значок #8A6526, 1.05.
 * Розміри — за замовчуванням із shared.css; екрани, де прототип їх перевизначає, передають свої.
 */
@Composable
fun ProtoButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = R.drawable.ic_solar_play,
    height: Dp = 38.dp,
    padding: Dp = 18.dp,
    fontSize: TextUnit = 13.sp,
    iconSize: Dp = 16.dp,
    onLongClick: (() -> Unit)? = null,
    /** Свій значок замість [icon] (ліхтарик головної дії); отримує стан фокуса. */
    leading: (@Composable (focused: Boolean) -> Unit)? = null,
    /** Після підпису: мітка UA / СУБ на кнопці сервісу. */
    trailing: (@Composable () -> Unit)? = null,
    /** .btn.svc — 700 з −.01em; звичайна кнопка — 600. */
    bold: Boolean = false,
    enabled: Boolean = true,
) {
    FocusableSurface(
        onClick = { if (enabled) onClick() },
        onLongClick = onLongClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = LikhtarColors.Surface.copy(alpha = 0.85f),
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusedScale = 1.05f,
        instantFill = true,
    ) { focused ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = padding),
        ) {
            if (leading != null) {
                leading(focused)
            } else if (icon != null) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = if (focused) LikhtarColors.AmberOnPaper else LikhtarColors.Glow,
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(
                label,
                style = TextStyle(
                    fontFamily = LikhtarType.Body,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = fontSize,
                    letterSpacing = if (bold) (-0.01f * fontSize.value).sp else 0.sp,
                ),
                color = if (focused) LikhtarColors.Background else LikhtarColors.TextPrimary.copy(alpha = if (enabled) 1f else 0.45f),
                maxLines = 1,
            )
            trailing?.invoke()
        }
    }
}
