package com.likhtar.home.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.likhtar.home.feature.settings.R
import com.likhtar.home.core.wallpaper.WallpaperTheme
import com.likhtar.home.feature.settings.SettingsUiState
import com.likhtar.home.feature.settings.SettingsViewModel
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.ToggleRow
import com.likhtar.home.feature.settings.ValueRow
import com.likhtar.home.feature.settings.ActionRow
import com.likhtar.home.feature.settings.SingleChoiceDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// ─── ТЛО Й ЗАСТАВКА ───────────────────────────────────────────────────
// Усе, що телевізор показує, коли ним не користуються: шпалери під Головною, «Спокій» (плитки
// ховаються, відео з висоти грає) і заставка. Раніше це лежало в хвості «Головної», за 30 кроків.
@Composable
internal fun BackdropSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var picker by remember { mutableStateOf<String?>(null) }
    GroupTitle(stringResource(R.string.settings_wallpaper_group))
    ValueRow(
        text = stringResource(R.string.settings_wallpaper_source),
        value = state.wallpaperProviders.firstOrNull { it.first == state.settings.wallpaperProviderId }?.second.orEmpty(),
        onClick = { picker = SOURCE },
    )
    // Теми показуємо лише коли обране саме це джерело — інакше вони ні на що не впливають.
    if (state.settings.wallpaperProviderId == THEMED_PROVIDER_ID) {
        ValueRow(
            text = stringResource(R.string.settings_wallpaper_theme),
            value = WallpaperTheme.entries.firstOrNull { it.id == state.settings.wallpaperTheme }
                ?.let { stringResource(it.titleRes) }.orEmpty(),
            onClick = { picker = THEME },
        )
    }
    ValueRow(
        text = stringResource(R.string.settings_wallpaper_interval),
        value = stringResource(R.string.settings_minutes, state.settings.wallpaperIntervalMinutes),
        onClick = viewModel::cycleWallpaperInterval,
    )
    ValueRow(
        text = stringResource(R.string.settings_wallpaper_dim),
        value = stringResource(R.string.settings_percent, state.settings.wallpaperDimPercent),
        onClick = viewModel::cycleWallpaperDim,
        subtitle = stringResource(R.string.settings_wallpaper_dim_subtitle),
    )
    ToggleRow(
        text = stringResource(R.string.settings_wallpaper_attribution),
        checked = state.settings.showWallpaperAttribution,
        onToggle = viewModel::toggleWallpaperAttribution,
    )

    GroupTitle(stringResource(R.string.settings_home_calm_group))
    ValueRow(
        text = stringResource(R.string.settings_home_ambient),
        value = ambientLabel(state.settings.ambientTimeoutSeconds),
        onClick = viewModel::cycleAmbientTimeout,
        subtitle = stringResource(R.string.settings_home_ambient_subtitle),
    )

    GroupTitle(stringResource(R.string.settings_screensaver_group))
    ValueRow(
        text = stringResource(R.string.settings_screensaver_after),
        value = minutesLabel(state.settings.screensaverMinutes),
        onClick = viewModel::cycleScreensaverMinutes,
        subtitle = stringResource(R.string.settings_screensaver_after_subtitle),
    )
    ValueRow(
        text = stringResource(R.string.settings_screensaver_sleep),
        value = minutesLabel(state.settings.screensaverSleepMinutes),
        onClick = viewModel::cycleScreensaverSleep,
        subtitle = stringResource(R.string.settings_screensaver_sleep_subtitle),
    )
    ValueRow(
        text = stringResource(R.string.settings_screensaver_aerial),
        value = stringResource(if (state.settings.screensaverAerial) R.string.settings_on else R.string.settings_off),
        onClick = viewModel::toggleScreensaverAerial,
    )
    ValueRow(
        text = stringResource(R.string.settings_screensaver_titles),
        value = stringResource(if (state.settings.screensaverTitles) R.string.settings_on else R.string.settings_off),
        onClick = viewModel::toggleScreensaverTitles,
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    ActionRow(
        text = stringResource(R.string.settings_screensaver_preview),
        action = stringResource(R.string.settings_screensaver_preview_action),
        // Клас заставки — у модулі застосунку, тож за іменем: налаштування від нього не залежать.
        onClick = {
            context.startActivity(
                android.content.Intent().setClassName(context, "com.likhtar.home.ScreensaverActivity"),
            )
        },
    )

    when (picker) {
        SOURCE -> SingleChoiceDialog(
            title = stringResource(R.string.settings_wallpaper_source),
            options = state.wallpaperProviders,
            selected = state.settings.wallpaperProviderId,
            onPick = { picker = null; viewModel.selectWallpaperProvider(it) },
            onDismiss = { picker = null },
        )
        THEME -> {
            val themes = WallpaperTheme.entries.map { it.id to stringResource(it.titleRes) }
            SingleChoiceDialog(
                title = stringResource(R.string.settings_wallpaper_theme),
                options = themes,
                selected = state.settings.wallpaperTheme,
                onPick = { id -> picker = null; WallpaperTheme.entries.firstOrNull { it.id == id }?.let(viewModel::selectWallpaperTheme) },
                onDismiss = { picker = null },
            )
        }
    }
}

private const val THEMED_PROVIDER_ID = "themed"
private const val SOURCE = "source"
private const val THEME = "theme"

@Composable
private fun minutesLabel(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.settings_never)
    else -> stringResource(R.string.settings_minutes, minutes)
}

@Composable
private fun ambientLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.settings_never)
    in 1..119 -> stringResource(R.string.settings_seconds, seconds)
    else -> stringResource(R.string.settings_minutes, seconds / 60)
}
