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

/** Крок входу в Jellyfin: адреса → користувач → пароль (може бути порожнім). */
private enum class JellyfinStep { ADDRESS, USER, PASSWORD }

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
    var jfStep by remember { mutableStateOf<JellyfinStep?>(null) }
    var jfAddress by remember { mutableStateOf("") }
    var jfUser by remember { mutableStateOf("") }
    var jfError by remember { mutableStateOf<String?>(null) }
    var plexHow by remember { mutableStateOf(false) }
    var plexAddress by remember { mutableStateOf(false) }
    var plexCode by remember { mutableStateOf<String?>(null) }
    var plexWait by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var plexServers by remember { mutableStateOf<List<MediaSource>>(emptyList()) }
    var plexError by remember { mutableStateOf<String?>(null) }
    fun addSource(source: MediaSource) = scope.launch { media.add(source); media.rescan() }

    GroupTitle(stringResource(R.string.settings_media_group))
    sources.forEach { source ->
        ValueRow(
            text = source.name,
            value = when (source.kind) {
                MediaSource.Kind.JELLYFIN -> "Jellyfin"
                MediaSource.Kind.PLEX -> "Plex"
                else -> source.kind.name
            },
            subtitle = source.address.takeIf { source.kind != MediaSource.Kind.DLNA },
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
    ActionRow(
        text = stringResource(R.string.settings_media_add_jellyfin),
        subtitle = jfError?.let { stringResource(R.string.settings_media_jellyfin_failed, it) } ?: stringResource(R.string.settings_media_add_jellyfin_subtitle),
        action = stringResource(R.string.settings_media_add_action),
        onClick = {
            jfError = null
            jfStep = JellyfinStep.ADDRESS
        },
    )
    ActionRow(
        text = stringResource(R.string.settings_media_add_plex),
        subtitle = plexError?.let { stringResource(R.string.settings_media_plex_failed, it) } ?: stringResource(R.string.settings_media_add_plex_subtitle),
        action = stringResource(R.string.settings_media_add_action),
        onClick = {
            plexError = null
            plexHow = true
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

    when (jfStep) {
        JellyfinStep.ADDRESS -> TextInputDialog(
            title = stringResource(R.string.settings_media_jellyfin_address),
            initialValue = jfAddress,
            placeholder = stringResource(R.string.settings_media_jellyfin_address_hint),
            onSubmit = { value ->
                jfAddress = value.trim()
                jfStep = if (jfAddress.isBlank()) null else JellyfinStep.USER
            },
            onDismiss = { jfStep = null },
        )
        JellyfinStep.USER -> TextInputDialog(
            title = stringResource(R.string.settings_media_jellyfin_user),
            initialValue = jfUser,
            onSubmit = { value ->
                jfUser = value.trim()
                jfStep = if (jfUser.isBlank()) null else JellyfinStep.PASSWORD
            },
            onDismiss = { jfStep = null },
        )
        JellyfinStep.PASSWORD -> TextInputDialog(
            title = stringResource(R.string.settings_media_jellyfin_password),
            initialValue = "",
            allowClear = true,
            onSubmit = { value ->
                jfStep = null
                scope.launch {
                    media.loginJellyfin(jfAddress, jfUser, value)
                        .onSuccess { media.add(it); media.rescan() }
                        .onFailure { jfError = it.message ?: it.javaClass.simpleName }
                }
            },
            onDismiss = { jfStep = null },
        )
        null -> Unit
    }

    if (plexHow) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_media_plex_how),
            options = listOf("account" to stringResource(R.string.settings_media_plex_account), "address" to stringResource(R.string.settings_media_plex_by_address)),
            selected = "account",
            onPick = { how ->
                plexHow = false
                if (how == "address") {
                    plexAddress = true
                } else {
                    val client = media.newId()
                    plexWait = scope.launch {
                        val pin = media.plexPin(client).getOrElse { plexError = it.message ?: it.javaClass.simpleName; return@launch }
                        plexCode = pin.code
                        media.waitPlex(client, pin)
                            .onSuccess { found -> if (found.size == 1) addSource(found[0]) else plexServers = found }
                            .onFailure { plexError = it.message ?: it.javaClass.simpleName }
                        plexCode = null
                    }
                }
            },
            onDismiss = { plexHow = false },
        )
    }
    plexCode?.let { code ->
        PlexLinkDialog(code = code, onDismiss = {
            plexWait?.cancel()
            plexCode = null
        })
    }
    plexServers.takeIf { it.isNotEmpty() }?.let { found ->
        SingleChoiceDialog(
            title = stringResource(R.string.settings_media_plex_pick_server),
            options = found.map { it.id to it.name },
            selected = found.first().id,
            onPick = { id ->
                plexServers = emptyList()
                found.firstOrNull { it.id == id }?.let(::addSource)
            },
            onDismiss = { plexServers = emptyList() },
            armed = true,
        )
    }
    if (plexAddress) {
        TextInputDialog(
            title = stringResource(R.string.settings_media_plex_address),
            initialValue = "",
            placeholder = stringResource(R.string.settings_media_plex_address_hint),
            onSubmit = { value ->
                plexAddress = false
                if (value.isNotBlank()) scope.launch {
                    media.plexByAddress(value).onSuccess { addSource(it) }.onFailure { plexError = it.message ?: it.javaClass.simpleName }
                }
            },
            onDismiss = { plexAddress = false },
        )
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
