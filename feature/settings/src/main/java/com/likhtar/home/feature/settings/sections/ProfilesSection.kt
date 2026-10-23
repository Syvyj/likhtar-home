package com.likhtar.home.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.likhtar.home.feature.settings.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.likhtar.home.feature.settings.SettingsUiState
import com.likhtar.home.feature.settings.SettingsViewModel
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.ValueRow
import com.likhtar.home.feature.settings.ActionRow

// ─── ПРОФІЛІ Й PIN ────────────────────────────────────────────────────
@Composable
internal fun ProfilesSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    // Профілі живуть на сервері — лише домашня збірка; у публічній вони мовчки не створювались.
    if (!com.likhtar.home.core.model.Distribution.store) {
        GroupTitle(stringResource(R.string.settings_profiles_group))
        val profiles by viewModel.profileList.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.refreshProfiles() }
        profiles.forEach { profile ->
            ActionRow(
                text = if (profile.kids) stringResource(R.string.settings_profile_kids, profile.name) else profile.name,
                action = stringResource(if (profile.id == state.settings.activeProfileId) R.string.settings_profile_active else R.string.settings_profile_select),
                onClick = { viewModel.selectProfile(profile.id) },
            )
        }
        ActionRow(
            text = stringResource(R.string.settings_profile_add),
            subtitle = stringResource(R.string.settings_profile_add_subtitle),
            action = stringResource(R.string.settings_profile_create),
            onClick = viewModel::addProfile,
        )
    }

    GroupTitle(stringResource(R.string.settings_parental_group))
    ValueRow(
        text = stringResource(R.string.settings_pin),
        value = stringResource(if (state.pinSet) R.string.settings_pin_created else R.string.settings_pin_not_created),
        onClick = viewModel::requestPinReset,
        subtitle = stringResource(R.string.settings_pin_subtitle),
    )
}
