package com.likhtar.home.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import com.likhtar.home.core.designsystem.PhoneQrDialog
import com.likhtar.home.core.designsystem.LikhtarIcons
import com.likhtar.home.core.designsystem.clockTime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.likhtar.home.core.data.locale
import com.likhtar.home.core.model.Distribution
import com.likhtar.home.core.designsystem.requestWithRetry
import android.content.ActivityNotFoundException
import com.likhtar.home.core.data.LikhtarSettings
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarScreenScaffold
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.focusDirections
import com.likhtar.home.core.designsystem.PinPadDialog
import com.likhtar.home.core.designsystem.TextInputDialog
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.feature.settings.sections.HomeSection
import com.likhtar.home.feature.settings.sections.BackdropSection
import com.likhtar.home.feature.settings.sections.MediaLibraryGroup
import com.likhtar.home.feature.settings.sections.WatchSection
import com.likhtar.home.feature.settings.sections.TvSection
import com.likhtar.home.feature.settings.sections.ProfilesSection
import com.likhtar.home.feature.settings.sections.StartupSection
import com.likhtar.home.feature.settings.sections.ConnectionsSection
import com.likhtar.home.feature.settings.sections.SmartHomeSection

/**
 * Налаштування: список розділів ліворуч, керування вибраним розділом праворуч,
 * і окрема дія переходу в системні налаштування Android TV.
 */
