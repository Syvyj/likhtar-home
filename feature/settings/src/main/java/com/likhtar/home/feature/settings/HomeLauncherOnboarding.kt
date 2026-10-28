package com.likhtar.home.feature.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.LaunchedEffect
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.unit.Dp
import com.likhtar.home.core.data.RemapperApp
import com.likhtar.home.core.data.HomeTakeover
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Text
import com.likhtar.home.core.data.HomeBlocker
import com.likhtar.home.core.data.HomeLauncherRepository
import com.likhtar.home.core.data.HomeLauncherStatus
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.QrCode

/**
 * «Зробити Likhtar домашнім» — статус кнопки HOME і спроба її забрати.
 *
 * Один вміст на два місця: оверлей при першому запуску ([onDismiss] показує «Пізніше») і
 * розділ у налаштуваннях, куди можна повернутися будь-коли ([onDismiss] = null).
 *
 * Лаунчер тут нічого не вимикає й нічого не перехоплює — лише відкриває системні екрани
 * вибору. Якщо пристрій їх не має, чесно показує команду, яку користувач виконає сам.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeLauncherOnboarding(
    repository: HomeLauncherRepository,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(repository.status()) }
    // ADB-блок показуємо лише тим, хто вже спробував і повернувся ні з чим: інструкція
    // з термінала на порожньому місці лякає більше, ніж допомагає.
    var attempted by remember { mutableStateOf(false) }
    val actionFocus = remember { FocusRequester() }

    // Статус міняється лише поки ми в системному діалозі — отже перевіряти його треба
    // на поверненні, а не таймером.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = repository.status()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Оверлей (є «Пізніше») — модальний, як майстер: головний екран під ним ставить свій
    // фокус уже після нас, і кнопки лишались без фокуса.
    var inside by remember { mutableStateOf(false) }
    if (onDismiss != null) {
        LaunchedEffect(Unit) {
            repeat(40) {
                if (inside) return@repeat
                runCatching { actionFocus.requestFocus() }
                kotlinx.coroutines.delay(50)
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onDismiss != null) {
                    Modifier
                        .onFocusChanged { inside = it.hasFocus }
                        .focusProperties { exit = { FocusRequester.Cancel } }
                        .focusGroup()
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                when {
                    status.isDefault -> R.string.settings_onboarding_ready
                    attempted -> R.string.settings_onboarding_blocked
                    else -> R.string.settings_onboarding_one_step
                },
            ),
            style = LikhtarType.Kicker,
            color = LikhtarColors.AccentBright,
        )
        Text(
            text = stringResource(
                if (status.isDefault) R.string.settings_onboarding_is_home else R.string.settings_onboarding_make_home,
            ),
            style = LikhtarType.HeroTitle,
            color = LikhtarColors.TextPrimary,
        )

        if (!status.isDefault) {
            Text(
                text = stringResource(R.string.settings_onboarding_explain),
                style = LikhtarType.Body2,
                color = LikhtarColors.TextTertiary,
                modifier = Modifier.width(430.dp),
            )
        }

        StatusPill(status)

        if (status.isDefault) {
            // Домашнім зробили через ADB телевізора — і повернути можна однією кнопкою.
            if (repository.takeover.isActive) {
                TvRevertButton(repository) { status = repository.status() }
                // Android не дає застосунку нічого повернути, коли його видаляють: стоковий екран
                // лишився б вимкненим, і телевізор — з чорним екраном без HOME.
                Text(
                    text = stringResource(R.string.settings_tv_takeover_uninstall_warning),
                    style = LikhtarType.Body2,
                    color = LikhtarColors.AccentBright,
                    modifier = Modifier.width(470.dp),
                )
            } else if (status.currentPackage != null) {
                // Роль наша, але стоковий лаунчер увімкнений: на Google TV після ввімкнення
                // він перебиває роль за пріоритетом HOME-фільтра (приставка, 08.2026).
                Text(
                    text = stringResource(R.string.settings_tv_takeover_boot_hint),
                    style = LikhtarType.Body2,
                    color = LikhtarColors.TextTertiary,
                    modifier = Modifier.width(470.dp),
                )
                TvTakeoverStep(repository) { status = repository.status() }
            }
            // Домашнім зробили системним вибором — повернути інший лаунчер теж мусить бути
            // можна звідси, а не лише після перехоплення.
            if (!repository.takeover.isActive) {
                ActionButton(
                    text = stringResource(R.string.settings_release_home),
                    onClick = { launchFirstAvailable(context, repository.releaseIntents()) },
                    modifier = Modifier.width(300.dp),
                )
                Text(
                    text = stringResource(R.string.settings_release_home_hint),
                    style = LikhtarType.Body2,
                    color = LikhtarColors.TextTertiary,
                    modifier = Modifier.width(470.dp),
                )
            }
            Text(
                text = stringResource(R.string.settings_onboarding_done_note),
                style = LikhtarType.Body2,
                color = LikhtarColors.TextTertiary,
                modifier = Modifier.width(430.dp),
            )
            onDismiss?.let {
                ActionButton(stringResource(R.string.settings_onboarding_done), it, Modifier.width(200.dp).focusRequester(actionFocus))
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = stringResource(if (attempted) R.string.settings_onboarding_retry else R.string.settings_onboarding_set),
                onClick = {
                    attempted = true
                    launchFirstAvailable(context, repository.requestIntents())
                },
                modifier = Modifier.width(200.dp).focusRequester(actionFocus),
            )
            onDismiss?.let { ActionButton(stringResource(R.string.settings_onboarding_later), it, Modifier.width(150.dp)) }
        }

        if (attempted) {
            HomeFallbackLadder(status, repository) { status = repository.status() }
        } else if (onDismiss != null) {
            Text(
                text = stringResource(R.string.settings_onboarding_back_hint),
                style = LikhtarType.MonoSmall,
                color = LikhtarColors.TextTertiary,
            )
        }
    }
}

/** Статус одним рядком: чи ми домашній екран, і хто ним є зараз. */
@Composable
private fun StatusPill(status: HomeLauncherStatus) {
    val ok = status.isDefault
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(LikhtarDimens.CornerMedium))
            .background(LikhtarColors.Glass)
            .border(1.dp, LikhtarColors.GlassBorder, RoundedCornerShape(LikhtarDimens.CornerMedium))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                .background(if (ok) StatusOkFill else StatusOffFill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (ok) "✓" else "✕",
                style = LikhtarType.MonoSmall,
                color = if (ok) StatusOk else StatusOff,
            )
        }
        Column {
            Text(
                text = when {
                    ok -> stringResource(R.string.settings_onboarding_status_ours)
                    status.currentLabel != null -> stringResource(R.string.settings_onboarding_status_other, status.currentLabel.orEmpty())
                    else -> stringResource(R.string.settings_onboarding_status_none)
                },
                style = LikhtarType.Label,
                color = LikhtarColors.TextPrimary,
            )
            // Пакет — лише того, хто тримає HOME замість нас. Коли HOME наш, під «Likhtar Home —
            // домашній екран» стояло `…tv.launcherx`, і читалось, ніби домашній — Google TV.
            status.currentPackage?.takeUnless { ok }?.let {
                Text(text = it, style = LikhtarType.MonoSmall, color = LikhtarColors.TextTertiary)
            }
        }
    }
}

