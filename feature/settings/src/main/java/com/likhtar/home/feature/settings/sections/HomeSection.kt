package com.likhtar.home.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.likhtar.home.feature.settings.R
import androidx.compose.ui.focus.FocusRequester
import com.likhtar.home.feature.settings.SettingsUiState
import com.likhtar.home.feature.settings.SettingsViewModel
import com.likhtar.home.feature.settings.TextField
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.ToggleRow
import com.likhtar.home.feature.settings.ValueRow
import com.likhtar.home.feature.settings.MultiChoiceDialog
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
    HomeLaunchGroup(state, viewModel)
    LocaleRows(state, viewModel)

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

    // Розділи Ліхтаря («Від Ліхтаря») приховати не можна; керуємо лише виглядом рядів та додатковими розділами.
    GroupTitle(stringResource(R.string.settings_home_layout_group))
    ToggleRow(
        text = stringResource(R.string.settings_home_combined_rows),
        subtitle = stringResource(R.string.settings_home_combined_rows_hint),
        checked = state.settings.homeCombinedRows,
        onToggle = viewModel::toggleHomeCombinedRows,
    )
    // «Ігри» — цілий розділ, а не лише плитка: вимкнено, доки людина не ввімкне сама.
    ToggleRow(
        text = stringResource(R.string.settings_home_games),
        subtitle = stringResource(R.string.settings_home_games_subtitle),
        checked = state.settings.gamesEnabled,
        onToggle = viewModel::toggleGames,
    )
    // Панель сповіщень Android (lounge): системну панель Google TV при Ліхтарі попереду не видно.
    ToggleRow(
        text = stringResource(R.string.settings_home_notifications),
        subtitle = stringResource(R.string.settings_home_notifications_subtitle),
        checked = state.settings.notificationFeed,
        onToggle = viewModel::toggleNotificationFeed,
    )
    if (state.settings.notificationFeed && state.settings.mutedNotificationApps.isNotEmpty()) {
        ValueRow(
            text = stringResource(R.string.settings_home_notifications_muted),
            value = state.settings.mutedNotificationApps.size.toString(),
            onClick = viewModel::unmuteNotificationApps,
            subtitle = stringResource(R.string.settings_home_notifications_muted_subtitle),
        )
    }
    if (state.settings.gamesEnabled) {
        // Дозвіл дають у системі — після повернення перечитуємо.
        var readable by remember { mutableStateOf(viewModel.roms.canReadAll) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            readable = viewModel.roms.canReadAll
            onPauseOrDispose { }
        }
        ValueRow(
            text = stringResource(R.string.settings_home_games_files),
            value = stringResource(if (readable) R.string.settings_home_games_files_on else R.string.settings_home_games_files_off),
            onClick = viewModel.roms::openFileAccessSettings,
            subtitle = stringResource(R.string.settings_home_games_files_subtitle),
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
        var pinOpen by remember { mutableStateOf(false) }
        val pinned = state.recentChannels.count { it.url in state.settings.pinnedChannels }
        ValueRow(
            text = stringResource(R.string.settings_home_channels_pinned),
            value = if (pinned == 0) stringResource(R.string.settings_not_set) else "$pinned",
            onClick = { pinOpen = true },
        )
        if (pinOpen) {
            MultiChoiceDialog(
                title = stringResource(R.string.settings_home_channels_pinned),
                options = state.recentChannels.map { it.url to it.name },
                selected = state.settings.pinnedChannels,
                onToggle = viewModel::togglePinnedChannel,
                onDismiss = { pinOpen = false },
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
}
