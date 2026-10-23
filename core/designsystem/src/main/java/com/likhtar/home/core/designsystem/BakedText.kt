package com.likhtar.home.core.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Кадр із надрукованим написом — мініатюра YouTube: мовники друкують назву й «13-16 СЕРІЇ»
 * просто на ній, і наш підпис поверх налазив на їхній («Ромео і Джульєтта з Черкас»).
 */
fun hasBakedText(imageUrl: String?): Boolean = imageUrl != null && "ytimg.com" in imageUrl

/**
 * Підкладка під підписом-текстом (логотипа немає) чи на кадрі з написом. Перша версія
 * — щільна майже чорна смуга на пів банера: напис ховала, але картки виглядали
 * траурно. Тепер — м'який градієнт теплого графіту, як у карток з
 * логотипом, лише щільніший у самому низу, де стоїть назва; знак назви має власну тінь.
 */
val BakedTextScrim = Brush.verticalGradient(
    0.45f to Color.Transparent,
    0.75f to LikhtarColors.Background.copy(alpha = 0.55f),
    1.00f to LikhtarColors.Background.copy(alpha = 0.9f),
)
