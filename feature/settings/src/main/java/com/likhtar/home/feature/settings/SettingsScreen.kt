package com.likhtar.home.feature.settings

import com.likhtar.home.core.designsystem.PhoneQrDialog
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
import com.likhtar.home.feature.settings.sections.WallpaperSection
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
    val hadTextPrompt = remember { mutableStateOf(false) }
    LaunchedEffect(state.textPrompt) {
        if (state.textPrompt != null) {
            hadTextPrompt.value = true
        } else if (hadTextPrompt.value) {
            hadTextPrompt.value = false
            editedRowFocusRequester.requestWithRetry()
        }
    }

    // Єдиний екран лаунчера з власним суцільним фоном. Решта розділів навмисно
    // прозорі — під ними спільні шпалери. Але тут читають URL-и, токени й назви
    // країн, і читабельність не має залежати від того, яке фото випало в ротації:
    // сіре скло на 54% альфи над світлим кадром з'їдає другорядний текст цілком.
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Прототип: рівний графіт сторінки.
            .background(LikhtarColors.Background),
    ) {
        LikhtarScreenScaffold(
            section = LikhtarSection.SETTINGS,
            onSelectSection = onSelectSection,
            menuFocusRequester = menuFocusRequester,
            contentFocusRequester = firstSectionFocusRequester,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = LikhtarDimens.ScreenPadding, end = LikhtarDimens.ScreenPadding, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                // Обидві колонки прокручуються: розділів десять, а в панелі «Шпалери»
                // або «Телебачення» рядків буває більше двадцяти — без прокрутки все,
                // що не влізло, було просто недосяжне з пульта.
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // .nav h1: 40 px / 600, −.02em.
                    Text(
                        text = stringResource(LikhtarSection.SETTINGS.titleRes),
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.4).sp),
                        color = Color.White,
                        modifier = Modifier.padding(start = 11.dp, bottom = 11.dp),
                    )
                    // Системні налаштування — не розділ LIKHTAR, а вихід із нього; стоять
                    // першими на вимогу замовника.
                    SystemSettingsRow(
                        onClick = { openSystemSettings(context, viewModel) },
                        // ↑ з першого рядка списку веде в меню — тепер це саме цей рядок.
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .focusDirections { up = menuFocusRequester },
                    )
                    // «З телефона» — поруч із системним виходом: довге набирати не пультом.
                    val phoneUrl by viewModel.phoneUrl.collectAsStateWithLifecycle()
                    val phoneOpen by viewModel.phoneOpen.collectAsStateWithLifecycle()
                    SystemSettingsRow(
                        title = stringResource(R.string.settings_phone),
                        subtitle = stringResource(R.string.settings_phone_subtitle),
                        icon = com.likhtar.home.core.designsystem.R.drawable.ic_solar_phone,
                        onClick = viewModel::openPhone,
                        modifier = Modifier.padding(bottom = 9.dp),
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

                    // «Зʼєднання» — для всіх: там медіатека (DLNA/SMB), а її ховало від не-українців.
                    // Початковий фокус — на розділі, з яким відкрили (ТВ Ефір → «ТВ»), інакше на першому.
                    val initialSection = remember { state.section }
                    SettingsSection.entries.forEach { section ->
                        SectionRow(
                            section = section,
                            active = section == state.section,
                            // Розділ перемикається на фокус, а не на OK: інакше ліворуч
                            // підсвічено одне, а праворуч показано інше, і зрозуміти, що
                            // саме зараз редагуєш, неможливо.
                            onFocus = { viewModel.selectSection(section) },
                            onClick = { runCatching { detailFocusRequester.requestFocus() } },
                            modifier = Modifier
                                .padding(bottom = 2.dp)
                                // → веде на ПЕРШИЙ рядок панелі. Без цього платформа шукає
                                // геометрично найближчий вузол і кидала на третій.
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
                                        // Початковий фокус — саме тут, а не на системному
                                        // рядку: перше, на що стає курсор, не має бути
                                        // виходом із лаунчера.
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

private fun openSystemSettings(context: Context, viewModel: SettingsViewModel) {
    val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        viewModel.say(context.getString(R.string.settings_system_missing))
    }
}

/**
 * Розділ ліворуч (.sec прототипу): назва 27 px / 600 і підпис 19 px; фокус — тепла біла
 * пігулка; розділ, у якому працюєш, — тихе тло .08 (вогник зліва прибрано: зайвий).
 */
@Composable
private fun SectionRow(
    section: SettingsSection,
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
            shape = RoundedCornerShape(11.dp),
            background = if (active) LikhtarColors.Paper.copy(alpha = 0.08f) else Color.Transparent,
            focusedBackground = LikhtarColors.Paper,
            borderColor = Color.Transparent,
            focusRingColor = Color.Transparent,
            focusedScale = 1f,
            instantFill = true,
        ) { focused ->
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)) {
                Text(
                    text = stringResource(section.title),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 16.2.sp),
                    color = if (focused) LikhtarColors.Background else Color.White,
                )
                Text(
                    // Без перекладу лише назва продукту.
                    text = section.summary?.let { stringResource(it) } ?: "Home Assistant",
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 9.5.sp, lineHeight = 12.8.sp),
                    color = if (focused) LikhtarColors.InkOnPaper else LikhtarColors.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** «Налаштування Android TV ↗» (.sec.sys): системний вихід — менше й зі значком нового вікна. */
@Composable
private fun SystemSettingsRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.settings_system_title),
    subtitle: String = stringResource(R.string.settings_system_summary),
    icon: Int = com.likhtar.home.core.designsystem.R.drawable.ic_solar_square_top_up,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        background = Color.Transparent,
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusRingColor = Color.Transparent,
        focusedScale = 1f,
        instantFill = true,
    ) { focused ->
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                    color = if (focused) LikhtarColors.Background else Color.White,
                    maxLines = 1,
                )
                androidx.tv.material3.Icon(
                    androidx.compose.ui.res.painterResource(icon),
                    null,
                    tint = if (focused) LikhtarColors.InkOnPaper else LikhtarColors.TextDim,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = subtitle,
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 9.5.sp),
                color = if (focused) LikhtarColors.InkOnPaper else LikhtarColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    // focusGroup + requester на колонці: запит фокуса на групу веде на її ПЕРШИЙ рядок,
    // хоч би скільки їх було в розділі. Тримати requester на самому рядку не вийшло б —
    // склад панелі в кожного розділу свій.
    // ← з панелі повертає в список. focusProperties на самій групі не працює — вихід
    // ліворуч шукається від конкретного рядка, тому напрямок роздається рядкам через
    // LocalDetailExit. Інакше фокус просто зникав: ліворуч від рядка стоїть заголовок
    // розділу, а він не фокусований.
    CompositionLocalProvider(LocalDetailExit provides backFocusRequester) {
    Column(
        modifier = Modifier
            .focusRequester(detailFocusRequester)
            .focusGroup()
            .verticalScroll(rememberScrollState()),
    ) {
        // .pane .head: назва розділу 40 px / 600 і підпис 22 px; далі список.
        Text(
            text = stringResource(state.section.title),
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.4).sp),
            color = Color.White,
        )
        Text(
            text = state.section.summary?.let { stringResource(it) } ?: "Home Assistant",
            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.sp),
            color = LikhtarColors.TextDim,
            modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
        )

        when (state.section) {
            // Шість розділів замість дев'яти: шпалери — частина Головної,
            // Home Assistant — одне з підключень.
            SettingsSection.HOME -> {
                HomeSection(state, viewModel, editedRowFocusRequester)
                WallpaperSection(state, viewModel)
            }
            SettingsSection.WATCH -> WatchSection(state, viewModel)
            SettingsSection.TV -> TvSection(state, viewModel, editedRowFocusRequester)
            SettingsSection.PROFILES -> ProfilesSection(state, viewModel)
            SettingsSection.STARTUP -> StartupSection(state, viewModel)
            SettingsSection.CONNECTIONS -> {
                ConnectionsSection(state, viewModel, editedRowFocusRequester)
                SmartHomeSection(state, viewModel, editedRowFocusRequester)
            }
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
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 11.sp),
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
