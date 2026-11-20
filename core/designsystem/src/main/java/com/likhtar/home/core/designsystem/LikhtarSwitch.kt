package com.likhtar.home.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Перемикач-пігулка з кружком (як у tvOS): стан видно й без кольору — кружок праворуч чи ліворуч.
 * Лише вигляд: натискання ловить рядок, у якому він стоїть.
 */
@Composable
fun LikhtarSwitch(on: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 25.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) LikhtarColors.Glow else LikhtarColors.SwitchOff)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(19.dp).clip(CircleShape).background(Color.White))
    }
}
