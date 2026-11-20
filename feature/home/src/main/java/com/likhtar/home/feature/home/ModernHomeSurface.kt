package com.likhtar.home.feature.home

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LocalHighContrast

/** Тонка обгортка над спільним [FocusableSurface] для плиток і кнопок Головної. */
@Composable
internal fun ModernHomeSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    background: Color,
    focusedBackground: Color = background,
    corner: Dp,
    scale: Float,
    lift: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") pointerParallax: Boolean = false,
    highlighted: Boolean = false,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    val highContrast = LocalHighContrast.current
    val ringColor = if (highlighted) LikhtarColors.LanternAmber else LikhtarColors.GlassActive
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        shape = RoundedCornerShape(corner),
        background = background,
        focusedBackground = focusedBackground,
        borderColor = ringColor.copy(alpha = if (highContrast) 0.45f else 0.09f),
        focusRingColor = ringColor,
        focusedScale = scale,
        focusedLift = lift,
        focusLighten = 0f,
        highlighted = highlighted,
        content = content,
    )
}
