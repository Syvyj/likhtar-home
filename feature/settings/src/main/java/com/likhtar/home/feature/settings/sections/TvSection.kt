package com.likhtar.home.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.likhtar.home.feature.settings.R
import androidx.compose.ui.focus.FocusRequester
import com.likhtar.home.feature.settings.SettingsUiState
import com.likhtar.home.feature.settings.SettingsViewModel
import com.likhtar.home.feature.settings.TextField
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.ValueRow
import com.likhtar.home.feature.settings.ChoiceRow
import com.likhtar.home.feature.settings.ActionRow
import com.likhtar.home.feature.settings.Note

// ─── ТЕЛЕБАЧЕННЯ ──────────────────────────────────────────────────────
@Composable
internal fun TvSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    editedRowFocusRequester: FocusRequester,
) {
    GroupTitle(stringResource(R.string.settings_tv_sources_group))
    ValueRow(
        text = stringResource(R.string.settings_tv_playlist),
        value = state.settings.tvPlaylistUrl.ifBlank { stringResource(R.string.settings_builtin) },
        onClick = viewModel::editTvPlaylistUrl,
        subtitle = stringResource(R.string.settings_tv_playlist_subtitle),
        focusRequester = editedRowFocusRequester
            .takeIf { state.lastEditedField == TextField.TV_PLAYLIST_URL },
    )
    ValueRow(
        text = stringResource(R.string.settings_epg_title),
        value = state.settings.tvEpgUrl.ifBlank { stringResource(R.string.settings_not_set) },
        onClick = viewModel::editTvEpgUrl,
        subtitle = stringResource(R.string.settings_tv_epg_subtitle),
        focusRequester = editedRowFocusRequester
            .takeIf { state.lastEditedField == TextField.TV_EPG_URL },
    )

    val hidden by viewModel.hiddenChannels.collectAsStateWithLifecycle()
    if (hidden.isNotEmpty()) {
        GroupTitle(stringResource(R.string.settings_tv_channels_group))
        ActionRow(
            text = stringResource(R.string.settings_tv_hidden),
            subtitle = hidden.size.toString(),
            action = stringResource(R.string.settings_tv_restore_all),
            onClick = viewModel::restoreHiddenChannels,
        )
    }

    // Країни каналів — каталоги MEGOGO/Sweet з нашого сервера; публічна збірка живе плейлистом.
    if (com.likhtar.home.core.model.Distribution.store) return
    GroupTitle(stringResource(R.string.settings_tv_countries_group))
    ValueRow(
        text = stringResource(R.string.settings_tv_find_country),
        value = state.tvCountryFilter.ifBlank { stringResource(R.string.settings_tv_filter_all) },
        onClick = viewModel::editTvCountryFilter,
        focusRequester = editedRowFocusRequester
            .takeIf { state.lastEditedField == TextField.TV_COUNTRY_FILTER },
    )
    if (state.tvCountries.isEmpty()) {
        Note(stringResource(R.string.settings_countries_loading))
    }
    val filter = state.tvCountryFilter.trim().lowercase()
    // Вибрані — вгору: інакше після вибору країна губиться серед 250 інших.
    val countries = state.tvCountries
        .filter { filter.isBlank() || it.name.lowercase().contains(filter) }
        .sortedByDescending { it.code in state.settings.tvCountries }
        .take(COUNTRY_ROWS)
    countries.forEach { country ->
        ChoiceRow(
            text = "${country.flag} ${country.name}",
            selected = country.code in state.settings.tvCountries,
            onSelect = { viewModel.toggleTvCountry(country.code) },
        )
    }
}

/** Скільки країн показуємо за раз: далі список стає непрохідним пультом, є пошук. */
private const val COUNTRY_ROWS = 40
