package com.likhtar.home.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.likhtar.home.feature.settings.R
import androidx.compose.ui.focus.FocusRequester
import com.likhtar.home.feature.settings.SettingsUiState
import com.likhtar.home.feature.settings.SettingsViewModel
import com.likhtar.home.feature.settings.TextField
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.ToggleRow
import com.likhtar.home.feature.settings.ValueRow
import com.likhtar.home.feature.settings.ChoiceRow
import com.likhtar.home.feature.settings.Note
import com.likhtar.home.core.model.LikhtarSection

// ─── ГОЛОВНА ──────────────────────────────────────────────────────────
// Усе, що видно на Головній: погода й ряди, що з'являються над плитками доку.
@Composable
internal fun HomeSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    editedRowFocusRequester: FocusRequester,
) {
    GroupTitle(stringResource(R.string.settings_home_weather_group))
    ToggleRow(
        text = stringResource(R.string.settings_home_show_weather),
        checked = state.settings.showWeather,
        onToggle = viewModel::toggleWeather,
    )
    if (state.settings.showWeather) {
        ValueRow(
            text = stringResource(R.string.settings_home_city),
            value = state.settings.weatherCity.ifBlank { stringResource(R.string.settings_not_set) },
            onClick = viewModel::editWeatherCity,
            focusRequester = editedRowFocusRequester
                .takeIf { state.lastEditedField == TextField.WEATHER_CITY },
        )
        // Результати пошуку міста — вибір, а не дія.
        state.weatherPlaces.forEach { place ->
            ChoiceRow(
                text = place.title,
                selected = false,
                onSelect = { viewModel.selectWeatherPlace(place) },
            )
        }
    }

    // Що лишити в доці: прибраний розділ відкривається з меню вгорі, тож нічого не губиться.
    GroupTitle(stringResource(R.string.settings_home_dock_group))
    listOf(
        LikhtarSection.CINEMA to R.string.settings_home_dock_cinema,
        LikhtarSection.LIVE_TV to R.string.settings_home_dock_live_tv,
        LikhtarSection.REALITY to R.string.settings_home_dock_reality,
        LikhtarSection.SEARCH to R.string.settings_home_dock_search,
        LikhtarSection.APPS to R.string.settings_home_dock_apps,
    ).forEach { (section, label) ->
        ToggleRow(
            text = stringResource(label),
            checked = section.route !in state.settings.hiddenDockSections,
            onToggle = { viewModel.toggleDockSection(section.route) },
        )
    }

    GroupTitle(stringResource(R.string.settings_home_torch_group))
    ToggleRow(
        text = stringResource(R.string.settings_home_torch),
        subtitle = stringResource(R.string.settings_home_torch_subtitle),
        checked = state.settings.homeShowTorch,
        onToggle = viewModel::toggleHomeTorch,
    )
    ToggleRow(
        text = stringResource(R.string.settings_home_voice),
        subtitle = stringResource(R.string.settings_home_voice_subtitle),
        checked = state.settings.voiceSearchOnTv,
        onToggle = viewModel::toggleVoiceOnTv,
    )

    GroupTitle(stringResource(R.string.settings_home_channels_group))
    ValueRow(
        text = stringResource(R.string.settings_home_channel_count),
        value = "${state.settings.homeChannelCount}",
        onClick = viewModel::cycleHomeChannelCount,
        subtitle = stringResource(R.string.settings_home_channel_count_subtitle),
    )
    if (state.recentChannels.isEmpty()) {
        Note(stringResource(R.string.settings_home_channels_empty))
    } else {
        state.recentChannels.forEach { channel ->
            ChoiceRow(
                text = channel.name,
                selected = channel.url in state.settings.pinnedChannels,
                onSelect = { viewModel.togglePinnedChannel(channel.url) },
            )
        }
    }

    GroupTitle(stringResource(R.string.settings_home_apps_group))
    ValueRow(
        text = stringResource(R.string.settings_home_apps_what),
        value = stringResource(if (state.settings.homeAppsPinned) R.string.settings_home_apps_pinned else R.string.settings_home_apps_recent),
        onClick = viewModel::toggleHomeAppsPinned,
        subtitle = stringResource(R.string.settings_home_apps_subtitle),
    )
    ValueRow(
        text = stringResource(R.string.settings_home_apps_count),
        value = "${state.settings.homeAppsCount}",
        onClick = viewModel::cycleHomeAppsCount,
    )

    GroupTitle(stringResource(R.string.settings_home_calm_group))
    ValueRow(
        text = stringResource(R.string.settings_home_ambient),
        value = ambientLabel(state.settings.ambientTimeoutSeconds),
        onClick = viewModel::cycleAmbientTimeout,
        subtitle = stringResource(R.string.settings_home_ambient_subtitle),
    )
}

@Composable
private fun ambientLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.settings_never)
    in 1..119 -> stringResource(R.string.settings_seconds, seconds)
    else -> stringResource(R.string.settings_minutes, seconds / 60)
}
