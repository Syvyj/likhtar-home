package com.likhtar.home.feature.settings.sections

import com.likhtar.home.core.data.locale
import com.likhtar.home.core.model.Distribution
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.likhtar.home.feature.settings.R
import androidx.compose.ui.focus.FocusRequester
import com.likhtar.home.feature.settings.SettingsUiState
import com.likhtar.home.feature.settings.SettingsViewModel
import com.likhtar.home.feature.settings.TextField
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.ValueRow
import com.likhtar.home.feature.settings.ActionRow

// ─── ПІДКЛЮЧЕННЯ ──────────────────────────────────────────────────────
// Усе технічне — в одному місці: адреси, ключі, акаунти. Раніше воно лежало
// посеред «Кінозалу» між перемикачами для глядача.
@Composable
internal fun ConnectionsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    editedRowFocusRequester: FocusRequester,
) {
    // Збірка для магазину: сервер наш і невидимий; власного ключа TMDB
    // немає, MEGOGO — лише в Україні.
    val store = Distribution.store
    if (!store) {
        GroupTitle(stringResource(R.string.settings_server_group))
        ValueRow(
            text = stringResource(R.string.settings_address),
            value = state.settings.likhtarBaseUrl.ifBlank { stringResource(R.string.settings_not_set) },
            onClick = viewModel::editLikhtarUrl,
            focusRequester = editedRowFocusRequester
                .takeIf { state.lastEditedField == TextField.LIKHTAR_URL },
        )
        ActionRow(
            text = stringResource(R.string.settings_find_server),
            action = stringResource(R.string.settings_search_action),
            onClick = viewModel::findServer,
        )
    }

    // Свій AI людини — лише публічна збірка: домашня ходить у модель через свій сервер.
    // Ключ набирати пультом — мука, тож налаштування лише з телефона.
    if (store) {
        GroupTitle(stringResource(R.string.settings_ai_group))
        val provider = com.likhtar.home.core.data.AiProviders.of(state.settings.aiProvider)
        ActionRow(
            text = provider?.title ?: stringResource(R.string.settings_ai_none),
            subtitle = provider?.let { state.settings.aiModel.ifBlank { it.model } } ?: stringResource(R.string.settings_ai_hint),
            action = stringResource(R.string.settings_phone),
            onClick = viewModel::openPhone,
        )
        if (provider != null) {
            ActionRow(
                text = stringResource(R.string.settings_ai_disconnect),
                action = stringResource(R.string.settings_ai_disconnect_action),
                onClick = viewModel::disconnectAi,
                destructive = true,
            )
        }
    }

    // Групи, яких у збірці для магазину немає зовсім, — у джерелах flavor-а.
    FlavorConnections(viewModel)

    MediaLibraryGroup(viewModel.media)

    // Акаунт MEGOGO — лише домашня збірка.
    if (!store) {
        GroupTitle(stringResource(R.string.settings_accounts_group))
        // Значення — відповідь сервісу, а не «пароль збережено».
        ActionRow(
            text = "MEGOGO",
            subtitle = state.megogoStatus.ifBlank { stringResource(R.string.settings_not_linked) },
            action = stringResource(R.string.settings_refresh),
            onClick = viewModel::refreshMegogo,
        )
    }

    if (!store) {
        GroupTitle("TMDB")
        ValueRow(
            text = stringResource(R.string.settings_own_tmdb_key),
            value = stringResource(if (state.settings.tmdbApiKey.isBlank()) R.string.settings_builtin else R.string.settings_saved),
            onClick = viewModel::editTmdbKey,
            focusRequester = editedRowFocusRequester
                .takeIf { state.lastEditedField == TextField.TMDB_KEY },
        )
    }
}
