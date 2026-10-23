package com.likhtar.home.feature.settings.sections

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.likhtar.home.feature.settings.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.likhtar.home.core.designsystem.PhoneQrDialog
import com.likhtar.home.core.smarthome.HaLinkStatus
import androidx.compose.ui.focus.FocusRequester
import com.likhtar.home.core.smarthome.SmartHomeStatus
import com.likhtar.home.feature.settings.SettingsUiState
import com.likhtar.home.feature.settings.SettingsViewModel
import com.likhtar.home.feature.settings.TextField
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.ToggleRow
import com.likhtar.home.feature.settings.ValueRow
import com.likhtar.home.feature.settings.ActionRow
import com.likhtar.home.feature.settings.Note

// ─── СМАРТ ДІМ ────────────────────────────────────────────────────────
@Composable
internal fun SmartHomeSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    editedRowFocusRequester: FocusRequester,
) {
    // Заголовок — над перемикачем: у «Підключеннях» він інакше стояв би під «Медіатекою».
    GroupTitle("Home Assistant")
    ToggleRow(
        text = stringResource(R.string.settings_smart_home_toggle),
        subtitle = stringResource(R.string.settings_smart_home_toggle_subtitle),
        checked = state.settings.smartHomeEnabled,
        onToggle = viewModel::toggleSmartHome,
    )
    if (state.settings.smartHomeEnabled) {
        // Вхід з телефону — головний шлях: без токена, пароль вводиться на телефоні в HA.
        val linked = remember(state.smartHomeStatus) { viewModel.haLinkedFromPhone() }
        ActionRow(
            text = stringResource(R.string.settings_ha_phone_link),
            subtitle = stringResource(if (linked) R.string.settings_ha_phone_linked else R.string.settings_ha_phone_hint),
            action = stringResource(if (linked) R.string.settings_ha_again else R.string.settings_ha_show_qr),
            onClick = viewModel::startHaPhoneLink,
        )
        if (linked) {
            ActionRow(text = stringResource(R.string.settings_ha_forget_phone), action = stringResource(R.string.settings_ha_forget), onClick = viewModel::forgetHaPhoneLink)
        }
        val qr by viewModel.haLink.collectAsStateWithLifecycle()
        val linkStatus by viewModel.haLinkStatus.collectAsStateWithLifecycle()
        qr?.let { link ->
            PhoneQrDialog(
                title = stringResource(R.string.settings_ha_qr_title),
                url = link.url,
                lines = listOf(
                    stringResource(R.string.settings_ha_qr_line1),
                    stringResource(R.string.settings_ha_qr_line2),
                ),
                status = when (val s = linkStatus) {
                    HaLinkStatus.Waiting -> null
                    HaLinkStatus.Opened -> stringResource(R.string.settings_ha_link_opened)
                    HaLinkStatus.Done -> stringResource(R.string.settings_ha_link_done)
                    is HaLinkStatus.Failed -> stringResource(R.string.settings_ha_link_failed, s.message)
                },
                statusOk = linkStatus == HaLinkStatus.Done,
                onDismiss = viewModel::closeHaPhoneLink,
            )
        }
        // Порожні поля при робочому з'єднанні — отже, адреса й токен узяті з файлу.
        val fromFile = state.smartHomeStatus !is SmartHomeStatus.NotConfigured
        ValueRow(
            text = stringResource(R.string.settings_address),
            value = state.settings.smartHomeUrl.ifBlank {
                stringResource(if (linked) R.string.settings_ha_from_phone else if (fromFile) R.string.settings_from_file else R.string.settings_not_set)
            },
            onClick = viewModel::editSmartHomeUrl,
            focusRequester = editedRowFocusRequester
                .takeIf { state.lastEditedField == TextField.HA_URL },
        )
        ValueRow(
            text = stringResource(R.string.settings_ha_token),
            value = when {
                state.settings.smartHomeToken.isNotBlank() -> maskedToken(state.settings.smartHomeToken)
                linked -> stringResource(R.string.settings_ha_token_not_needed)
                fromFile -> stringResource(R.string.settings_from_file)
                else -> maskedToken(state.settings.smartHomeToken)
            },
            onClick = viewModel::editSmartHomeToken,
            focusRequester = editedRowFocusRequester
                .takeIf { state.lastEditedField == TextField.HA_TOKEN },
        )
        ActionRow(
            text = stringResource(R.string.settings_ha_connection),
            subtitle = smartHomeStatusText(state.smartHomeStatus),
            action = stringResource(R.string.settings_check),
            onClick = viewModel::testSmartHomeConnection,
        )
        ValueRow(
            text = stringResource(R.string.settings_ha_refresh),
            value = stringResource(R.string.settings_seconds, state.settings.smartHomeRefreshSeconds),
            onClick = viewModel::cycleSmartHomeRefresh,
        )
        ToggleRow(
            text = stringResource(R.string.settings_ha_voice),
            subtitle = stringResource(R.string.settings_ha_voice_subtitle),
            checked = state.settings.voiceCommandsEnabled,
            onToggle = viewModel::toggleVoiceCommands,
        )
        // Токен довгий: хто не хоче вводити пультом — кладе файл.
    }
}

/**
 * Токен на екрані телевізора не показуємо ніколи: лишаємо хвіст, якого досить, щоб
 * упізнати «той самий чи інший», але замало, щоб ним скористатися.
 */
@Composable
private fun maskedToken(token: String): String = when {
    token.isBlank() -> stringResource(R.string.settings_not_set)
    token.length <= 4 -> stringResource(R.string.settings_saved)
    else -> stringResource(R.string.settings_token_saved_tail, token.takeLast(4))
}

@Composable
private fun smartHomeStatusText(status: SmartHomeStatus): String = when (status) {
    SmartHomeStatus.NotConfigured -> stringResource(R.string.settings_ha_not_configured)
    SmartHomeStatus.Connecting -> stringResource(R.string.settings_ha_connecting)
    is SmartHomeStatus.Connected -> pluralStringResource(
        com.likhtar.home.core.smarthome.R.plurals.smarthome_devices, status.deviceCount, status.deviceCount,
    )
    is SmartHomeStatus.Error -> stringResource(R.string.settings_ha_error)
}