@Composable
fun SettingsScreen(
    onSelectSection: (LikhtarSection) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Саме на поверненні, а не один раз: після входу ми приходимо сюди з екрана WebView,
    // і LaunchedEffect(Unit) уже не спрацював би — рядок показував би «Не підключено»
    // одразу після вдалого входу.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshMegogo()
        onPauseOrDispose {}
    }
    val menuFocusRequester = rememberInitialFocusRequester(state.pinPrompt != null)
    val firstSectionFocusRequester = rememberInitialFocusRequester(state.pinPrompt == null)
    val context = LocalContext.current

    // Після закриття діалогу фокус має повернутися на рядок, з якого його відкрили: інакше
    // платформа віддає фокус першому вузлу екрана (верхньому меню), і користувач мусить
    // проїхати пультом увесь список назад.
    val editedRowFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }
    // Куди повертає ← з правої панелі: на рядок відкритого розділу, а не на перший у списку.
    val activeSectionFocusRequester = remember { FocusRequester() }
    val focusScope = androidx.compose.runtime.rememberCoroutineScope()
    val hadTextPrompt = remember { mutableStateOf(false) }
    LaunchedEffect(state.textPrompt) {
        if (state.textPrompt != null) {
            hadTextPrompt.value = true
        } else if (hadTextPrompt.value) {
            hadTextPrompt.value = false
            editedRowFocusRequester.requestWithRetry()
        }
    }

    var clock by remember { mutableStateOf(clockTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            clock = clockTime()
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    val nearestBringIntoView = remember { object : BringIntoViewSpec {} }

    @OptIn(ExperimentalFoundationApi::class)
    CompositionLocalProvider(LocalBringIntoViewSpec provides nearestBringIntoView) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LikhtarColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 50.dp, vertical = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LikhtarIcons.Lantern(size = 24.dp)
                    Text(
                        text = "LIKHTAR HOME",
                        style = TextStyle(
                            fontFamily = LikhtarType.Body,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.6.sp,
                        ),
                        color = LikhtarColors.GlassActive,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = clock,
                        style = TextStyle(
                            fontFamily = LikhtarType.Body,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        ),
                        color = LikhtarColors.WarmStone,
                    )
                    HomeBackButton(
                        text = "← " + stringResource(LikhtarSection.HOME.titleRes),
                        onClick = { onSelectSection(LikhtarSection.HOME) },
                        modifier = Modifier
                            .focusRequester(menuFocusRequester)
                            .focusDirections { down = firstSectionFocusRequester },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(38.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(264.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(LikhtarSection.SETTINGS.titleRes),
                        style = TextStyle(
                            fontFamily = LikhtarType.Body,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = (-0.8).sp,
                        ),
                        color = LikhtarColors.GlassActive,
                        modifier = Modifier.padding(start = 10.dp, bottom = 14.dp),
                    )
                    SystemSettingsRow(
                        glyph = "⚙",
                        trailing = "↗",
                        onClick = { openSystemSettings(context, viewModel) },
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .focusDirections { up = menuFocusRequester; right = detailFocusRequester },
                    )
                    val phoneUrl by viewModel.phoneUrl.collectAsStateWithLifecycle()
                    val phoneOpen by viewModel.phoneOpen.collectAsStateWithLifecycle()
                    SystemSettingsRow(
                        title = stringResource(R.string.settings_phone),
                        subtitle = stringResource(R.string.settings_phone_subtitle),
                        glyph = "▣",
                        trailing = "›",
                        onClick = viewModel::openPhone,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .focusDirections { right = detailFocusRequester },
                    )
                    if (phoneOpen) {
                        val status by viewModel.phoneSetup.status.collectAsStateWithLifecycle()
                        PhoneQrDialog(
                            title = stringResource(R.string.settings_phone),
                            url = phoneUrl,
                            lines = listOf(stringResource(R.string.settings_phone_qr1), stringResource(R.string.settings_phone_qr2)),
                            onDismiss = viewModel::closePhone,
                            status = when (val st = status) {
                                PhoneSetupStatus.Waiting -> null
                                PhoneSetupStatus.Opened -> stringResource(R.string.settings_phone_opened)
                                is PhoneSetupStatus.Saved -> stringResource(R.string.settings_phone_saved, st.what.joinToString(", "))
                            },
                            statusOk = status is PhoneSetupStatus.Saved,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(LikhtarColors.GlassDivider),
                    )

                    val initialSection = remember { state.section }
                    SettingsSection.entries.forEach { section ->
                        SectionRow(
                            section = section,
                            active = section == state.section,
                            onFocus = { viewModel.selectSection(section) },
                            onClick = { focusScope.launch { androidx.compose.runtime.withFrameNanos { }; runCatching { detailFocusRequester.requestFocus() } } },
                            modifier = Modifier
                                .padding(bottom = 3.dp)
                                .focusDirections { right = detailFocusRequester }
                                .then(
                                    if (section == state.section) {
                                        Modifier.focusRequester(activeSectionFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(
                                    if (section == initialSection) {
                                        Modifier.focusRequester(firstSectionFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    SectionDetail(
                        state = state,
                        viewModel = viewModel,
                        editedRowFocusRequester = editedRowFocusRequester,
                        detailFocusRequester = detailFocusRequester,
                        backFocusRequester = activeSectionFocusRequester,
                    )
                }
            }
        }

        state.pinPrompt?.let { prompt ->
            PinPadDialog(
                prompt = prompt,
                error = state.pinError,
                onSubmit = viewModel::submitPin,
                onDismiss = viewModel::dismissPin,
            )
        }

        SavedNotice(
            settings = state.settings,
            message = state.message,
            modifier = Modifier.align(Alignment.BottomEnd),
        )

        state.textPrompt?.let { prompt ->
            TextInputDialog(
                title = prompt.title,
                description = prompt.description,
                placeholder = prompt.placeholder,
                initialValue = prompt.initialValue,
                allowClear = prompt.allowClear,
                onSubmit = viewModel::submitTextPrompt,
                onDismiss = viewModel::dismissTextPrompt,
            )
        }
    }
    }
}

private fun openSystemSettings(context: Context, viewModel: SettingsViewModel) {
    val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        viewModel.say(context.getString(R.string.settings_system_missing))
    }
}

@Composable
private fun HomeBackButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val corner = 99.dp
    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .drawWithContent {
                drawContent()
                if (focused) {
                    val strokeWidth = 1.5.dp.toPx()
                    val gap = 1.5.dp.toPx()
                    val outset = gap + strokeWidth / 2f
                    drawRoundRect(
                        color = LikhtarColors.GlassActive,
                        topLeft = Offset(-outset, -outset),
                        size = Size(size.width + 2 * outset, size.height + 2 * outset),
                        cornerRadius = CornerRadius(corner.toPx() + outset),
                        style = Stroke(width = strokeWidth),
                    )
                }
            },
        shape = RoundedCornerShape(corner),
        background = LikhtarColors.SurfaceAlt,
        focusedBackground = LikhtarColors.Border,
        borderColor = Color.White.copy(alpha = 0.11f),
        focusRingColor = Color.Transparent,
        focusedScale = 1f,
        instantFill = true,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = LikhtarType.Body,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
            color = LikhtarColors.GlassActive,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
        )
    }
}

private fun SettingsSection.glyph(): String = when (this) {
    SettingsSection.HOME -> "⌂"
    SettingsSection.BACKDROP -> "◩"
    SettingsSection.WATCH -> "▶"
    SettingsSection.TV -> "▣"
    SettingsSection.MEDIA -> "▤"
    SettingsSection.SMART_HOME -> "◉"
    SettingsSection.PROFILES -> "♙"
    SettingsSection.STARTUP -> "◎"
    SettingsSection.CONNECTIONS -> "⌁"
    SettingsSection.ABOUT -> "ⓘ"
}

/**
 * Розділ ліворуч (.side у likhtar-settings-modern.html): іконка 34×34 у квадраті #49433a,
 * назва й підпис, стрілка › праворуч. Активний — #49433a, у фокусі — #fffaf2 з темним текстом.
 */
@Composable
private fun SectionRow(
    section: SettingsSection,
    active: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SidebarRow(
        title = stringResource(section.title),
        subtitle = section.summary?.let { stringResource(it) } ?: "Home Assistant",
        glyph = section.glyph(),
        trailing = "›",
        active = active,
        onFocus = onFocus,
        onClick = onClick,
        modifier = modifier,
    )
}

/** Спеціальні рядки вгорі сайдбару: «Android TV ↗» та «З телефона ›». */
@Composable
private fun SystemSettingsRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.settings_system_title),
    subtitle: String = stringResource(R.string.settings_system_summary),
    glyph: String = "⚙",
    trailing: String = "↗",
) {
    SidebarRow(
        title = title,
        subtitle = subtitle,
        glyph = glyph,
        trailing = trailing,
        active = false,
        onFocus = {},
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun SidebarRow(
    title: String,
    subtitle: String,
    glyph: String,
    trailing: String,
    active: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocus() },
            shape = RoundedCornerShape(12.dp),
            background = if (active) LikhtarColors.BorderStrong else Color.Transparent,
            focusedBackground = LikhtarColors.GlassActive,
            borderColor = Color.Transparent,
            focusRingColor = Color.Transparent,
            focusedScale = 1f,
            instantFill = true,
        ) { focused ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (focused) LikhtarColors.WarmInkBright else LikhtarColors.BorderStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = glyph,
                        style = TextStyle(
                            fontFamily = LikhtarType.Body,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                        color = if (focused) LikhtarColors.AmberOnPaper else LikhtarColors.LanternAmber,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = TextStyle(
                            fontFamily = LikhtarType.Body,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                        ),
                        color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.GlassActive,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            fontFamily = LikhtarType.Body,
                            fontSize = 11.5.sp,
                            lineHeight = 13.sp,
                        ),
                        color = if (focused) LikhtarColors.InkOnPaper else LikhtarColors.WarmMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = trailing,
                    style = TextStyle(
                        fontFamily = LikhtarType.Body,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
                    color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.GlassActive,
                )
            }
        }
    }
}

