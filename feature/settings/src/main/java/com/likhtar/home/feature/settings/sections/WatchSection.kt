package com.likhtar.home.feature.settings.sections

import com.likhtar.home.core.data.locale
import com.likhtar.home.core.model.Distribution
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.likhtar.home.feature.settings.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.likhtar.home.feature.settings.SettingsUiState
import com.likhtar.home.feature.settings.SettingsViewModel
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.ToggleRow
import com.likhtar.home.feature.settings.ValueRow
import com.likhtar.home.feature.settings.TitleActionsDialog
import com.likhtar.home.feature.settings.CountryPickerDialog
import com.likhtar.home.feature.settings.HiddenServicesDialog

// ─── ПЕРЕГЛЯД ─────────────────────────────────────────────────────────
@Composable
internal fun WatchSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    // Плеєр — і для публічної збірки: канали свого плейлиста грають саме в ньому.
    run {
        GroupTitle(stringResource(R.string.settings_watch_player_group))
        // remember: назва береться з PackageManager, ходити туди на кожну
        // рекомпозицію не треба.
        val playerLabel = remember(state.settings.videoPlayerPackage) {
            viewModel.videoPlayerLabel(state.settings.videoPlayerPackage)
        }
        ValueRow(
            text = stringResource(R.string.settings_watch_player),
            value = playerLabel,
            onClick = viewModel::cycleVideoPlayer,
            subtitle = stringResource(R.string.settings_watch_player_subtitle),
        )
    }

    GroupTitle(stringResource(R.string.settings_watch_rows_group))
    // Українська озвучка — питання лише українського інтерфейсу (так само рахує каталог).
    if (state.settings.locale.language == "uk") ToggleRow(
        text = stringResource(R.string.settings_watch_without_ua),
        subtitle = stringResource(R.string.settings_watch_without_ua_subtitle),
        checked = state.settings.showWithoutUaAudio,
        onToggle = viewModel::toggleShowWithoutUaAudio,
    )
    ToggleRow(
        text = stringResource(R.string.settings_watch_hide_watched),
        subtitle = stringResource(R.string.settings_watch_hide_watched_subtitle),
        checked = state.settings.hideWatched,
        onToggle = viewModel::toggleHideWatched,
    )
    ToggleRow(
        text = stringResource(R.string.settings_watch_only_my_apps),
        subtitle = stringResource(R.string.settings_watch_only_my_apps_subtitle),
        checked = state.settings.onlyMyApps,
        onToggle = viewModel::toggleOnlyMyApps,
    )
    var servicesOpen by remember { mutableStateOf(false) }
    val hidden = state.settings.hiddenServices
    ValueRow(
        text = stringResource(R.string.settings_watch_hidden_services),
        value = if (hidden.isEmpty()) stringResource(R.string.settings_watch_hidden_none) else hidden.size.toString(),
        onClick = { servicesOpen = true },
        subtitle = stringResource(R.string.settings_watch_hidden_services_subtitle),
    )
    if (servicesOpen) {
        HiddenServicesDialog(hidden = hidden, onToggle = viewModel::toggleHiddenService, onDismiss = { servicesOpen = false })
    }
    ToggleRow(
        text = stringResource(R.string.settings_watch_anime),
        subtitle = stringResource(R.string.settings_watch_anime_subtitle),
        checked = state.settings.showAnime,
        onToggle = viewModel::toggleShowAnime,
    )

    GroupTitle(stringResource(R.string.settings_watch_title_page_group))
    ToggleRow(
        text = stringResource(R.string.settings_watch_live_stills),
        subtitle = stringResource(R.string.settings_watch_live_stills_subtitle),
        checked = state.settings.titleLiveStills,
        onToggle = viewModel::toggleTitleLiveStills,
    )
    ToggleRow(
        text = stringResource(R.string.settings_watch_trailer_up),
        subtitle = stringResource(R.string.settings_watch_trailer_up_subtitle),
        checked = state.settings.titleTrailerOnUp,
        onToggle = viewModel::toggleTrailerOnUp,
    )
    ToggleRow(
        text = stringResource(R.string.settings_watch_title_under_logo),
        subtitle = stringResource(R.string.settings_watch_title_under_logo_subtitle),
        checked = state.settings.titleUnderLogo,
        onToggle = viewModel::toggleTitleUnderLogo,
    )
    var actionsOpen by remember { mutableStateOf(false) }
    ValueRow(
        text = stringResource(R.string.settings_button_order),
        value = viewModel.titleActionLabel(state.settings.titleActionsOrder.first()),
        onClick = { actionsOpen = true },
    )
    if (actionsOpen) {
        TitleActionsDialog(
            order = state.settings.titleActionsOrder,
            label = viewModel::titleActionLabel,
            onMove = viewModel::moveTitleAction,
            onDismiss = { actionsOpen = false },
        )
    }
    // Вікно вибору — стан екрана, моделі тут робити нічого.
    var countriesOpen by remember { mutableStateOf(false) }
    ValueRow(
        text = stringResource(R.string.settings_watch_availability),
        value = viewModel.availabilityCountriesLabel(state.settings.availabilityCountries),
        onClick = { countriesOpen = true },
        subtitle = stringResource(R.string.settings_watch_availability_subtitle),
    )
    if (countriesOpen) {
        CountryPickerDialog(
            countries = state.tvCountries,
            selected = state.settings.availabilityCountries,
            onToggle = viewModel::toggleAvailabilityCountry,
            onAuto = viewModel::clearAvailabilityCountries,
            onDismiss = { countriesOpen = false },
        )
    }
}
