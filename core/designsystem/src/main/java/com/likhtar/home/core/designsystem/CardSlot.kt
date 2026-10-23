package com.likhtar.home.core.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier

/**
 * Слот картки в ряду чи сітці: поля 7 × 8 dp. Разом із проміжком рейки ([LikhtarDimens.RailGap])
 * це [LikhtarDimens.CardGap] видимої відстані між картками — однаково в Кінозалі, Реаліті,
 * Ліхтарику, на сторінках сервісів і в добірках (було 18, 20 і 24 dp).
 */
fun Modifier.cardSlot(): Modifier = padding(horizontal = LikhtarDimens.CardSlotX, vertical = LikhtarDimens.CardSlotY)
