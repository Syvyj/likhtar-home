package com.likhtar.home.feature.apps
import com.likhtar.home.core.designsystem.CloudColors

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
    BOOSTEROID("Boosteroid", CloudColors.Boosteroid, listOf("com.boosteroidtv.streaming", "com.boosteroid.streaming"), R.string.apps_cloud_boosteroid, R.drawable.cloud_boosteroid),
    XBOX("Xbox Cloud Gaming", CloudColors.Xbox, listOf("com.microsoft.xboxone.smartglass"), R.string.apps_cloud_xbox, R.drawable.cloud_xbox, "https://www.xbox.com/play"),
    GEFORCE_NOW("GeForce NOW", CloudColors.GeForceNow, listOf("com.nvidia.geforcenow"), R.string.apps_cloud_geforce, R.drawable.cloud_geforce_now),
    PS_REMOTE("PS Remote Play", CloudColors.PsRemote, listOf("com.playstation.remoteplay"), R.string.apps_cloud_ps, R.drawable.cloud_ps_remote),
}
