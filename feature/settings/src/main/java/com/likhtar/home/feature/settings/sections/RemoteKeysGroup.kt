package com.likhtar.home.feature.settings.sections

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.likhtar.home.core.data.KeyAction
import com.likhtar.home.core.data.KeyPress
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.model.StreamingService
import com.likhtar.home.feature.settings.ActionRow
import com.likhtar.home.feature.settings.GroupTitle
import com.likhtar.home.feature.settings.R
import com.likhtar.home.feature.settings.SettingsViewModel
import com.likhtar.home.feature.settings.SingleChoiceDialog
import com.likhtar.home.feature.settings.ValueRow

/**
 * «Кнопки пульта»: людина ловить свою кнопку й дає їй дію — коротким натисканням і
 * утриманням. Без служби доступності (HomeKeyService) нічого з цього не працює, тож її стан
 * — першим рядком.
 */
@Composable
internal fun RemoteKeysGroup(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val bindings by viewModel.keyBindings.collectAsStateWithLifecycle()
    val apps by viewModel.launchableApps.collectAsStateWithLifecycle()
    var serviceOn by remember { mutableStateOf(false) }
    // Службу вмикають у системних налаштуваннях — повернулись звідти, перечитуємо.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Через AccessibilityManager: Settings.Secure цей ключ застосунку не віддає.
        val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        serviceOn = manager?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty().any { it.id.startsWith("${context.packageName}/") }
    }
    var catching by remember { mutableStateOf(false) }
    // Щойно зловлена кнопка показується й без дії — щоб їй можна було дати лише утримання.
    var fresh by remember { mutableStateOf(emptySet<Int>()) }
    var picking by remember { mutableStateOf<Pair<Int, KeyPress>?>(null) }
    // Вибір після «зловити» відкриває кнопка пульта, а не OK — хвоста OK ковтати не треба.
    var pickArmed by remember { mutableStateOf(false) }

    GroupTitle(stringResource(R.string.settings_keys_group))
    // Службу показуємо, лише коли вона вимкнена: увімкнена — це не налаштування, а технічна деталь.
    if (!serviceOn) ActionRow(
        text = stringResource(R.string.settings_keys_service),
        subtitle = stringResource(if (serviceOn) R.string.settings_keys_service_on else R.string.settings_keys_service_off),
        action = stringResource(R.string.settings_keys_service_enable),
        onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
    )

    val labels = actionLabels(apps.map { it.packageName to it.label })
    val codes = (bindings.map { it.keyCode } + fresh).distinct().sorted()
    codes.forEach { code ->
        val name = keyName(code)
        val short = bindings.firstOrNull { it.keyCode == code && it.press == KeyPress.SHORT }?.action
        val long = bindings.firstOrNull { it.keyCode == code && it.press == KeyPress.LONG }?.action
        ValueRow(
            text = name,
            value = labels[short?.let(::actionCode) ?: SYSTEM].orEmpty(),
            onClick = { pickArmed = false; picking = code to KeyPress.SHORT },
        )
        ValueRow(
            text = stringResource(R.string.settings_keys_hold, name),
            value = labels[long?.let(::actionCode) ?: SYSTEM].orEmpty(),
            onClick = { pickArmed = false; picking = code to KeyPress.LONG },
        )
    }
    if (serviceOn) {
        ActionRow(
            text = stringResource(R.string.settings_keys_add),
            subtitle = stringResource(R.string.settings_keys_add_subtitle),
            action = stringResource(R.string.settings_keys_add_action),
            onClick = { catching = true },
        )
    }

    if (catching) {
        DisposableEffect(Unit) {
            viewModel.startCatchingKey()
            onDispose { viewModel.stopCatchingKey() }
        }
        LaunchedEffect(Unit) {
            viewModel.caughtKeys.collect { code ->
                catching = false
                fresh = fresh + code
                pickArmed = true
                picking = code to KeyPress.SHORT
            }
        }
        LikhtarDialog(onDismiss = { catching = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_keys_catch_title), style = LikhtarType.Label, color = LikhtarColors.TextPrimary)
                Text(stringResource(R.string.settings_keys_catch_hint), style = LikhtarType.Body2, color = LikhtarColors.TextTertiary)
            }
        }
    }

    picking?.let { (code, press) ->
        val current = bindings.firstOrNull { it.keyCode == code && it.press == press }?.action
        val name = keyName(code)
        SingleChoiceDialog(
            title = stringResource(R.string.settings_keys_pick, if (press == KeyPress.LONG) stringResource(R.string.settings_keys_hold, name) else name),
            options = labels.toList(),
            selected = current?.let(::actionCode) ?: SYSTEM,
            onPick = { picked ->
                picking = null
                pickArmed = false
                viewModel.setKeyAction(code, press, parseAction(picked))
            },
            onDismiss = { picking = null },
            armed = pickArmed,
        )
    }
}

/** Код дії → підпис у порядку показу: система, розділи, сторінки стрімінгів, застосунки. */
@Composable
private fun actionLabels(apps: List<Pair<String, String>>): Map<String, String> = buildMap {
    put(SYSTEM, stringResource(R.string.settings_keys_system))
    put(ASSISTANT, stringResource(R.string.settings_keys_assistant))
    SECTIONS.forEach { put("section:${it.route}", stringResource(R.string.settings_keys_section, stringResource(it.titleRes))) }
    StreamingService.withPage.forEach { put("streaming:${it.key}", stringResource(R.string.settings_keys_streaming, it.title)) }
    apps.sortedBy { it.second.lowercase() }.forEach { (pkg, label) -> put("app:$pkg", stringResource(R.string.settings_keys_app, label)) }
}

private fun actionCode(action: KeyAction): String = when (action) {
    is KeyAction.App -> "app:${action.packageName}"
    is KeyAction.Section -> "section:${action.route}"
    is KeyAction.Streaming -> "streaming:${action.brand}"
    KeyAction.Assistant -> ASSISTANT
}

private fun parseAction(code: String): KeyAction? {
    val value = code.substringAfter(':')
    return when (code.substringBefore(':')) {
        "app" -> KeyAction.App(value)
        "section" -> KeyAction.Section(value)
        "streaming" -> KeyAction.Streaming(value)
        "assistant" -> KeyAction.Assistant
        else -> null
    }
}

/** Назва кнопки, як її знає Android: `GUIDE`, `STEM_1`, `PROG_RED`. */
private fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

private const val SYSTEM = "system"
private const val ASSISTANT = "assistant:"

private val SECTIONS = listOf(
    LikhtarSection.HOME, LikhtarSection.SEARCH, LikhtarSection.CINEMA, LikhtarSection.LIVE_TV,
    LikhtarSection.REALITY, LikhtarSection.SMART_HOME, LikhtarSection.APPS,
)