/**
 * Драбина варіантів, коли система не дала замінити лаунчер: спершу те, що робиться з
 * дивана, і лише потім — те, для чого потрібен комп'ютер.
 *
 * ADB-команд тут немає навмисно. Команда, набрана з екрана телевізора, — найкоротший шлях
 * вимкнути не той пакет; кроки живуть на зовнішній сторінці, куди веде QR.
 */
@Composable
private fun HomeFallbackLadder(status: HomeLauncherStatus, repository: HomeLauncherRepository, onChanged: () -> Unit) {
    val context = LocalContext.current
    val explanation = stringResource(
        when (status.blocker) {
            HomeBlocker.GOOGLE_TV -> R.string.settings_onboarding_blocker_gtv
            HomeBlocker.AOSP -> R.string.settings_onboarding_blocker_aosp
            HomeBlocker.UNKNOWN -> R.string.settings_onboarding_blocker_unknown
        },
    )

    Column(
        modifier = Modifier.width(470.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "▲", style = LikhtarType.MonoSmall, color = Warn)
            Text(
                text = stringResource(R.string.settings_onboarding_restricted),
                style = LikhtarType.Label,
                color = LikhtarColors.TextPrimary,
            )
        }
        Text(text = explanation, style = LikhtarType.Body2, color = LikhtarColors.TextTertiary)

        LadderStep(
            index = "1",
            title = stringResource(R.string.settings_tv_takeover_title),
            audience = stringResource(R.string.settings_tv_takeover_audience),
        ) {
            TvTakeoverStep(repository, onChanged)
        }

        LadderStep(
            index = "2",
            title = stringResource(R.string.settings_onboarding_remapper_title),
            audience = stringResource(R.string.settings_onboarding_remapper_audience),
        ) {
            Text(
                text = stringResource(R.string.settings_onboarding_remapper_text),
                style = LikhtarType.Body2,
                color = LikhtarColors.TextTertiary,
            )
            HomeLauncherRepository.REMAPPERS.forEach { app ->
                RemapperCard(app) { launchFirstAvailable(context, repository.storeIntents(app.packageName)) }
            }
        }

        val helpUrl = repository.helpUrl(status)
        if (helpUrl != null) LadderStep(
            index = "3",
            title = stringResource(R.string.settings_onboarding_computer_title),
            audience = stringResource(R.string.settings_onboarding_computer_audience),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QrCode(content = helpUrl, size = 84.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_onboarding_computer_text),
                        style = LikhtarType.Body2,
                        color = LikhtarColors.TextTertiary,
                    )
                    Text(
                        text = helpUrl,
                        style = LikhtarType.MonoSmall,
                        color = LikhtarColors.AccentBright,
                    )
                }
            }
        }
    }
}

