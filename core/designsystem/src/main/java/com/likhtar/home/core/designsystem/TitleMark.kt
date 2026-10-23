package com.likhtar.home.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/**
 * Фірмовий текстовий знак — назва, коли логотипа немає. Вузький постерний Oswald великими літерами з тінню: читається як знак шоу, а не
 * як підпис, і тримає ту саму висоту, що логотипи сусідів. Кегль — від [base], у 1,25 раза
 * більший: вузький шрифт при тому ж кеглі виглядає дрібніше.
 */
fun titleMarkStyle(base: TextStyle): TextStyle = base.copy(
    fontFamily = LikhtarType.Poster,
    fontWeight = FontWeight.SemiBold,
    fontSize = base.fontSize * 1.25f,
    lineHeight = base.fontSize * 1.3f,
    letterSpacing = 0.02.em,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(0f, 2f), blurRadius = 10f),
)

/** Назва для знака: великими літерами, як на логотипах шоу. */
fun titleMarkText(title: String): String = title.uppercase()
