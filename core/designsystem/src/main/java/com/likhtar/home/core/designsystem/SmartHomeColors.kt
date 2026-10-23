package com.likhtar.home.core.designsystem

import androidx.compose.ui.graphics.Color

/** Смарт дім: світло ламп (люстра, торшер, LED-стрічка) і стани плиток. */
object SmartHomeColors {
    // Справжнє світло в кімнатах: центр плями кожного типу лампи.
    val Ceiling = Color(0xFFFFCE78)
    val FloorLamp = Color(0xFFFFB250)
    val Strip = Color(0xFFFFC86E)
    /** Ореол за значком увімкненого пристрою. */
    val Halo = Color(0xFFFFBE5A)
    /** Тло увімкненої плитки. */
    val LitTile = Color(0xFF402E16)
    /** Застереження пристрою: недоступний, тривога. */
    val Fault = Color(0xFFFF7A6B)
}