/**
 * Домашній екран прямо з телевізора: ADB самого телевізора ([com.likhtar.home.core.data.HomeTakeover]).
 * Кроки для людини — увімкнути налагодження; далі одна кнопка і «Завжди дозволяти» на екрані.
 */
@Composable
private fun TvTakeoverStep(repository: HomeLauncherRepository, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(TvPhase.IDLE) }
    var problem by remember { mutableStateOf<HomeTakeover.Problem?>(null) }
    Text(text = stringResource(R.string.settings_tv_takeover_steps), style = LikhtarType.Body2, color = LikhtarColors.TextTertiary)
    // Як потім видаляти — до згоди, а не після: стоковий екран вимикається, і видалений без
    // «Повернути» Ліхтар лишає телевізор із чорним екраном.
    Text(text = stringResource(R.string.settings_tv_takeover_before_uninstall), style = LikhtarType.Label, color = LikhtarColors.AccentBright)
    ActionButton(
        text = stringResource(if (phase == TvPhase.IDLE) R.string.settings_tv_takeover_button else R.string.settings_tv_takeover_working),
        onClick = {
            if (phase != TvPhase.IDLE) return@ActionButton
            phase = TvPhase.WORKING
            problem = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    repository.takeover.takeOver(onApproval = { phase = TvPhase.APPROVAL })
                }
                problem = result
                phase = TvPhase.IDLE
                onChanged()
            }
        },
        modifier = Modifier.width(220.dp),
    )
    val note = when {
        phase == TvPhase.APPROVAL -> stringResource(R.string.settings_tv_takeover_approve)
        problem != null -> stringResource(problemText(problem!!))
        else -> null
    }
    note?.let { Text(text = it, style = LikhtarType.Label, color = if (problem != null) Warn else LikhtarColors.AccentBright) }
}

