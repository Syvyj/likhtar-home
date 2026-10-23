package com.likhtar.home.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.likhtar.home.core.catalog.media.DlnaServer
import com.likhtar.home.core.catalog.media.MediaLibrary
import com.likhtar.home.core.catalog.media.MediaSource
import com.likhtar.home.core.catalog.media.SmbAddress
import com.likhtar.home.core.designsystem.TextInputDialog
import com.likhtar.home.feature.settings.ActionRow
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.R
import com.likhtar.home.feature.settings.SingleChoiceDialog
import com.likhtar.home.feature.settings.ValueRow
import kotlinx.coroutines.launch

/** Крок додавання SMB-папки: адреса → користувач → пароль (лише якщо користувач є). */
private enum class SmbStep { ADDRESS, USER, PASSWORD }

/**
 * «Медіатека»: свої DLNA-сервери й SMB-папки. Знайдене сканом стає картками Кінозалу
 * («З медіатеки») і кнопкою на сторінці фільму.
 */
@Composable
internal fun MediaLibraryGroup(media: MediaLibrary) {
    val scope = rememberCoroutineScope()
    val sources by media.sources.collectAsStateWithLifecycle(emptyList())
    val library by media.state.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<DlnaServer>?>(null) }
    var step by remember { mutableStateOf<SmbStep?>(null) }
    var draft by remember { mutableStateOf(MediaSource("", MediaSource.Kind.SMB, "", "")) }
    var smbError by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<MediaSource?>(null) }

    GroupTitle(stringResource(R.string.settings_media_group))
    sources.forEach { source ->
        ValueRow(
            text = source.name,
            value = source.kind.name,
            subtitle = source.address.takeIf { source.kind == MediaSource.Kind.SMB },
            onClick = { removing = source },
        )
    }
    ActionRow(
        text = stringResource(R.string.settings_media_find_dlna),
        subtitle = if (found?.isEmpty() == true) stringResource(R.string.settings_media_none_found) else stringResource(R.string.settings_media_find_dlna_subtitle),
        action = stringResource(if (searching) R.string.settings_media_searching else R.string.settings_search_action),
        onClick = {
            if (!searching) scope.launch {
                searching = true
                found = media.discoverDlna().filterNot { s -> sources.any { it.address == s.location } }
                searching = false
            }
        },
    )
    ActionRow(
        text = stringResource(R.string.settings_media_add_smb),
        subtitle = smbError?.let { stringResource(R.string.settings_media_smb_failed, it) } ?: stringResource(R.string.settings_media_add_smb_subtitle),
        action = stringResource(R.string.settings_media_add_action),
        onClick = {
            smbError = null
            draft = MediaSource(media.newId(), MediaSource.Kind.SMB, "", "")
            step = SmbStep.ADDRESS
        },
    )
    if (sources.isNotEmpty()) {
        ActionRow(
            text = stringResource(R.string.settings_media_rescan),
            subtitle = library.status.ifBlank { stringResource(R.string.settings_media_never) },
            action = stringResource(if (library.scanning) R.string.settings_media_scanning else R.string.settings_media_rescan_action),
            onClick = { if (!library.scanning) media.rescan() },
        )
    }

    found?.takeIf { it.isNotEmpty() }?.let { servers ->
        SingleChoiceDialog(
            title = stringResource(R.string.settings_media_pick_dlna),
            options = servers.map { it.location to it.name },
            selected = "",
            onPick = { location ->
                found = null
                val server = servers.first { it.location == location }
                scope.launch {
                    media.add(MediaSource(media.newId(), MediaSource.Kind.DLNA, server.name, server.location))
                    media.rescan()
                }
            },
            onDismiss = { found = null },
            // Вікно з'являється само, коли пошук скінчився, — хвоста OK ковтати нема чого.
            armed = true,
        )
    }

    when (step) {
        SmbStep.ADDRESS -> TextInputDialog(
            title = stringResource(R.string.settings_media_smb_address),
            initialValue = draft.address.ifBlank { "smb://" },
            placeholder = stringResource(R.string.settings_media_smb_address_hint),
            onSubmit = { value ->
                val address = SmbAddress.parse(value)
                if (address == null) {
                    step = null
                    smbError = value
                } else {
                    draft = draft.copy(address = "smb://${address.host}/${address.share}/${address.path}".trimEnd('/'), name = address.path.substringAfterLast('/').ifBlank { address.share })
                    step = SmbStep.USER
                }
            },
            onDismiss = { step = null },
        )
        SmbStep.USER -> TextInputDialog(
            title = stringResource(R.string.settings_media_smb_user),
            initialValue = draft.user,
            description = stringResource(R.string.settings_media_smb_user_hint),
            allowClear = true,
            onSubmit = { value ->
                draft = draft.copy(user = value.trim())
                if (draft.user.isBlank()) {
                    step = null
                    save(scope, media, draft) { smbError = it }
                } else {
                    step = SmbStep.PASSWORD
                }
            },
            onDismiss = { step = null },
        )
        SmbStep.PASSWORD -> TextInputDialog(
            title = stringResource(R.string.settings_media_smb_password),
            initialValue = "",
            allowClear = true,
            onSubmit = { value ->
                step = null
                save(scope, media, draft.copy(password = value)) { smbError = it }
            },
            onDismiss = { step = null },
        )
        null -> Unit
    }

    removing?.let { source ->
        SingleChoiceDialog(
            title = stringResource(R.string.settings_media_remove_title, source.name),
            options = listOf("keep" to stringResource(R.string.settings_media_keep), "remove" to stringResource(R.string.settings_media_remove)),
            selected = "keep",
            onPick = { picked ->
                removing = null
                if (picked == "remove") scope.launch { media.remove(source.id) }
            },
            onDismiss = { removing = null },
            // На приставці OK рядка спрацьовує на відпусканні — хвоста вже немає.
            armed = true,
        )
    }
}

/** Перевірити папку й зберегти; не відкрилась — текст помилки в рядок «Додати». */
private fun save(scope: kotlinx.coroutines.CoroutineScope, media: MediaLibrary, source: MediaSource, onError: (String) -> Unit) {
    scope.launch {
        val error = media.check(source)
        if (error != null) {
            onError(error)
        } else {
            media.add(source)
            media.rescan()
        }
    }
}