@Composable
private fun SectionDetail(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    editedRowFocusRequester: FocusRequester,
    detailFocusRequester: FocusRequester,
    backFocusRequester: FocusRequester,
) {
    CompositionLocalProvider(LocalDetailExit provides backFocusRequester) {
    Column(
        modifier = Modifier
            .focusRequester(detailFocusRequester)
            .focusGroup()
            .verticalScroll(androidx.compose.runtime.key(state.section) { rememberScrollState() })
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(state.section.title),
            style = TextStyle(
                fontFamily = LikhtarType.Body,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = (-1.0).sp,
            ),
            color = LikhtarColors.GlassActive,
        )
        Text(
            text = state.section.summary?.let { stringResource(it) }.orEmpty(),
            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.5.sp),
            color = LikhtarColors.WarmMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        when (state.section) {
            // Розкладка розділів — як була: шпалери, медіатека й пульт — окремими
            // розділами, а не хвостами «Головної», «Перегляду» й «Профілів».
            SettingsSection.HOME -> HomeSection(state, viewModel, editedRowFocusRequester)
            SettingsSection.BACKDROP -> BackdropSection(state, viewModel)
            SettingsSection.WATCH -> WatchSection(state, viewModel)
            SettingsSection.MEDIA -> MediaLibraryGroup(viewModel.media)
            SettingsSection.TV -> TvSection(state, viewModel, editedRowFocusRequester)
            SettingsSection.SMART_HOME -> SmartHomeSection(state, viewModel, editedRowFocusRequester)
            SettingsSection.PROFILES -> ProfilesSection(state, viewModel)
            SettingsSection.STARTUP -> StartupSection(state, viewModel)
            SettingsSection.CONNECTIONS -> ConnectionsSection(state, viewModel, editedRowFocusRequester)
            SettingsSection.ABOUT -> com.likhtar.home.feature.settings.sections.AboutSection(viewModel.appUpdate)
        }

    }
    }
}