/** «Повернути як було»: стоковий лаунчер, його домашній екран, майстер налаштування. */
@Composable
private fun TvRevertButton(repository: HomeLauncherRepository, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<HomeTakeover.Problem?>(null) }
    ActionButton(
        text = stringResource(if (busy) R.string.settings_tv_takeover_working else R.string.settings_tv_takeover_revert),
        onClick = {
            if (busy) return@ActionButton
            busy = true
            scope.launch {
                problem = withContext(Dispatchers.IO) { repository.takeover.revert() }
                busy = false
                onChanged()
            }
        },
        modifier = Modifier.width(300.dp),
    )
    problem?.let { Text(text = stringResource(problemText(it)), style = LikhtarType.Label, color = Warn) }
}

private enum class TvPhase { IDLE, WORKING, APPROVAL }

private fun problemText(problem: HomeTakeover.Problem): Int = when (problem) {
    HomeTakeover.Problem.DEBUGGING_OFF -> R.string.settings_tv_takeover_debugging_off
    HomeTakeover.Problem.PORT_CLOSED -> R.string.settings_tv_takeover_port_closed
    HomeTakeover.Problem.NOT_APPROVED -> R.string.settings_tv_takeover_not_approved
    HomeTakeover.Problem.NO_RESPONSE -> R.string.settings_tv_takeover_no_response
    HomeTakeover.Problem.WIRELESS_TLS -> R.string.settings_tv_takeover_wireless
    HomeTakeover.Problem.WIFI_OWNED -> R.string.settings_tv_takeover_wifi_owned
    HomeTakeover.Problem.WIFI_UNKNOWN -> R.string.settings_tv_takeover_wifi_unknown
    HomeTakeover.Problem.REFUSED -> R.string.settings_tv_takeover_refused
    HomeTakeover.Problem.OTHER -> R.string.settings_tv_takeover_failed
}

/** Сходинка драбини: номер, назва, кому підходить — і вміст під ними. */
@Composable
private fun LadderStep(
    index: String,
    title: String,
    audience: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LikhtarDimens.CornerMedium))
            .background(LikhtarColors.Glass)
            .border(1.dp, LikhtarColors.GlassBorder, RoundedCornerShape(LikhtarDimens.CornerMedium))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                    .background(LikhtarColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = index, style = LikhtarType.MonoSmall, color = LikhtarColors.OnAccent)
            }
            Text(text = title, style = LikhtarType.Label, color = LikhtarColors.TextPrimary)
        }
        Text(text = audience, style = LikhtarType.MonoSmall, color = LikhtarColors.TextTertiary)
        content()
    }
}

@Composable
private fun RemapperCard(app: RemapperApp, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LikhtarDimens.CornerMedium),
        background = LikhtarColors.SurfaceAlt,
        focusedBackground = LikhtarColors.GlassActive,
        borderColor = LikhtarColors.GlassBorder,
        focusedScale = 1f,
        instantFill = true,
    ) { focused ->
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = app.title,
                style = LikhtarType.Label,
                color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextPrimary,
            )
            Text(
                text = if (app.noteRes != 0) stringResource(app.noteRes) else app.note,
                style = LikhtarType.Body2,
                color = if (focused) LikhtarColors.OnGlassActiveMuted else LikhtarColors.TextTertiary,
            )
        }
    }
}


@Composable
private fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    LikhtarButton(label = text, onClick = onClick, modifier = modifier)

/** Перший інтент, який пристрій узагалі вміє відкрити; решта — запасні шляхи. */
internal fun launchFirstAvailable(context: Context, intents: List<Intent>) {
    intents.forEach { intent ->
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Наступний варіант каскаду.
        } catch (_: SecurityException) {
            // Деякі прошивки закривають системні екрани стороннім застосункам.
        }
    }
}

private val StatusOk = LikhtarColors.TileGreen
private val StatusOff = LikhtarColors.DangerSoft
private val StatusOkFill = LikhtarColors.TileGreen.copy(alpha = 0.16f)
private val StatusOffFill = LikhtarColors.DangerSoft.copy(alpha = 0.16f)
private val Warn = LikhtarColors.Warn
