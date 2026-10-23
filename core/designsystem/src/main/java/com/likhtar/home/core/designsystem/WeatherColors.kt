package com.likhtar.home.core.designsystem

import androidx.compose.ui.graphics.Color

/** Палітра сторінки погоди: небо за станом і часом доби, опади, світило. */
object WeatherColors {
    /** Холодне біле чорнило сторінки погоди. */
    val Ink = Color(0xFFEEF0F2)
    val Rain = Color(0xFF8FC1FF)
    val RainBright = Color(0xFFB9D8FF)
    val RainBar = Color(0xFF78AFFF)
    /** Опади на теплій білій плитці дня у фокусі. */
    val RainOnPaper = Color(0xFF2C5EA8)

    // Небо: верх і низ градієнта.
    val ClearTop = Color(0xFF4F7FB0)
    val ClearBottom = Color(0xFF2A3443)
    val CloudyTop = Color(0xFF5E6874)
    val CloudyBottom = Color(0xFF30333A)
    val RainTop = Color(0xFF4D5866)
    val RainBottom = Color(0xFF2A2D33)
    val NightTop = Color(0xFF1A2540)
    val NightBottom = Color(0xFF16181F)
    val NightRainTop = Color(0xFF1D2430)
    val NightRainBottom = Color(0xFF141619)

    /** Світило за кадром: у негоду й уночі (вдень — Glow). */
    val OrbOvercast = Color(0xFFDCE1EB)
    val OrbNight = Color(0xFFBECDFF)
    /** Затемнення низу неба під текстом. */
    val Floor = Color(0xFF0A0A0C)
}