/**
 * «Збережено» — там, де людина дивиться, а не дрібним текстом у самому низу панелі, яку
 * ще треба прокрутити. Показується на кожну зміну
 * налаштувань і на кожне повідомлення моделі («Шукаю сервер…», «Знайдено: …»); текст —
 * те, що сказала модель, інакше просто «Збережено».
 */
@Composable
private fun SavedNotice(settings: LikhtarSettings, message: String?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(R.string.settings_saved)
    var last by remember { mutableStateOf(settings) }
    // Перша секунда — налаштування ще доїжджають зі сховища: це не зміна від людини.
    val openedAt = remember { System.currentTimeMillis() }
    LaunchedEffect(settings, message) {
        val changed = settings != last
        last = settings
        if (System.currentTimeMillis() - openedAt < NOTICE_WARMUP_MS) return@LaunchedEffect
        when {
            message != null -> shown = message
            changed -> shown = saved
        }
    }
    LaunchedEffect(shown) {
        if (shown != null) {
            delay(NOTICE_MS)
            shown = null
        }
    }
    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(end = LikhtarDimens.ScreenPadding, bottom = 28.dp),
    ) {
        // .saved: пігулка --deep .92 з тонкою рамкою, галочка бурштином, 22 px / 500.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                .background(LikhtarColors.BackgroundDeep.copy(alpha = 0.92f))
                .border(0.75.dp, LikhtarColors.Faint, RoundedCornerShape(LikhtarDimens.CornerPill))
                .padding(horizontal = 13.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.tv.material3.Icon(
                androidx.compose.ui.res.painterResource(com.likhtar.home.core.designsystem.R.drawable.ic_solar_check_circle),
                null,
                tint = LikhtarColors.Glow,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = shown.orEmpty(),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 11.5.sp),
                color = LikhtarColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }
    }
}

private const val NOTICE_MS = 2_500L
private const val NOTICE_WARMUP_MS = 1_000L
