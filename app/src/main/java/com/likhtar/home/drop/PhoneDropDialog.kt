package com.likhtar.home.drop

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.R
import com.likhtar.home.core.data.DropState
import com.likhtar.home.core.data.PhoneDrop
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.QrCode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class PhoneDropViewModel @Inject constructor(
    val drop: PhoneDrop,
    private val settings: SettingsRepository,
    private val roms: com.likhtar.home.core.data.RomLibrary,
) : ViewModel() {
    /** «До ігор»: образ уже в Download/ROMs — вмикаємо розділ, якщо він ще вимкнений. */
    fun openGames(then: () -> Unit) {
        viewModelScope.launch {
            settings.setGamesEnabled(true)
            then()
            // «Ігри» могли вже бути на екрані — тоді самі вони теку не перечитають.
            roms.refresh()
        }
    }

    /** Відео — у плеєр із налаштувань (як усе в лаунчері), решта — тим, що система вважає своїм. */
    fun open(context: Context, uri: Uri, name: String, mime: String) {
        viewModelScope.launch {
            val player = if (mime.startsWith("video/")) settings.settings.first().videoPlayerPackage else ""
            openFile(context, uri, name, mime, player)
        }
    }

    /**
     * Плейлист із телефона — у ТВ Ефір: копія у приватні файли лаунчера (файл у «Завантаженнях»
     * людина може видалити чи перейменувати) і `file://` замість адреси. `true` — вийшло.
     */
    suspend fun usePlaylist(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = java.io.File(context.filesDir, "playlist.m3u")
            context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
            // Мітка часу в адресі — щоб кеш каналів побачив новий файл, а не віддав старий список.
            settings.setTvPlaylistUrl(target.toURI().toString() + "#" + System.currentTimeMillis())
        }.isSuccess
    }
}

/** M3U за назвою: MIME у різних телефонів різний (audio/x-mpegurl, application/octet-stream…). */
private fun isPlaylist(name: String) = name.lowercase().let { it.endsWith(".m3u") || it.endsWith(".m3u8") }

/**
 * «Передати на приставку»: QR → сторінка на телефоні → файл чи посилання на ТВ, і вікно саме
 * пропонує, що з ним зробити: відео — дивитись, фото — показати, APK — встановити.
 */
@Composable
fun PhoneDropDialog(
    onDismiss: () -> Unit,
    onOpenGames: () -> Unit = {},
    viewModel: PhoneDropViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val url = remember { viewModel.drop.start() }
    val state by viewModel.drop.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { viewModel.drop.reset() } }
    val actionFocus = remember { FocusRequester() }

    LikhtarDialog(onDismiss = onDismiss, width = 640.dp, armed = true) {
        // Крупно, як «З телефона» в налаштуваннях: вікно читають з дивана.
        Text(stringResource(R.string.drop_title), style = LikhtarType.PageTitle, color = LikhtarColors.TextPrimary)
        // Файл уже йде чи прийшов — телефон на зв'язку, QR лише забирає місце (з фото кнопка
        // «Закрити» випадала за екран).
        val showQr = state is DropState.Waiting || state is DropState.Connected || state is DropState.Failed
        if (url == null) {
            Text(stringResource(R.string.drop_no_network), style = LikhtarType.Body1, color = LikhtarColors.TextSecondary, modifier = Modifier.padding(vertical = 10.dp))
        } else if (showQr) {
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QrCode(content = url, size = 200.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.drop_step1), style = LikhtarType.TitleLead, color = LikhtarColors.TextPrimary)
                    Text(stringResource(R.string.drop_step2), style = LikhtarType.TitleLead, color = LikhtarColors.TextPrimary)
                    Text(url, style = LikhtarType.Body1, color = LikhtarColors.TextSecondary)
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }

        when (val s = state) {
            DropState.Waiting -> Status(stringResource(R.string.drop_waiting))
            DropState.Connected -> Status(stringResource(R.string.drop_connected))
            is DropState.Receiving -> {
                Status(stringResource(R.string.drop_receiving, s.name, (s.done * 100 / s.total.coerceAtLeast(1)).toInt(), size(s.total)))
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp).height(4.dp).background(LikhtarColors.SurfaceHigh, RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(s.done.toFloat() / s.total.coerceAtLeast(1)).height(4.dp).background(LikhtarColors.Amber, RoundedCornerShape(2.dp)))
                }
            }
            is DropState.Received -> {
                Status(stringResource(R.string.drop_received, s.name), ok = true)
                when {
                    s.mime.startsWith("image/") -> AsyncImage(
                        model = s.uri,
                        contentDescription = s.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(bottom = 12.dp),
                    )
                    isPlaylist(s.name) -> {
                        var done by remember(s.uri) { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()
                        if (done) Status(stringResource(R.string.drop_playlist_done), ok = true)
                        else Action(stringResource(R.string.drop_use_playlist), actionFocus) {
                            scope.launch { done = viewModel.usePlaylist(context, s.uri) }
                        }
                    }
                    s.mime.startsWith("video/") || s.mime.startsWith("audio/") ->
                        Action(stringResource(R.string.drop_watch), actionFocus) { viewModel.open(context, s.uri, s.name, s.mime) }
                    s.mime == APK -> Action(stringResource(R.string.drop_install), actionFocus) { install(context, s.uri, s.name) }
                    PhoneDrop.isRom(s.name) -> Action(stringResource(R.string.drop_open_games), actionFocus) { viewModel.openGames(onOpenGames) }
                    else -> Status(stringResource(R.string.drop_saved, "Download/${PhoneDrop.FOLDER}"))
                }
            }
            is DropState.Link -> {
                Status(s.url, ok = true)
                Action(stringResource(R.string.drop_open_link), actionFocus) { openLink(context, s.url) }
            }
            is DropState.Failed -> Status(s.message)
        }
        LikhtarButton(label = stringResource(R.string.drop_close), onClick = onDismiss)
    }

    // Прийшло, що можна відкрити, — фокус одразу на дію: людина з пультом, а не з телефоном.
    LaunchedEffect(state) {
        if (state is DropState.Received || state is DropState.Link) runCatching { actionFocus.requestFocus() }
    }
}

@Composable
private fun Status(text: String, ok: Boolean = false) {
    Text(
        text = text,
        style = LikhtarType.Body1,
        color = if (ok) LikhtarColors.AccentBright else LikhtarColors.TextSecondary,
        maxLines = 2,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun Action(label: String, focus: FocusRequester, onClick: () -> Unit) {
    LikhtarButton(label = label, onClick = onClick, primary = true, modifier = Modifier.padding(bottom = 10.dp).focusRequester(focus))
}

private const val APK = "application/vnd.android.package-archive"

private fun size(b: Long): String = if (b > 1_000_000_000) "%.1f ГБ".format(b / 1e9) else "%.0f МБ".format(b / 1e6)

private fun openFile(context: Context, uri: Uri, name: String, mime: String, player: String) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra("title", name.substringBeforeLast('.'))
    val withPlayer = if (player.isNotBlank()) Intent(intent).setPackage(player) else null
    runCatching { context.startActivity(withPlayer ?: intent) }
        .recoverCatching { context.startActivity(Intent.createChooser(intent, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Log.w(TAG, "відкрити $name", it) }
}

/** APK: спершу дозвіл «встановлювати з лаунчера» (система питає раз), далі — системний встановлювач. */
private fun install(context: Context, uri: Uri, name: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Log.w(TAG, "дозвіл на встановлення", it) }
        return
    }
    openFile(context, uri, name, APK, "")
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "посилання $url", e)
    }
}

private const val TAG = "PhoneDrop"
