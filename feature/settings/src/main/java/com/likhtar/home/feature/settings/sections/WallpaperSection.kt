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
import com.likhtar.home.feature.settings.ChoiceRow

// ─── ШПАЛЕРИ ──────────────────────────────────────────────────────────
@Composable
internal fun WallpaperSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    GroupTitle(stringResource(R.string.settings_wallpaper_source_group))
    state.wallpaperProviders.forEach { (id, name) ->
        ChoiceRow(
            text = name,
            selected = state.settings.wallpaperProviderId == id,
            onSelect = { viewModel.selectWallpaperProvider(id) },
        )
    }
    if (state.settings.wallpaperProviderId == THEMED_PROVIDER_ID) {
        GroupTitle(stringResource(R.string.settings_wallpaper_theme_group))
        WallpaperTheme.entries.forEach { theme ->
            ChoiceRow(
                text = stringResource(theme.titleRes),
                selected = state.settings.wallpaperTheme == theme.id,
                onSelect = { viewModel.selectWallpaperTheme(theme) },
            )
        }
    }
    GroupTitle(stringResource(R.string.settings_wallpaper_show_group))
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
}

/** Теми показуємо лише коли обране саме це джерело — інакше вони ні на що не впливають. */
private const val THEMED_PROVIDER_ID = "themed"
