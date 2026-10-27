package com.likhtar.home.feature.apps

import androidx.compose.ui.graphics.Color

/**
 * Хмарні ігри в розділі «Ігри» — ярлики, а не каталог: встановлений сервіс відкривається,
 * невстановлений веде на свою сторінку в Google Play. Xbox на Google TV застосунку ще не має —
 * тоді xbox.com/play у браузері ([web]). [icon] — значок застосунку з його сторінки в Google Play.
 */
enum class CloudGame(
    val title: String,
    val color: Color,
    val packages: List<String>,
    val noteRes: Int,
    val icon: Int,
    val web: String? = null,
) {
    BOOSTEROID("Boosteroid", Color(0xFF6B3CF0), listOf("com.boosteroidtv.streaming", "com.boosteroid.streaming"), R.string.apps_cloud_boosteroid, R.drawable.cloud_boosteroid),
    XBOX("Xbox Cloud Gaming", Color(0xFF107C10), listOf("com.microsoft.xboxone.smartglass"), R.string.apps_cloud_xbox, R.drawable.cloud_xbox, "https://www.xbox.com/play"),
    GEFORCE_NOW("GeForce NOW", Color(0xFF76B900), listOf("com.nvidia.geforcenow"), R.string.apps_cloud_geforce, R.drawable.cloud_geforce_now),
    PS_REMOTE("PS Remote Play", Color(0xFF0070D1), listOf("com.playstation.remoteplay"), R.string.apps_cloud_ps, R.drawable.cloud_ps_remote),
}
