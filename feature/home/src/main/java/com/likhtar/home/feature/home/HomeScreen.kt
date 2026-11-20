package com.likhtar.home.feature.home
import com.likhtar.home.core.designsystem.BrandColors

import androidx.compose.ui.unit.Dp
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.togetherWith
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.likhtar.home.core.designsystem.PreparingScrim
import com.likhtar.home.core.catalog.ResumeCard
import com.likhtar.home.core.catalog.open
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.designsystem.BakedTextScrim
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.hasBakedText
import com.likhtar.home.core.designsystem.ResumeTitle
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarIcons
import com.likhtar.home.core.designsystem.focusDirections
import com.likhtar.home.core.designsystem.focusDown
import com.likhtar.home.core.designsystem.focusUp
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.LocalAmbientIdle
import com.likhtar.home.core.designsystem.rememberAppArtwork
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import com.likhtar.home.core.designsystem.requestWithRetry
import androidx.compose.runtime.LaunchedEffect
import com.likhtar.home.core.data.RecentChannel
import com.likhtar.home.core.smarthome.SmartDevice
import com.likhtar.home.core.model.LauncherApp
import com.likhtar.home.core.catalog.ContinueItem
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.model.SearchLaunch
import com.likhtar.home.core.model.HourForecast
import com.likhtar.home.core.model.Sky
import com.likhtar.home.core.model.WeatherSnapshot

/**
 * Головна: час, привітання, погода і док розділів — у стилі Apple-скла, тому самому,
 * що на сторінках каталогів.
 *
 * Ліва колонка — провідник, а не окрема «картка в картці»: вона стоїть просто на шпалерах.
 * Контраст дають лише локальні сюжети стрічки, без важкої загальної рамки чи blur.
 *
 * Шпалери та вінетки малює навігація (`WallpaperBackground`, профіль `HOME`) — тут лише вміст.
 */
@Composable
fun HomeScreen(
    onSelectSection: (LikhtarSection) -> Unit,
    onOpenWeather: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTitle: (com.likhtar.home.core.catalog.CatalogItem) -> Unit = {},
    /** Сторінка шоу Реаліті за ext-ключем проєкту. */
    onOpenShow: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    val updateProgress by viewModel.updateProgress.collectAsStateWithLifecycle()
    val updateError by viewModel.updateError.collectAsStateWithLifecycle()
    val updateAwaiting by viewModel.updateAwaitingPermission.collectAsStateWithLifecycle()
    var updateOpen by remember { mutableStateOf(false) }
    update?.takeIf { updateOpen }?.let { release ->
        com.likhtar.home.core.designsystem.UpdateDialog(
            version = release.version,
            notes = release.notes,
            progress = updateProgress,
            error = updateError,
            onUpdate = { viewModel.installUpdate(release) },
            onDismiss = { updateOpen = false; viewModel.clearUpdateError() },
            awaitingPermission = updateAwaiting,
            fallbackUrl = com.likhtar.home.core.data.AppUpdate.SITE,
        )
    }

    // «Продовжити» перечитуємо на кожному поверненні на екран. Раніше цим займався
    // п'ятихвилинний цикл у ViewModel — рівно та затримка, з якою ряд оновлювався після
    // виходу з YouTube чи MEGOGO.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (state.homeShowResume) viewModel.refreshResume() }

    // Поки початковий стан ще збирається, лишаємо старий екран як безпечний fallback.
    // Нова композиція бере ті самі дані й маршрути, що й попередня Головна.
    if (state.tiles.isNotEmpty()) {
        ModernHomeContent(
            state = state,
            onSelectSection = onSelectSection,
            onOpenWeather = onOpenWeather,
            onLaunch = { viewModel.launch(it) },
            onToggleSmartDevice = viewModel::toggleSmartDevice,
            onSetCombinedRows = viewModel::setHomeCombinedRows,
            onSetRowPosition = viewModel::setHomeRowPosition,
            onToggleSectionsFirst = viewModel::toggleHomeSectionsFirst,
            onToggleAppsRowHidden = viewModel::toggleHomeAppsRowHidden,
            onToggleHomeAppHidden = viewModel::toggleHomeAppHidden,
            onUnhideAllHomeApps = viewModel::unhideAllHomeApps,
            onMoveHomeApp = viewModel::moveHomeApp,
            update = update,
            onUpdate = { updateOpen = true },
            inputs = inputs,
            onOpenInput = viewModel::openInput,
            onOpenResume = { card ->
                card.open(
                    onShow = onOpenShow,
                    onTitle = { title ->
                        viewModel.arrivalSeen(card)
                        onOpenTitle(title)
                    },
                    onLaunch = { viewModel.openInApp(it) },
                    onSection = onSelectSection,
                )
            },
            onToggleShowResume = viewModel::toggleHomeShowResume,
            onToggleShowGames = viewModel::toggleHomeShowGames,
            onMoveSection = viewModel::moveSection,
        )
        return
    }

    // Дата живе з годинником, а місто — тільки всередині погодного віджета.
    // Початковий фокус Головної свідомо на погоді: це перше, що користувач бачить,
    // але після будь-якої дії з пульта асинхронне оновлення не має його красти.
    val firstTileFocusRequester = remember { FocusRequester() }
    // Один реквестер, а не два. Раніше на картці погоди висіли `weatherFocusRequester` і
    // окремий «початковий», два `focusRequester` підряд на одному вузлі — і ↑ з доку
    // мовчки гинуло в `runCatching`, лишаючи екран узагалі без фокуса.
    val weatherShown = state.showWeather && state.weather != null
    val weatherFocusRequester = rememberInitialFocusRequester(weatherShown)
    // Без погоди (вимкнена чи ще не приїхала) запит вище гине, і Головна лишалась зовсім без
    // фокуса: стрілкам нема від чого рухатись, «нічого не клацається». Тоді — перша
    // плитка доку.
    LaunchedEffect(weatherShown) { if (!weatherShown) firstTileFocusRequester.requestWithRetry() }
    val shelfFocusRequester = remember { FocusRequester() }
    val contextFocusRequester = remember { FocusRequester() }
    var focusedDockSection by remember { mutableStateOf<LikhtarSection?>(null) }
    var lightAt by remember { mutableStateOf(Offset.Unspecified) }
    var dockHasFocus by remember { mutableStateOf(true) }
    // Чи є в поточному ряду хоч одна картка, яка бере фокус.
    val contextHasCards = when (focusedDockSection) {
        LikhtarSection.HOME -> true
        LikhtarSection.CINEMA -> state.resume.isNotEmpty()
        LikhtarSection.REALITY -> state.realityResume.isNotEmpty()
        LikhtarSection.LIVE_TV -> state.recentChannels.isNotEmpty()
        LikhtarSection.SMART_HOME -> state.smartDevices.isNotEmpty()
        // «Новий пошук» стоїть завжди, навіть коли історія порожня.
        LikhtarSection.SEARCH -> true
        LikhtarSection.APPS -> state.pinnedApps.isNotEmpty()
        LikhtarSection.GAMES -> state.games.isNotEmpty()
        else -> false
    }
    // Кожна картка ряду мусить знати вихід угору й донизу: між зонами пів екрана
    // порожнечі, і геометричний пошук фокуса там глухне.
    // ↓ з полиці — на пункт доку, чия це полиця (він лишається підсвіченим), а не на перший:
    // з «Продовжити перегляд» людина поверталась на LIKHTAR HOME, і полиця під нею мінялась.
    val shelfOwnerFocusRequester = remember { FocusRequester() }
    val contextCross = Modifier.focusUp(weatherFocusRequester).focusDown(shelfOwnerFocusRequester)

    // У ambient-режимі плитки не просто ховаються, а згасають: різке зникнення на великому
    // екрані читається як збій, а не як задум. Годинник і погода лишаються — телевізор
    // частіше стоїть увімкненим як фон, ніж активно використовується.
    val idle = LocalAmbientIdle.current
    val tilesAlpha by animateFloatAsState(
        targetValue = if (idle) 0f else 1f,
        animationSpec = tween(AMBIENT_FADE_MS),
        label = "ambientTiles",
    )

    // OLED Pixel Shift: у режимі очікування годинник щохвилини мікро-зсувається на кілька
    // пікселів, інакше статичні цифри випалюють матрицю.
    val pixelShiftX = if (idle) (((state.time.hashCode() % 5) - 2) * 2).dp else 0.dp
    val pixelShiftY = if (idle) ((((state.time.hashCode() / 5) % 5) - 2) * 2).dp else 0.dp

    // Уночі ambient-годинник пригашений: о третій ранку повна яскравість світить у кімнату.
    val currentHour = remember(state.time) { state.time.substringBefore(':').toIntOrNull() ?: 12 }
    val isNight = currentHour >= 23 || currentHour < 6
    val ambientClockAlpha by animateFloatAsState(
        targetValue = if (idle && isNight) 0.72f else 1f,
        animationSpec = tween(AMBIENT_FADE_MS),
        label = "ambientClockAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()

    ) {
        // Без затінення шпалер: темні смуги згори й знизу та плями під годинником і погодою
        // (прототип) сприймали за поломку телевізора — фото як є.

        // Верхня зона: ліворуч ідентичність і годинник, праворуч віджет погоди.
        // Верхнього меню на Головній немає навмисно — навігація живе в доці внизу.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = HomePadding, end = HomePadding, top = ContentTop),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            ClockBlock(
                time = state.time,
                date = state.date,
                modifier = Modifier
                    .offset(x = pixelShiftX, y = pixelShiftY)
                    // graphicsLayer, а не alpha(): згасання читається у фазі малювання, і
                    // Головна не перекомпоновується на кожен кадр анімації.
                    .graphicsLayer { alpha = ambientClockAlpha },
            )

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.weather?.takeIf { state.showWeather }?.let { weather ->
                    WeatherCard(
                        weather = weather,
                        onOpen = onOpenWeather,
                        modifier = Modifier
                            .offset(x = -pixelShiftX, y = pixelShiftY)
                            .graphicsLayer { alpha = tilesAlpha }
                            .focusRequester(weatherFocusRequester)
                            // Фокус на погоді — ряд над доком ховається: інакше «Ліхтарик» і
                            // «Налаштування» висіли без фокуса на «Ліхтар Хом». Саме тут,
                            // а не «нижня зона втратила фокус»: під час переходу плитка → ряд
                            // зона на мить без фокуса, ряд зникав, і фокус падав на погоду.
                            .onFocusChanged { if (it.isFocused) focusedDockSection = null }
                            // Явний вихід униз — на ПЕРШУ плитку доку. Між погодою й доком
                            // пів екрана порожнечі, і геометричний пошук фокуса кидав туди,
                            // куди ближче по прямій: з правого верхнього кута це виявлявся
                            // останній застосунок, тобто кінець ряду замість його початку.
                            .focusDown(firstTileFocusRequester),
                    )
                }

            }
        }

        // Обидва ряди «Продовжити» ведуть однаково — тим самим шляхом, що й інші екрани.
        val openResume: (ResumeCard) -> Unit = { card ->
            card.open(
                onShow = onOpenShow,
                onTitle = { title ->
                    viewModel.arrivalSeen(card)
                    onOpenTitle(title)
                },
                onLaunch = { viewModel.openInApp(it) },
                onSection = onSelectSection,
            )
        }

        // Світло під тим, що у фокусі — пунктом смуги чи карткою полиці (прототип):
        // одна тепла пляма, що лише їде вбік; у «тиші» лишається приглушеною.
        // Пляма — еліпс 550×450 (як closest-side у прототипі): коло, розтягнуте вшир.
        val light by animateOffsetAsState(
            if (lightAt.isSpecified) lightAt else Offset.Zero, tween(450, easing = LightEase), label = "light",
        )
        if (lightAt.isSpecified) Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(450.dp)
                .graphicsLayer {
                    translationX = light.x - size.width / 2f
                    translationY = light.y - size.height / 2f
                    scaleX = 550f / 450f
                    alpha = 0.45f + 0.55f * tilesAlpha
                }
                .drawBehind {
                    drawOval(
                        brush = Brush.radialGradient(
                            0f to LikhtarColors.Spill.copy(alpha = 0.3f), 0.55f to LikhtarColors.Amber.copy(alpha = 0.1f), 1f to Color.Transparent,
                            center = center, radius = size.minDimension / 2f,
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Screen,
                    )
                },
        )

        // Нижня зона: полиця «Продовжити» над доком розділів.
        CompositionLocalProvider(LocalLight provides { at -> lightAt = at }) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .graphicsLayer { alpha = tilesAlpha }
                .padding(start = HomePadding, end = HomePadding, bottom = DockBottom),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = focusedDockSection,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(tween(220, delayMillis = 60)) + androidx.compose.animation.slideInVertically(tween(220, delayMillis = 60)) { it / 12 })
                        .togetherWith(androidx.compose.animation.fadeOut(tween(120)))
                },
                label = "shelf",
            ) { section ->
            when (section) {
                LikhtarSection.HOME -> HomeContextRow(
                    showTorch = state.showTorch,
                    update = update,
                    onUpdate = { updateOpen = true },
                    onOpenSettings = { onSelectSection(LikhtarSection.SETTINGS) },
                    onOpenTorch = { onSelectSection(LikhtarSection.SEARCH) },
                    onAsk = { query ->
                        SearchLaunch.query = query
                        onSelectSection(LikhtarSection.SEARCH)
                    },
                    inputs = inputs,
                    onOpenInput = viewModel::openInput,
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.CINEMA -> CinemaContextRow(
                    cards = state.resume,
                    onOpen = openResume,
                    onOpenHistory = onOpenHistory,
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.REALITY -> RealityContextRow(
                    cards = state.realityResume,
                    onOpen = openResume,
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.LIVE_TV -> ChannelsContextRow(
                    channels = state.recentChannels,
                    pinned = state.pinnedChannels,
                    onOpen = viewModel::openChannel,
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.SMART_HOME -> SmartHomeContextRow(
                    devices = state.smartDevices,
                    onOpen = { onSelectSection(LikhtarSection.SMART_HOME) },
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.APPS -> AppsContextRow(
                    apps = state.pinnedApps,
                    onLaunch = viewModel::launch,
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                LikhtarSection.GAMES -> AppsContextRow(
                    apps = state.games,
                    onLaunch = viewModel::launch,
                    first = contextFocusRequester,
                    crossZone = contextCross,
                    label = R.string.home_your_games,
                )
                LikhtarSection.SEARCH -> SearchContextRow(
                    queries = state.recentSearches,
                    // Запит із пігулки — одразу шукати; «Новий пошук» — у поле.
                    onSearch = { query ->
                        if (query.isBlank()) SearchLaunch.focusField = true else SearchLaunch.query = query
                        onSelectSection(LikhtarSection.SEARCH)
                    },
                    first = contextFocusRequester,
                    crossZone = contextCross,
                )
                else -> Unit
            }
            }
            Dock(
                tiles = state.tiles,
                onSelectSection = { section ->
                    if (section == LikhtarSection.SEARCH) SearchLaunch.focusField = true
                    onSelectSection(section)
                },
                onFocused = { focusedDockSection = it },
                firstTileFocusRequester = firstTileFocusRequester,
                shelfOwnerFocusRequester = shelfOwnerFocusRequester,
                // ↑ з доку — у власний контекстний ряд. Якщо ряду немає або він
                // порожній, перестрибуємо одразу на погоду: підвисати ні на чому.
                upTarget = if (contextHasCards) contextFocusRequester else weatherFocusRequester,
                selected = focusedDockSection,
                hasFocus = dockHasFocus,
                onFocusInside = { dockHasFocus = it },
            )
        }
        }

        // Підготовка джерела: сервер шукає джерело, яке реально тягне, і це секунди. Відсотків
        // тут немає навмисно — показувати прочитані байти як прогрес фільму нечесно.
        PreparingScrim(state.preparing)

    }
}

private enum class ModernRowKind { APPS, LIKHTAR }

/** Головна 2.0: постійні застосунки й розділи замість контекстної полиці доку. */
@Composable
internal fun ModernHomeContent(
    state: HomeUiState,
    onSelectSection: (LikhtarSection) -> Unit,
    onOpenWeather: () -> Unit,
    onLaunch: (LauncherApp) -> Unit,
    update: com.likhtar.home.core.data.AppRelease?,
    onUpdate: () -> Unit,
    onToggleSmartDevice: (SmartDevice) -> Unit = {},
    onSetCombinedRows: (Boolean) -> Unit = {},
    onSetRowPosition: (com.likhtar.home.core.data.HomeRowPosition) -> Unit = {},
    onToggleSectionsFirst: () -> Unit = {},
    onToggleAppsRowHidden: () -> Unit = {},
    onToggleHomeAppHidden: (String) -> Unit = {},
    onUnhideAllHomeApps: () -> Unit = {},
    onMoveHomeApp: (String, Boolean) -> Unit = { _, _ -> },
    /** Входи HDMI й інші джерела ТВ — у швидкому меню. */
    inputs: List<TvInputTile> = emptyList(),
    onOpenInput: (TvInputTile) -> Unit = {},
    onOpenResume: (ResumeCard) -> Unit = {},
    onToggleShowResume: () -> Unit = {},
    onToggleShowGames: () -> Unit = {},
    /** Переставити розділ Ліхтаря на одну позицію (довге OK на плитці → ← →). */
    onMoveSection: (LikhtarSection, Boolean) -> Unit = { _, _ -> },
) {
    var smartOpen by remember { mutableStateOf(false) }
    var wasSmartOpen by remember { mutableStateOf(false) }
    var gearMenuOpen by remember { mutableStateOf(false) }
    var wasGearMenuOpen by remember { mutableStateOf(false) }
    var desktopOpen by remember { mutableStateOf(false) }
    var wasDesktopOpen by remember { mutableStateOf(false) }
    var rowMenuKind by remember { mutableStateOf<ModernRowKind?>(null) }
    var wasRowMenuKind by remember { mutableStateOf<ModernRowKind?>(null) }
    var movingRowKind by remember { mutableStateOf<ModernRowKind?>(null) }
    var appMenuTargetId by remember { mutableStateOf<String?>(null) }
    var wasAppMenuOpen by remember { mutableStateOf(false) }
    var lastAppMenuIndex by remember { mutableStateOf(0) }
    var movingAppId by remember { mutableStateOf<String?>(null) }
    /** Розділ Ліхтаря, який зараз переставляють (довге OK на плитці). */
    var movingSection by remember { mutableStateOf<LikhtarSection?>(null) }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    val appCardBounds = remember { androidx.compose.runtime.mutableStateMapOf<String, Rect>() }
    val rowHandleBounds = remember { androidx.compose.runtime.mutableStateMapOf<ModernRowKind, Rect>() }
    val firstApp = rememberInitialFocusRequester()
    val weatherButton = remember { FocusRequester() }
    val smartButton = remember { FocusRequester() }
    val settingsButton = remember { FocusRequester() }
    val panelButton = remember { FocusRequester() }
    val gearFirstItem = remember { FocusRequester() }
    val desktopFirstItem = remember { FocusRequester() }
    val rowMenuFirstItem = remember { FocusRequester() }
    val appMenuFirstItem = remember { FocusRequester() }
    val appsRowHandle = remember { FocusRequester() }
    val likhtarRowHandle = remember { FocusRequester() }
    val sections = state.tiles.filterNot {
        it.section == LikhtarSection.HOME ||
            it.section == LikhtarSection.REALITY ||
            it.section == LikhtarSection.SMART_HOME ||
            it.section == LikhtarSection.APPS
    }
    val smartEnabled = state.tiles.any { it.section == LikhtarSection.SMART_HOME }
    val weatherShown = state.showWeather && state.weather != null
    val firstHeader = when {
        weatherShown -> weatherButton
        smartEnabled -> smartButton
        else -> settingsButton
    }
    val showAppsRow = !state.homeAppsRowHidden || state.homeCombinedRows
    val sectionsOnTop = (!showAppsRow || state.homeSectionsFirst) && !state.homeCombinedRows && sections.isNotEmpty()
    val appsRowCount = state.pinnedApps.size + 1
    val topRowCount = when {
        state.homeCombinedRows -> state.pinnedApps.size + sections.size + 1
        sectionsOnTop -> sections.size
        else -> appsRowCount
    }
    val bottomRowCount = when {
        state.homeCombinedRows -> 0
        !showAppsRow -> 0
        sectionsOnTop -> appsRowCount
        else -> sections.size
    }
    val topRowRequesters = remember(topRowCount) {
        List(topRowCount) { index -> if (index == 0) firstApp else FocusRequester() }
    }
    val bottomRowRequesters = remember(bottomRowCount) {
        List(bottomRowCount) { FocusRequester() }
    }
    val appsRowRequesters = if (sectionsOnTop) bottomRowRequesters else topRowRequesters
    // Один ряд: розділи Ліхтаря першими (за замовчуванням), застосунки — після них.
    val combinedSectionsFirst = state.homeCombinedRows && state.homeSectionsFirst
    val appOffset = if (combinedSectionsFirst) sections.size else 0
    /** Вузол фокуса плитки розділу [index] — у тому ряду й на тому місці, де вона стоїть. */
    fun sectionRequester(index: Int): FocusRequester? = when {
        state.homeCombinedRows -> topRowRequesters.getOrNull(if (combinedSectionsFirst) index else state.pinnedApps.size + index)
        sectionsOnTop -> topRowRequesters.getOrNull(index)
        else -> bottomRowRequesters.getOrNull(index)
    }
    val idle = LocalAmbientIdle.current
    val contentAlpha by animateFloatAsState(if (idle) 0f else 1f, tween(AMBIENT_FADE_MS), label = "homeContent")

    val overlayOpen = smartOpen || gearMenuOpen || desktopOpen || rowMenuKind != null || appMenuTargetId != null
    BackHandler(enabled = overlayOpen || movingAppId != null || movingRowKind != null || movingSection != null) {
        when {
            movingSection != null -> movingSection = null
            movingAppId != null -> movingAppId = null
            movingRowKind != null -> movingRowKind = null
            appMenuTargetId != null -> appMenuTargetId = null
            rowMenuKind != null -> rowMenuKind = null
            desktopOpen -> desktopOpen = false
            gearMenuOpen -> gearMenuOpen = false
            smartOpen -> smartOpen = false
        }
    }
    LaunchedEffect(smartOpen) {
        if (smartOpen) {
            wasSmartOpen = true
            androidx.compose.runtime.withFrameNanos { }
        } else if (wasSmartOpen) {
            wasSmartOpen = false
            androidx.compose.runtime.withFrameNanos { }
            smartButton.requestWithRetry()
        }
    }
    LaunchedEffect(gearMenuOpen) {
        if (gearMenuOpen) {
            wasGearMenuOpen = true
            androidx.compose.runtime.withFrameNanos { }
            gearFirstItem.requestWithRetry()
        } else if (wasGearMenuOpen && !desktopOpen) {
            wasGearMenuOpen = false
            androidx.compose.runtime.withFrameNanos { }
            settingsButton.requestWithRetry()
        }
    }
    LaunchedEffect(desktopOpen) {
        if (desktopOpen) {
            wasDesktopOpen = true
            wasGearMenuOpen = false
            androidx.compose.runtime.withFrameNanos { }
            desktopFirstItem.requestWithRetry()
        } else if (wasDesktopOpen) {
            wasDesktopOpen = false
            androidx.compose.runtime.withFrameNanos { }
            settingsButton.requestWithRetry()
        }
    }
    LaunchedEffect(rowMenuKind) {
        if (rowMenuKind != null) {
            wasRowMenuKind = rowMenuKind
            androidx.compose.runtime.withFrameNanos { }
            rowMenuFirstItem.requestWithRetry()
        } else if (wasRowMenuKind != null) {
            val kind = wasRowMenuKind
            wasRowMenuKind = null
            if (movingRowKind == null) {
                androidx.compose.runtime.withFrameNanos { }
                if (kind == ModernRowKind.APPS && showAppsRow) {
                    appsRowHandle.requestWithRetry()
                } else if (kind == ModernRowKind.LIKHTAR && !state.homeCombinedRows) {
                    likhtarRowHandle.requestWithRetry()
                } else {
                    firstApp.requestWithRetry()
                }
            }
        }
    }
    LaunchedEffect(movingRowKind, state.homeSectionsFirst, showAppsRow) {
        val kind = movingRowKind ?: return@LaunchedEffect
        if (!showAppsRow || state.homeCombinedRows) {
            movingRowKind = null
            return@LaunchedEffect
        }
        androidx.compose.runtime.withFrameNanos { }
        val handle = if (kind == ModernRowKind.APPS) appsRowHandle else likhtarRowHandle
        handle.requestWithRetry()
    }
    LaunchedEffect(appMenuTargetId) {
        if (appMenuTargetId != null) {
            wasAppMenuOpen = true
            val idx = state.pinnedApps.indexOfFirst { it.id == appMenuTargetId }
            if (idx >= 0) lastAppMenuIndex = idx
            androidx.compose.runtime.withFrameNanos { }
            appMenuFirstItem.requestWithRetry()
        } else if (wasAppMenuOpen) {
            wasAppMenuOpen = false
            if (movingAppId == null) {
                androidx.compose.runtime.withFrameNanos { }
                val targetRequester = appsRowRequesters.getOrNull((lastAppMenuIndex + appOffset).coerceIn(0, (appsRowRequesters.size - 1).coerceAtLeast(0))) ?: firstApp
                targetRequester.requestWithRetry()
            }
        }
    }
    LaunchedEffect(movingAppId, state.pinnedApps, sectionsOnTop) {
        val targetId = movingAppId ?: return@LaunchedEffect
        val idx = state.pinnedApps.indexOfFirst { it.id == targetId }
        if (idx < 0) {
            movingAppId = null
            return@LaunchedEffect
        }
        androidx.compose.runtime.withFrameNanos { }
        appsRowRequesters.getOrNull(idx + appOffset)?.requestWithRetry()
    }
    // Плитка розділу їде за ← →: фокус іде слідом за нею на нове місце.
    LaunchedEffect(movingSection, sections) {
        val moving = movingSection ?: return@LaunchedEffect
        val idx = sections.indexOfFirst { it.section == moving }
        if (idx < 0) {
            movingSection = null
            return@LaunchedEffect
        }
        androidx.compose.runtime.withFrameNanos { }
        sectionRequester(idx)?.requestWithRetry()
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
            .onPreviewKeyEvent { ev ->
                val activeSection = movingSection
                val activeAppId = movingAppId
                if (activeSection != null) {
                    when (ev.key) {
                        Key.DirectionLeft -> {
                            if (ev.type == KeyEventType.KeyDown) onMoveSection(activeSection, true)
                            true
                        }
                        Key.DirectionRight -> {
                            if (ev.type == KeyEventType.KeyDown) onMoveSection(activeSection, false)
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> true
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape -> {
                            if (ev.type == KeyEventType.KeyDown) movingSection = null
                            true
                        }
                        else -> false
                    }
                } else if (activeAppId != null) {
                    when (ev.key) {
                        Key.DirectionLeft -> {
                            if (ev.type == KeyEventType.KeyDown) onMoveHomeApp(activeAppId, true)
                            true
                        }
                        Key.DirectionRight -> {
                            if (ev.type == KeyEventType.KeyDown) onMoveHomeApp(activeAppId, false)
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> true
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape -> {
                            if (ev.type == KeyEventType.KeyDown) movingAppId = null
                            true
                        }
                        else -> false
                    }
                } else {
                    val activeRow = movingRowKind
                    if (activeRow != null) {
                        val isRowOnTop = if (activeRow == ModernRowKind.APPS) !state.homeSectionsFirst else state.homeSectionsFirst
                        when (ev.key) {
                            Key.DirectionUp -> {
                                if (ev.type == KeyEventType.KeyDown && !isRowOnTop) onToggleSectionsFirst()
                                true
                            }
                            Key.DirectionDown -> {
                                if (ev.type == KeyEventType.KeyDown && isRowOnTop) onToggleSectionsFirst()
                                true
                            }
                            Key.DirectionLeft, Key.DirectionRight -> true
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape -> {
                                if (ev.type == KeyEventType.KeyDown) movingRowKind = null
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
            },
    ) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        val density = LocalDensity.current
        val screenPadding = viewportWidth * 0.052f
        val railGap = (viewportWidth * 0.0135f).coerceIn(12.dp, 24.dp)
        val cardWidth = ((viewportWidth - screenPadding * 2 - railGap * 4) / 5.1f).coerceAtLeast(156.dp)
        val appHeight = (viewportWidth * 0.082f).coerceIn(84.dp, 138.dp)
        val sectionHeight = (viewportWidth * 0.068f).coerceIn(74.dp, 116.dp)
        val titleSize = (viewportWidth.value * 0.015f).coerceIn(16f, 24f)
        // CSS прототипу: затемнення поверх тих самих шпалер, без blur і важких ефектів.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to LikhtarColors.BackgroundDeep.copy(alpha = 0.42f), 0.3f to LikhtarColors.BackgroundDeep.copy(alpha = 0.18f), 1f to LikhtarColors.BackgroundDeep.copy(alpha = 0.87f),
        )))
        CompositionLocalProvider(com.likhtar.home.core.designsystem.LocalFocusBlocked provides overlayOpen) {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = screenPadding, end = screenPadding,
                    top = viewportWidth * 0.037f, bottom = viewportWidth * 0.053f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.time, style = DockLabelStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), color = LikhtarColors.Ink, maxLines = 1)
                        Spacer(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.22f)))
                        state.weather?.takeIf { state.showWeather }?.let { weather ->
                            ModernHomeSurface(
                                onClick = onOpenWeather,
                                modifier = Modifier.focusRequester(weatherButton).focusDown(firstApp).height(42.dp),
                                corner = 100.dp,
                                background = LikhtarColors.PillGlass,
                                focusedBackground = LikhtarColors.BorderStrong,
                                scale = 1.05f, lift = 2.dp,
                            ) { focused ->
                                val iconProgress by animateFloatAsState(if (focused) 1f else 0f, tween(220, easing = LightEase), label = "weatherIcon")
                                Row(Modifier.fillMaxHeight().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LikhtarIcons.Sky(sky = weather.sky, night = weather.hourly.firstOrNull()?.isNight() ?: false,
                                        tint = LikhtarColors.Glow, size = 22.dp, weight = 1.2f,
                                        modifier = Modifier.graphicsLayer { rotationZ = 20f * iconProgress; scaleX = 1f + .12f * iconProgress; scaleY = scaleX })
                                    Text(plainTemperature(weather.temperature), style = DockLabelStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                                        color = LikhtarColors.Ink, maxLines = 1)
                                }
                            }
                        }
                        if (smartEnabled) {
                            ModernHeaderButton(
                                text = stringResource(R.string.home_my_home), icon = R.drawable.ic_dock_smart,
                                onClick = { smartOpen = true }, modifier = Modifier.focusRequester(smartButton).focusDown(firstApp),
                            )
                        }
                        update?.let {
                            HomePill(text = stringResource(R.string.home_update, it.version),
                                icon = com.likhtar.home.core.designsystem.R.drawable.ic_solar_download, onClick = onUpdate,
                                modifier = Modifier.focusDown(firstApp))
                        }
                        ModernHeaderButton(
                            text = null, icon = R.drawable.ic_settings_solar,
                            onClick = { gearMenuOpen = true },
                            modifier = Modifier.focusRequester(settingsButton).focusDown(firstApp),
                        )
                    }
                }
                val reduceMotion = com.likhtar.home.core.designsystem.LocalReduceMotion.current
                var bottomRowFocused by remember { mutableStateOf(false) }
                // Додаткові ряди («Продовжити», «Ігри») — з швидкого меню, під основними.
                var extraRowsFocused by remember { mutableStateOf(false) }
                val hasTwoRows = bottomRowCount > 0
                val targetRowShift = when {
                    // Коли користувач спустився у другий ряд (і швидкі налаштування закриті),
                    // плавно піднімаємо ряди, щоб другий ряд повністю був у безпечній зоні.
                    (hasTwoRows && bottomRowFocused || extraRowsFocused) && !desktopOpen -> 0.dp
                    hasTwoRows -> when (state.homeRowPosition) {
                        com.likhtar.home.core.data.HomeRowPosition.TOP -> 0.dp
                        com.likhtar.home.core.data.HomeRowPosition.MIDDLE -> 36.dp
                        com.likhtar.home.core.data.HomeRowPosition.BOTTOM -> 96.dp
                        com.likhtar.home.core.data.HomeRowPosition.MINIMAL -> 156.dp
                    }
                    else -> when (state.homeRowPosition) {
                        com.likhtar.home.core.data.HomeRowPosition.TOP -> 0.dp
                        com.likhtar.home.core.data.HomeRowPosition.MIDDLE -> 12.dp
                        com.likhtar.home.core.data.HomeRowPosition.BOTTOM -> 22.dp
                        com.likhtar.home.core.data.HomeRowPosition.MINIMAL -> 30.dp
                    }
                }
                val rowShift by androidx.compose.animation.core.animateDpAsState(
                    targetValue = targetRowShift,
                    animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
                    else androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = 220f,
                    ),
                    label = "homeRowShift",
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        // Зсув висоти рядів — ДО прокрутки: усередині неї прокрутка обрізала зсунуті вниз картки
                        // («Один ряд» + «Внизу» різало картки навпіл).
                        .graphicsLayer {
                            alpha = contentAlpha
                            translationY = rowShift.toPx()
                        }
                        // Прокрутка лише коли ввімкнені додаткові ряди («Продовжити», «Ігри»): вона обрізає все
                        // за своїм краєм, і без потреби різала рамку збільшеної картки знизу.
                        .then(
                            if (state.homeShowResume || state.homeShowGames) {
                                Modifier.verticalScroll(rememberScrollState()).padding(bottom = 18.dp)
                            } else {
                                Modifier
                            },
                        ),
                    verticalArrangement = Arrangement.spacedBy((viewportWidth - screenPadding * 2) * 0.026f),
                ) {
                    // Плитки розділів в одному ряду: перед застосунками чи після — за налаштуванням.
                    val emitCombinedSections: (androidx.compose.foundation.lazy.LazyListScope) -> Unit = { scope ->
                        scope.items(sections.size, key = { "section-${sections[it].section.route}" }) { index ->
                            val tile = sections[index]
                            ModernSectionCard(
                                tile = tile,
                                label = if (tile.section == LikhtarSection.SEARCH) stringResource(LikhtarSection.SEARCH.titleRes)
                                    else tile.labelRes?.let { stringResource(it) } ?: tile.label,
                                onClick = { if (movingSection != null) movingSection = null else onSelectSection(tile.section) },
                                onLongClick = { movingAppId = null; movingSection = tile.section },
                                highlighted = movingSection == tile.section,
                                modifier = Modifier
                                    .then(sectionRequester(index)?.let { Modifier.focusRequester(it) } ?: Modifier)
                                    .focusUp(firstHeader),
                                width = cardWidth,
                                height = appHeight,
                                combined = true,
                            )
                        }
                    }
                    val renderAppsSection: @Composable (Boolean) -> Unit = { isTopRow ->
                        val rowRequesters = if (isTopRow) topRowRequesters else bottomRowRequesters
                        val otherRequesters = if (isTopRow) bottomRowRequesters else topRowRequesters
                        val showHandle = !state.homeCombinedRows
                        var firstCardFocused by remember { mutableStateOf(false) }
                        ModernContextSection(
                            label = stringResource(if (state.homeCombinedRows) R.string.home_apps_and_sections else R.string.home_your_apps),
                            gap = railGap,
                            titleSize = titleSize,
                            handleRequester = appsRowHandle.takeIf { showHandle },
                            handleHighlighted = rowMenuKind == ModernRowKind.APPS || movingRowKind == ModernRowKind.APPS,
                            handleMoving = movingRowKind == ModernRowKind.APPS,
                            onHandlePositioned = { rowHandleBounds[ModernRowKind.APPS] = it.translate(-rootOffset.x, -rootOffset.y) },
                            firstCardRequester = rowRequesters.firstOrNull(),
                            firstCardFocused = firstCardFocused,
                            upFromHandle = if (isTopRow) firstHeader else otherRequesters.firstOrNull(),
                            onRowFocusChanged = if (!isTopRow) ({ bottomRowFocused = it }) else null,
                            followIndex = movingAppId?.let { id -> state.pinnedApps.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(appOffset) }
                                ?: movingSection?.takeIf { state.homeCombinedRows }?.let { sec ->
                                    sections.indexOfFirst { it.section == sec }.takeIf { it >= 0 }
                                        ?.let { if (combinedSectionsFirst) it else state.pinnedApps.size + it }
                                },
                            onOpenRowMenu = if (showHandle) ({
                                if (movingRowKind != null) movingRowKind = null else rowMenuKind = ModernRowKind.APPS
                            }) else null,
                        ) {
                            if (combinedSectionsFirst) emitCombinedSections(this)
                            items(state.pinnedApps.size, key = { state.pinnedApps[it].id }) { index ->
                                val app = state.pinnedApps[index]
                                val crossTarget = otherRequesters.getOrNull(index.coerceAtMost(otherRequesters.lastIndex))
                                    ?.takeIf { otherRequesters.isNotEmpty() && !state.homeCombinedRows }
                                val isMovingThis = movingAppId == app.id
                                val isHighlightedThis = isMovingThis || appMenuTargetId == app.id
                                ModernAppCard(
                                    app = app,
                                    onClick = { if (movingAppId != null) movingAppId = null else onLaunch(app) },
                                    onLongClick = {
                                        movingAppId = null
                                        movingRowKind = null
                                        appMenuTargetId = app.id
                                    },
                                    width = cardWidth,
                                    height = appHeight,
                                    highlighted = isHighlightedThis,
                                    isMoving = isMovingThis,
                                    canMoveLeft = index > 0,
                                    canMoveRight = index < state.pinnedApps.lastIndex,
                                    modifier = Modifier
                                        .onGloballyPositioned { coords ->
                                            appCardBounds[app.id] = coords.boundsInRoot().translate(-rootOffset.x, -rootOffset.y)
                                        }
                                        .focusRequester(rowRequesters[appOffset + index])
                                        .then(if (appOffset + index == 0) Modifier.onFocusChanged { firstCardFocused = it.isFocused } else Modifier)
                                        .focusUp(if (isTopRow) firstHeader else crossTarget)
                                        .focusDown(if (isTopRow) crossTarget else null),
                                )
                            }
                            if (state.homeCombinedRows && !combinedSectionsFirst) emitCombinedSections(this)
                            item(key = "all-apps") {
                                val allAppsIndex = rowRequesters.lastIndex
                                val crossTarget = otherRequesters.getOrNull(allAppsIndex.coerceAtMost(otherRequesters.lastIndex))
                                    ?.takeIf { otherRequesters.isNotEmpty() && !state.homeCombinedRows }
                                ModernSectionCard(
                                    tile = HomeTile(LikhtarSection.APPS, 0),
                                    label = stringResource(R.string.home_all_apps),
                                    onClick = { onSelectSection(LikhtarSection.APPS) },
                                    modifier = Modifier
                                        .focusRequester(rowRequesters[allAppsIndex])
                                        .then(if (allAppsIndex == 0) Modifier.onFocusChanged { firstCardFocused = it.isFocused } else Modifier)
                                        .focusUp(if (isTopRow) firstHeader else crossTarget)
                                        .focusDown(if (isTopRow) crossTarget else null),
                                    width = cardWidth,
                                    height = appHeight,
                                    combined = true,
                                )
                            }
                        }
                    }
                    val renderLikhtarSection: @Composable (Boolean) -> Unit = { isTopRow ->
                        val rowRequesters = if (isTopRow) topRowRequesters else bottomRowRequesters
                        val otherRequesters = if (isTopRow) bottomRowRequesters else topRowRequesters
                        var firstCardFocused by remember { mutableStateOf(false) }
                        ModernContextSection(
                            label = stringResource(R.string.home_likhtar_sections),
                            gap = railGap,
                            titleSize = titleSize,
                            showBrandLantern = true,
                            handleRequester = likhtarRowHandle,
                            handleHighlighted = rowMenuKind == ModernRowKind.LIKHTAR || movingRowKind == ModernRowKind.LIKHTAR,
                            handleMoving = movingRowKind == ModernRowKind.LIKHTAR,
                            onHandlePositioned = { rowHandleBounds[ModernRowKind.LIKHTAR] = it.translate(-rootOffset.x, -rootOffset.y) },
                            firstCardRequester = rowRequesters.firstOrNull(),
                            firstCardFocused = firstCardFocused,
                            upFromHandle = if (isTopRow) firstHeader else otherRequesters.firstOrNull(),
                            onRowFocusChanged = if (!isTopRow) ({ bottomRowFocused = it }) else null,
                            onOpenRowMenu = {
                                if (movingRowKind != null) movingRowKind = null else rowMenuKind = ModernRowKind.LIKHTAR
                            },
                            followIndex = movingSection?.let { sec -> sections.indexOfFirst { it.section == sec }.takeIf { it >= 0 } },
                        ) {
                            items(sections.size, key = { sections[it].section.route }) { index ->
                                val tile = sections[index]
                                val crossTarget = otherRequesters.getOrNull(index.coerceAtMost(otherRequesters.lastIndex))
                                    ?.takeIf { otherRequesters.isNotEmpty() }
                                ModernSectionCard(
                                    tile = tile,
                                    label = if (tile.section == LikhtarSection.SEARCH) stringResource(LikhtarSection.SEARCH.titleRes)
                                        else tile.labelRes?.let { stringResource(it) } ?: tile.label,
                                    onClick = { if (movingSection != null) movingSection = null else onSelectSection(tile.section) },
                                    onLongClick = { movingAppId = null; movingSection = tile.section },
                                    highlighted = movingSection == tile.section,
                                    modifier = Modifier
                                        .focusRequester(rowRequesters[index])
                                        .then(if (index == 0) Modifier.onFocusChanged { firstCardFocused = it.isFocused } else Modifier)
                                        .focusUp(if (isTopRow) firstHeader else crossTarget)
                                        .focusDown(if (isTopRow) crossTarget else null),
                                    width = cardWidth,
                                    height = sectionHeight,
                                )
                            }
                        }
                    }
                    if (sectionsOnTop) {
                        renderLikhtarSection(true)
                        if (showAppsRow) renderAppsSection(false)
                    } else {
                        if (showAppsRow) renderAppsSection(true)
                        if (sections.isNotEmpty() && !state.homeCombinedRows) {
                            renderLikhtarSection(!showAppsRow)
                        }
                    }
                    val resume = state.resume
                    val games = state.games
                    // Увімкнений ряд видно й порожнім — з підказкою: інакше перемикач нічого не міняв би.
                    if (state.homeShowResume || (state.homeShowGames && games.isNotEmpty())) {
                        Column(
                            modifier = Modifier.onFocusChanged { extraRowsFocused = it.hasFocus },
                            verticalArrangement = Arrangement.spacedBy((viewportWidth - screenPadding * 2) * 0.026f),
                        ) {
                            if (state.homeShowResume) {
                                ModernContextSection(label = stringResource(R.string.home_continue_watching), gap = railGap, titleSize = titleSize) {
                                    if (resume.isEmpty()) item { Text(stringResource(R.string.home_continue_watching_empty), style = LikhtarType.Body2, color = LikhtarColors.InkFaint) }
                                    items(resume.size, key = { "resume-$it" }) { index ->
                                        ContinueCard(card = resume[index], onClick = { onOpenResume(resume[index]) })
                                    }
                                }
                            }
                            // Ігор нема — ряду теж: підказку в самому низу, без фокуса, не прокрутити до видимого.
                            if (state.homeShowGames && games.isNotEmpty()) {
                                ModernContextSection(label = stringResource(R.string.home_your_games), gap = railGap, titleSize = titleSize) {
                                    items(games.size, key = { "game-${games[it].id}" }) { index ->
                                        ModernAppCard(app = games[index], onClick = { onLaunch(games[index]) }, width = cardWidth, height = appHeight)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (gearMenuOpen) {
            ModernGearDropdown(
                firstItemRequester = gearFirstItem,
                topOffset = viewportWidth * 0.037f + 50.dp,
                endOffset = screenPadding,
                onOpenQuickSettings = {
                    gearMenuOpen = false
                    desktopOpen = true
                },
                onOpenAllSettings = {
                    gearMenuOpen = false
                    onSelectSection(LikhtarSection.SETTINGS)
                },
            )
        }
        if (desktopOpen) {
            ModernDesktopSettingsDrawer(
                state = state,
                firstItemRequester = desktopFirstItem,
                onSetCombinedRows = onSetCombinedRows,
                onSetRowPosition = onSetRowPosition,
                onOpenAllSettings = {
                    desktopOpen = false
                    onSelectSection(LikhtarSection.SETTINGS)
                },
                onClose = { desktopOpen = false },
                inputs = inputs,
                onOpenInput = { desktopOpen = false; onOpenInput(it) },
                onToggleShowResume = onToggleShowResume,
                onToggleShowGames = onToggleShowGames,
            )
        }
        rowMenuKind?.let { kind ->
            val rect = rowHandleBounds[kind]
            val rawLeft = if (rect != null) with(density) { rect.left.toDp() } else screenPadding
            val rawTop = if (rect != null) with(density) { rect.bottom.toDp() } + 6.dp else (viewportHeight * 0.45f)
            ModernRowDropdown(
                kind = kind,
                state = state,
                firstItemRequester = rowMenuFirstItem,
                startOffset = rawLeft.coerceIn(screenPadding, (viewportWidth - 236.dp).coerceAtLeast(screenPadding)),
                topOffset = rawTop.coerceIn(16.dp, (viewportHeight - 164.dp).coerceAtLeast(16.dp)),
                onStartMoveRow = {
                    rowMenuKind = null
                    movingRowKind = kind
                },
                onToggleAppsRowHidden = {
                    rowMenuKind = null
                    onToggleAppsRowHidden()
                },
                onUnhideAllApps = {
                    rowMenuKind = null
                    onUnhideAllHomeApps()
                },
            )
        }
        appMenuTargetId?.let { targetId ->
            val appIndex = state.pinnedApps.indexOfFirst { it.id == targetId }
            val targetApp = state.pinnedApps.getOrNull(appIndex)
            if (targetApp == null) {
                appMenuTargetId = null
            } else {
                val rect = appCardBounds[targetId]
                val rawLeft = if (rect != null) with(density) { rect.left.toDp() } else screenPadding
                // Під карткою, а коли там нема місця (ряд унизу екрана) — над нею: інакше меню лягало
                // на саму картку, і не було видно, що саме переставляють.
                val menuHeight = 100.dp
                val rawTop = if (rect != null) with(density) {
                    val below = rect.bottom.toDp() + 6.dp
                    if (below + menuHeight <= viewportHeight - 8.dp) below else rect.top.toDp() - menuHeight - 8.dp
                } else (viewportHeight * 0.45f)
                ModernAppDropdown(
                    firstItemRequester = appMenuFirstItem,
                    startOffset = rawLeft.coerceIn(screenPadding, (viewportWidth - 200.dp).coerceAtLeast(screenPadding)),
                    topOffset = rawTop.coerceIn(16.dp, (viewportHeight - 114.dp).coerceAtLeast(16.dp)),
                    onStartMove = {
                        appMenuTargetId = null
                        movingAppId = targetApp.id
                    },
                    onHide = {
                        appMenuTargetId = null
                        onToggleHomeAppHidden(targetApp.id)
                    },
                )
            }
        }
        if (smartOpen) {
            ModernSmartHomeDrawer(
                devices = state.smartDevices,
                panelButton = panelButton,
                onToggleDevice = onToggleSmartDevice,
                onOpenAllDevices = {
                    smartOpen = false
                    onSelectSection(LikhtarSection.SMART_HOME)
                },
                onClose = { smartOpen = false },
            )
        }
        PreparingScrim(state.preparing)
    }
}

@Composable
private fun BoxScope.ModernGearDropdown(
    firstItemRequester: FocusRequester,
    topOffset: Dp,
    endOffset: Dp,
    onOpenQuickSettings: () -> Unit,
    onOpenAllSettings: () -> Unit,
) {
    val secondItemRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = topOffset, end = endOffset)
            .width(252.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LikhtarColors.Surface)
            .border(1.dp, LikhtarColors.GlassBorderStrong, RoundedCornerShape(14.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModernHomeSurface(
            onClick = onOpenQuickSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .focusRequester(firstItemRequester)
                .focusDown(secondItemRequester),
            corner = 9.dp,
            background = LikhtarColors.Surface.copy(alpha = 0f),
            focusedBackground = LikhtarColors.BorderStrong,
            scale = 1.02f,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.home_quick_settings),
                    style = DockLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = LikhtarColors.GlassActive,
                )
                Text(
                    "›",
                    style = DockLabelStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    color = LikhtarColors.Glow,
                )
            }
        }
        ModernHomeSurface(
            onClick = onOpenAllSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .focusRequester(secondItemRequester)
                .focusUp(firstItemRequester),
            corner = 9.dp,
            background = LikhtarColors.Surface.copy(alpha = 0f),
            focusedBackground = LikhtarColors.BorderStrong,
            scale = 1.02f,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.home_all_settings),
                    style = DockLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = LikhtarColors.GlassActive,
                )
                Text(
                    "→",
                    style = DockLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = LikhtarColors.WarmMuted,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ModernDesktopSettingsDrawer(
    state: HomeUiState,
    firstItemRequester: FocusRequester,
    onSetCombinedRows: (Boolean) -> Unit,
    onSetRowPosition: (com.likhtar.home.core.data.HomeRowPosition) -> Unit,
    onOpenAllSettings: () -> Unit,
    onClose: () -> Unit,
    inputs: List<TvInputTile> = emptyList(),
    onOpenInput: (TvInputTile) -> Unit = {},
    onToggleShowResume: () -> Unit = {},
    onToggleShowGames: () -> Unit = {},
) {
    val resumeToggleRequester = remember { FocusRequester() }
    val gamesToggleRequester = remember { FocusRequester() }
    val inputRequesters = remember(inputs.size) { List(inputs.size) { FocusRequester() } }
    val closeRequester = remember { FocusRequester() }
    val oneRowRequester = remember { FocusRequester() }
    val rowPositionRequester = remember { FocusRequester() }
    val allSettingsRequester = remember { FocusRequester() }
    val positionLabel = stringResource(
        when (state.homeRowPosition) {
            com.likhtar.home.core.data.HomeRowPosition.TOP -> R.string.home_row_position_top
            com.likhtar.home.core.data.HomeRowPosition.MIDDLE -> R.string.home_row_position_middle
            com.likhtar.home.core.data.HomeRowPosition.BOTTOM -> R.string.home_row_position_bottom
            com.likhtar.home.core.data.HomeRowPosition.MINIMAL -> R.string.home_row_position_minimal
        },
    )
    // М'яке затемнення, щоб позаду панелі було добре видно живе превʼю висоти рядів.
    Box(Modifier.fillMaxSize().background(LikhtarColors.Shade.copy(alpha = 0.32f)))
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(340.dp)
            .background(LikhtarColors.Panel)
            .border(1.dp, LikhtarColors.PillBorder)
            // Пунктів більше, ніж висота екрана: панель прокручується до фокуса.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_quick_settings),
                style = DockLabelStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                color = LikhtarColors.GlassActive,
            )
            ModernHomeSurface(
                onClick = onClose,
                modifier = Modifier
                    .focusRequester(closeRequester)
                    .focusDown(firstItemRequester)
                    .size(40.dp),
                corner = 100.dp,
                background = LikhtarColors.SurfaceHigh,
                focusedBackground = LikhtarColors.BorderStrong,
                scale = 1.05f,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("×", style = DockLabelStyle.copy(fontSize = 21.sp, fontWeight = FontWeight.Medium), color = LikhtarColors.GlassActive)
                }
            }
        }
        Text(
            stringResource(R.string.home_desktop_subtitle),
            style = LikhtarType.Body2.copy(fontSize = 13.5.sp),
            color = LikhtarColors.WarmMuted,
        )

        Text(
            stringResource(R.string.home_rows_group),
            style = DockLabelStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = LikhtarColors.WarmGoldSoft,
            modifier = Modifier.padding(top = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(LikhtarColors.Surface)
                .border(1.dp, LikhtarColors.GlassBorder, RoundedCornerShape(14.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModernHomeSurface(
                onClick = { onSetCombinedRows(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .focusRequester(firstItemRequester)
                    .focusUp(closeRequester)
                    .focusDown(oneRowRequester),
                corner = 10.dp,
                background = if (!state.homeCombinedRows) LikhtarColors.SurfaceHigh else LikhtarColors.Surface.copy(alpha = 0f),
                focusedBackground = LikhtarColors.BorderStrong,
                scale = 1.015f,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.home_layout_two_rows),
                        style = DockLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = LikhtarColors.GlassActive,
                    )
                    if (!state.homeCombinedRows) {
                        Text(
                            "✓",
                            style = DockLabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            color = LikhtarColors.Glow,
                        )
                    }
                }
            }
            ModernHomeSurface(
                onClick = { onSetCombinedRows(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .focusRequester(oneRowRequester)
                    .focusUp(firstItemRequester)
                    .focusDown(rowPositionRequester),
                corner = 10.dp,
                background = if (state.homeCombinedRows) LikhtarColors.SurfaceHigh else LikhtarColors.Surface.copy(alpha = 0f),
                focusedBackground = LikhtarColors.BorderStrong,
                scale = 1.015f,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.home_layout_one_row),
                        style = DockLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = LikhtarColors.GlassActive,
                    )
                    if (state.homeCombinedRows) {
                        Text(
                            "✓",
                            style = DockLabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            color = LikhtarColors.Glow,
                        )
                    }
                }
            }
        }

        Text(
            stringResource(R.string.home_row_position_group),
            style = DockLabelStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = LikhtarColors.WarmGoldSoft,
            modifier = Modifier.padding(top = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(LikhtarColors.Surface)
                .border(1.dp, LikhtarColors.GlassBorder, RoundedCornerShape(14.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModernHomeSurface(
                onClick = { onSetRowPosition(state.homeRowPosition.next()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .focusRequester(rowPositionRequester)
                    .focusUp(oneRowRequester)
                    .focusDown(resumeToggleRequester)
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.DirectionLeft -> {
                                onSetRowPosition(state.homeRowPosition.step(-1))
                                true
                            }
                            Key.DirectionRight -> {
                                onSetRowPosition(state.homeRowPosition.step(+1))
                                true
                            }
                            else -> false
                        }
                    },
                corner = 10.dp,
                background = LikhtarColors.SurfaceHigh,
                focusedBackground = LikhtarColors.BorderStrong,
                scale = 1.015f,
            ) { focused ->
                val canStepLeft = state.homeRowPosition.ordinal > 0
                val canStepRight = state.homeRowPosition.ordinal < com.likhtar.home.core.data.HomeRowPosition.entries.lastIndex
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "‹",
                        style = DockLabelStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = if (canStepLeft) (if (focused) LikhtarColors.Glow else LikhtarColors.GlassActive) else LikhtarColors.BorderMuted,
                    )
                    Text(
                        positionLabel,
                        style = DockLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = LikhtarColors.GlassActive,
                    )
                    Text(
                        "›",
                        style = DockLabelStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = if (canStepRight) (if (focused) LikhtarColors.Glow else LikhtarColors.GlassActive) else LikhtarColors.BorderMuted,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.home_row_position_hint),
                    style = LikhtarType.Body2.copy(fontSize = 11.5.sp),
                    color = LikhtarColors.WarmMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.likhtar.home.core.data.HomeRowPosition.entries.forEach { stop ->
                        val active = stop == state.homeRowPosition
                        Box(
                            modifier = Modifier
                                .size(width = if (active) 14.dp else 5.dp, height = 5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (active) LikhtarColors.Glow else LikhtarColors.BorderMuted),
                        )
                    }
                }
            }
        }

        QuickGroupTitle(stringResource(R.string.home_extra_rows_group))
        QuickGroup {
            QuickSwitchRow(
                text = stringResource(R.string.home_continue_watching),
                on = state.homeShowResume,
                onClick = onToggleShowResume,
                modifier = Modifier.focusRequester(resumeToggleRequester).focusUp(rowPositionRequester).focusDown(gamesToggleRequester),
            )
            QuickSwitchRow(
                text = stringResource(R.string.home_your_games),
                on = state.homeShowGames,
                onClick = onToggleShowGames,
                modifier = Modifier.focusRequester(gamesToggleRequester).focusUp(resumeToggleRequester)
                    .focusDown(inputRequesters.firstOrNull() ?: allSettingsRequester),
            )
        }
        if (inputs.isNotEmpty()) {
            QuickGroupTitle(stringResource(R.string.home_devices))
            QuickGroup {
                inputs.forEachIndexed { index, input ->
                    ModernHomeSurface(
                        onClick = { onOpenInput(input) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .focusRequester(inputRequesters[index])
                            .focusUp(inputRequesters.getOrNull(index - 1) ?: gamesToggleRequester)
                            .focusDown(inputRequesters.getOrNull(index + 1) ?: allSettingsRequester),
                        corner = 10.dp,
                        background = LikhtarColors.Surface.copy(alpha = 0f),
                        focusedBackground = LikhtarColors.BorderStrong,
                        scale = 1.015f,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Image(
                                painter = painterResource(input.icon()),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(LikhtarColors.Glow),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                input.name,
                                style = DockLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                color = LikhtarColors.GlassActive,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (input.port.isNotBlank()) {
                                Text(input.port, style = LikhtarType.Body2, color = LikhtarColors.WarmMuted, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        ModernHomeSurface(
            onClick = onOpenAllSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .focusRequester(allSettingsRequester)
                .focusUp(inputRequesters.lastOrNull() ?: gamesToggleRequester),
            corner = 12.dp,
            background = LikhtarColors.SurfaceHigh,
            focusedBackground = LikhtarColors.BorderStrong,
            scale = 1.03f,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.home_all_settings),
                    style = DockLabelStyle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                    color = LikhtarColors.GlassActive,
                )
                Text(
                    "→",
                    style = DockLabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = LikhtarColors.GlassActive,
                )
            }
        }
    }
}

/** Заголовок групи швидкого меню. */
@Composable
private fun QuickGroupTitle(text: String) {
    Text(
        text,
        style = DockLabelStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = LikhtarColors.WarmGoldSoft,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Рамка групи швидкого меню — та сама, що в «Рядах» і «Висоті». */
@Composable
private fun QuickGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LikhtarColors.Surface)
            .border(1.dp, LikhtarColors.GlassBorder, RoundedCornerShape(14.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** Рядок з перемикачем-пігулкою: OK перемикає. */
@Composable
private fun QuickSwitchRow(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ModernHomeSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(46.dp),
        corner = 10.dp,
        background = LikhtarColors.Surface.copy(alpha = 0f),
        focusedBackground = LikhtarColors.BorderStrong,
        scale = 1.015f,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text,
                style = DockLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = LikhtarColors.GlassActive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            com.likhtar.home.core.designsystem.LikhtarSwitch(on)
        }
    }
}

@Composable
private fun BoxScope.ModernRowDropdown(
    kind: ModernRowKind,
    state: HomeUiState,
    firstItemRequester: FocusRequester,
    startOffset: Dp,
    topOffset: Dp,
    onStartMoveRow: () -> Unit,
    onToggleAppsRowHidden: () -> Unit,
    onUnhideAllApps: () -> Unit,
) {
    val isAppsRow = kind == ModernRowKind.APPS
    val canMoveRow = !state.homeAppsRowHidden
    val actions = buildList<Triple<String, String?, () -> Unit>> {
        if (canMoveRow) {
            add(Triple(stringResource(R.string.home_row_move), "↑↓", onStartMoveRow))
        }
        if (isAppsRow) {
            add(Triple(stringResource(R.string.home_row_hide), null, onToggleAppsRowHidden))
            if (state.homeHiddenApps.isNotEmpty()) {
                add(Triple(stringResource(R.string.home_restore_hidden_apps), null, onUnhideAllApps))
            }
        } else {
            if (state.homeAppsRowHidden) {
                add(Triple(stringResource(R.string.home_row_show_apps), null, onToggleAppsRowHidden))
            }
            if (state.homeHiddenApps.isNotEmpty()) {
                add(Triple(stringResource(R.string.home_restore_hidden_apps), null, onUnhideAllApps))
            }
        }
    }
    if (actions.isEmpty()) return
    val itemRequesters = remember(actions.size) {
        List(actions.size) { index -> if (index == 0) firstItemRequester else FocusRequester() }
    }
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = startOffset, top = topOffset)
            .width(228.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LikhtarColors.Surface)
            .border(1.dp, LikhtarColors.GlassBorderStrong, RoundedCornerShape(14.dp))
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        actions.forEachIndexed { index, (label, badge, action) ->
            ModernHomeSurface(
                onClick = action,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .focusRequester(itemRequesters[index])
                    .then(if (index > 0) Modifier.focusUp(itemRequesters[index - 1]) else Modifier)
                    .then(if (index < itemRequesters.lastIndex) Modifier.focusDown(itemRequesters[index + 1]) else Modifier),
                corner = 9.dp,
                background = LikhtarColors.Surface.copy(alpha = 0f),
                focusedBackground = LikhtarColors.BorderStrong,
                scale = 1.02f,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        label,
                        style = DockLabelStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                        color = LikhtarColors.GlassActive,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (badge != null) {
                        Text(
                            badge,
                            style = DockLabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            color = LikhtarColors.Glow,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ModernAppDropdown(
    firstItemRequester: FocusRequester,
    startOffset: Dp,
    topOffset: Dp,
    onStartMove: () -> Unit,
    onHide: () -> Unit,
) {
    val secondItemRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = startOffset, top = topOffset)
            .width(192.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LikhtarColors.Surface)
            .border(1.dp, LikhtarColors.GlassBorderStrong, RoundedCornerShape(14.dp))
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModernHomeSurface(
            onClick = onStartMove,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .focusRequester(firstItemRequester)
                .focusDown(secondItemRequester),
            corner = 9.dp,
            background = LikhtarColors.Surface.copy(alpha = 0f),
            focusedBackground = LikhtarColors.BorderStrong,
            scale = 1.02f,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.home_app_move),
                    style = DockLabelStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                    color = LikhtarColors.GlassActive,
                )
                Text(
                    "‹ ›",
                    style = DockLabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = LikhtarColors.Glow,
                )
            }
        }
        ModernHomeSurface(
            onClick = onHide,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .focusRequester(secondItemRequester)
                .focusUp(firstItemRequester),
            corner = 9.dp,
            background = LikhtarColors.Surface.copy(alpha = 0f),
            focusedBackground = LikhtarColors.BorderStrong,
            scale = 1.02f,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.home_app_hide),
                    style = DockLabelStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                    color = LikhtarColors.GlassActive,
                )
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.ModernSmartHomeDrawer(
    devices: List<SmartDevice>,
    panelButton: FocusRequester,
    onToggleDevice: (SmartDevice) -> Unit,
    onOpenAllDevices: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    val closeRequester = remember { FocusRequester() }
    val tempDevice = devices.firstOrNull {
        it.kind == com.likhtar.home.core.smarthome.SmartDeviceKind.CLIMATE ||
            it.unit?.contains("°") == true ||
            it.unit?.contains("C", ignoreCase = true) == true
    }
    val listDevices = devices.filter { it != tempDevice }
    val firstDeviceRequester = remember { FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = onClose)
    // Фокус ставить сама панель (на першу річ, якою можна керувати): вона одна знає, що в ній є.
    LaunchedEffect(Unit) {
        (if (listDevices.isNotEmpty()) firstDeviceRequester else if (onOpenAllDevices != null) panelButton else closeRequester).requestWithRetry()
    }
    Box(Modifier.fillMaxSize().background(LikhtarColors.Shade.copy(alpha = 0.66f)))
    Column(
        modifier = Modifier
            // Фокус не виходить з панелі на картки позаду: закривають її Back або «×».
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(360.dp)
            .background(LikhtarColors.Panel)
            .border(1.dp, LikhtarColors.PillBorder)
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_my_home),
                style = DockLabelStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
                color = LikhtarColors.GlassActive,
            )
            ModernHomeSurface(
                onClick = onClose,
                modifier = Modifier
                    .focusRequester(closeRequester)
                    .focusDown(if (listDevices.isNotEmpty()) firstDeviceRequester else if (onOpenAllDevices != null) panelButton else null)
                    .size(42.dp),
                corner = 100.dp,
                background = LikhtarColors.SurfaceHigh,
                focusedBackground = LikhtarColors.BorderStrong,
                scale = 1.05f,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("×", style = DockLabelStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium), color = LikhtarColors.GlassActive)
                }
            }
        }
        Text(
            stringResource(R.string.home_smart_subtitle),
            style = LikhtarType.Body2.copy(fontSize = 14.sp),
            color = LikhtarColors.WarmMuted,
        )
        if (tempDevice != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LikhtarColors.Surface)
                    .border(1.dp, LikhtarColors.GlassBorder, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.home_smart_temp_title),
                    style = DockLabelStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                    color = LikhtarColors.WarmStone,
                )
                Text(
                    tempDevice.readableState ?: tempDevice.state,
                    style = DockLabelStyle.copy(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
                    color = LikhtarColors.GlassActive,
                )
                Text(
                    tempDevice.room ?: tempDevice.name,
                    style = LikhtarType.Body2.copy(fontSize = 14.sp),
                    color = LikhtarColors.WarmMuted,
                )
            }
        }
        if (devices.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LikhtarColors.Surface)
                    .border(1.dp, LikhtarColors.GlassBorder, RoundedCornerShape(16.dp))
                    .padding(18.dp),
            ) {
                Text(stringResource(R.string.home_smart_empty), style = LikhtarType.Body2, color = LikhtarColors.WarmMuted)
            }
        } else if (listDevices.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LikhtarColors.Surface)
                    .border(1.dp, LikhtarColors.GlassBorder, RoundedCornerShape(16.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listDevices.forEachIndexed { index, device ->
                    ModernHomeSurface(
                        onClick = { if (device.isControllable) onToggleDevice(device) else onOpenAllDevices?.invoke() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .thenFirst(index, firstDeviceRequester)
                            .then(if (index == 0) Modifier.focusUp(closeRequester) else Modifier)
                            .then(if (index == listDevices.lastIndex && onOpenAllDevices != null) Modifier.focusDown(panelButton) else Modifier),
                        corner = 12.dp,
                        background = LikhtarColors.Surface.copy(alpha = 0f),
                        focusedBackground = LikhtarColors.SurfaceHigh,
                        scale = 1.02f,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_dock_smart),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(LikhtarColors.Glow),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                device.name,
                                style = DockLabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                color = LikhtarColors.GlassActive,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (device.isControllable) {
                                com.likhtar.home.core.designsystem.LikhtarSwitch(device.isOn)
                            } else {
                                Text(
                                    deviceStateLabel(device),
                                    style = LikhtarType.Body2,
                                    color = LikhtarColors.WarmMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (onOpenAllDevices != null) {
            ModernHomeSurface(
                onClick = onOpenAllDevices,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .focusRequester(panelButton)
                    .focusUp(if (listDevices.isNotEmpty()) firstDeviceRequester else closeRequester),
                corner = 12.dp,
                background = LikhtarColors.SurfaceHigh,
                focusedBackground = LikhtarColors.BorderStrong,
                scale = 1.03f,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.home_all_devices),
                        style = DockLabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        color = LikhtarColors.GlassActive,
                    )
                    Text(
                        "→",
                        style = DockLabelStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        color = LikhtarColors.GlassActive,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernContextSection(
    label: String,
    gap: Dp,
    titleSize: Float,
    showBrandLantern: Boolean = false,
    handleRequester: FocusRequester? = null,
    handleHighlighted: Boolean = false,
    handleMoving: Boolean = false,
    onHandlePositioned: ((Rect) -> Unit)? = null,
    firstCardRequester: FocusRequester? = null,
    firstCardFocused: Boolean = false,
    upFromHandle: FocusRequester? = null,
    onRowFocusChanged: ((Boolean) -> Unit)? = null,
    onOpenRowMenu: (() -> Unit)? = null,
    /** Картку переставляють: ряд прокручується слідом за нею, навіть за край екрана. */
    followIndex: Int? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val railState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(followIndex) {
        val target = followIndex ?: return@LaunchedEffect
        val visible = railState.layoutInfo.visibleItemsInfo
        val fullyShown = visible.firstOrNull { it.index == target }?.let { item ->
            item.offset >= railState.layoutInfo.viewportStartOffset &&
                item.offset + item.size <= railState.layoutInfo.viewportEndOffset
        } == true
        // Лише коли картка не вся на екрані — без смикання ряду на кожен крок.
        if (!fullyShown) railState.scrollToItem((target - 1).coerceAtLeast(0))
    }
    Column(
        modifier = Modifier
            .then(if (onRowFocusChanged != null) Modifier.onFocusChanged { onRowFocusChanged(it.hasFocus) } else Modifier)
            .onPreviewKeyEvent { ev ->
                if (firstCardFocused && handleRequester != null && ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                    runCatching { handleRequester.requestFocus() }.isSuccess
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onOpenRowMenu != null && handleRequester != null) {
                ModernHomeSurface(
                    onClick = onOpenRowMenu,
                    onLongClick = onOpenRowMenu,
                    modifier = Modifier
                        .size(width = if (handleMoving) 36.dp else 28.dp, height = 28.dp)
                        .then(if (onHandlePositioned != null) Modifier.onGloballyPositioned { onHandlePositioned(it.boundsInRoot()) } else Modifier)
                        .focusRequester(handleRequester)
                        .focusUp(upFromHandle)
                        .focusDown(firstCardRequester)
                        .onKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionRight) {
                                firstCardRequester?.let { runCatching { it.requestFocus() }.isSuccess } ?: false
                            } else {
                                false
                            }
                        },
                    corner = 8.dp,
                    background = LikhtarColors.PillGlass,
                    focusedBackground = LikhtarColors.BorderStrong,
                    scale = 1.06f,
                    highlighted = handleHighlighted,
                ) { focused ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (handleMoving) "↑↓" else "≡",
                            style = DockLabelStyle.copy(fontSize = if (handleMoving) 12.sp else 14.sp, fontWeight = FontWeight.Bold),
                            color = if (focused || handleHighlighted) LikhtarColors.Glow else LikhtarColors.WarmMuted,
                        )
                    }
                }
            }
            if (showBrandLantern) {
                LikhtarIcons.Lantern(size = 24.dp)
            }
            Text(
                label,
                style = DockLabelStyle.copy(
                    fontSize = titleSize.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = if (showBrandLantern) 0.8.sp else (-.02).sp,
                ),
                color = if (handleMoving) LikhtarColors.Glow else LikhtarColors.GlassActive,
            )
        }
        com.likhtar.home.core.designsystem.LikhtarRail(state = railState, gap = gap, verticalAlignment = Alignment.CenterVertically, bleed = 16.dp, content = content)
    }
}

@Composable
private fun ModernSectionCard(
    tile: HomeTile,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    width: Dp,
    height: Dp,
    combined: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    /** Плитку зараз переставляють: бурштинове кільце, як у застосунку. */
    highlighted: Boolean = false,
) {
    val all = tile.section == LikhtarSection.APPS
    ModernHomeSurface(
        onClick = onClick, onLongClick = onLongClick, modifier = modifier.size(width, height), corner = 16.dp,
        background = if (all) LikhtarColors.ScrimPanel else LikhtarColors.Surface.copy(alpha = 0.93f),
        scale = 1.055f, lift = if (all || highlighted) 4.dp else 4.dp, pointerParallax = true,
        highlighted = highlighted,
    ) { focused ->
        val iconScale by animateFloatAsState(if (focused) 1.14f else 1f, tween(220, easing = LightEase), label = "sectionIcon")
        val icon: @Composable () -> Unit = {
            Box(Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }) { DockGlyph(tile, focused = false, size = 26.dp) }
        }
        val text: @Composable () -> Unit = {
            Text(label, style = DockLabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = LikhtarColors.GlassActive, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (combined && !all) {
            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) { icon(); text() }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (all) Arrangement.spacedBy(11.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(13.dp)) { icon(); text() }
        }
    }
}

@Composable
private fun ModernHeaderButton(text: String?, icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ModernHomeSurface(
        onClick = onClick,
        modifier = modifier.height(42.dp).then(if (text == null) Modifier.width(42.dp) else Modifier),
        corner = 100.dp, background = LikhtarColors.PillGlass, focusedBackground = LikhtarColors.BorderStrong,
        scale = 1.05f, lift = 2.dp,
    ) { focused ->
        val iconProgress by animateFloatAsState(if (focused) 1f else 0f, tween(220, easing = LightEase), label = "headerIcon")
        Row(Modifier.fillMaxHeight().then(if (text == null) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .padding(horizontal = if (text == null) 0.dp else 15.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Image(painterResource(icon), contentDescription = text ?: stringResource(R.string.home_settings),
                colorFilter = ColorFilter.tint(LikhtarColors.Glow), modifier = Modifier.size(if (text == null) 20.dp else 19.dp)
                    .graphicsLayer { scaleX = 1f + (if (text == null) .1f else .14f) * iconProgress; scaleY = scaleX; rotationZ = if (text == null) 30f * iconProgress else 0f })
            if (text != null) {
                Spacer(Modifier.width(9.dp))
                Text(text, style = DockLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = LikhtarColors.Ink, maxLines = 1)
            }
        }
    }
}

/** Єдина темна підкладка, як у макеті; іконка береться зі встановленого застосунку. */
@Composable
private fun ModernAppCard(
    app: LauncherApp,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    isMoving: Boolean = false,
    canMoveLeft: Boolean = false,
    canMoveRight: Boolean = false,
) {
    // Оригінальний TV-банер застосунку (банер активності → застосунку), а не наш знак; немає банера —
    // знак сервісу чи іконка з назвою, як було. Спільний кеш — без повторного декодування на кожну появу.
    val artwork = com.likhtar.home.core.designsystem.rememberAppArtwork(app.packageName, app.activityName)
    val icon = artwork?.takeIf { !it.banner }?.image
    ModernHomeSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(width, height),
        corner = 16.dp,
        background = LikhtarColors.ScrimPanel,
        focusedBackground = if (highlighted || isMoving) LikhtarColors.SurfaceHigh else LikhtarColors.ScrimPanel,
        scale = 1.055f,
        lift = if (highlighted || isMoving) 4.dp else 0.dp,
        pointerParallax = true,
        highlighted = highlighted || isMoving,
    ) { focused ->
        val contentScale by animateFloatAsState(if (focused) 1.03f else 1f, tween(200, easing = LightEase), label = "appContent")
        Box(Modifier.fillMaxSize()) {
            if (artwork?.banner == true) {
                Image(
                    bitmap = artwork.image,
                    contentDescription = app.label,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else Row(
                Modifier.fillMaxSize().padding(horizontal = if (isMoving) 22.dp else 12.dp).graphicsLayer { scaleX = contentScale; scaleY = contentScale },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val name = app.label.lowercase()
                when {
                    name.contains("netflix") -> Text("NETFLIX", style = DockLabelStyle.copy(fontWeight = FontWeight.Black, fontSize = 20.sp), color = BrandColors.Netflix, maxLines = 1)
                    name.contains("youtube") -> {
                        Box(Modifier.size(width = 25.dp, height = 18.dp).background(BrandColors.YouTubeRed, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                            Text("▶", style = TextStyle(fontSize = 11.5.sp), color = Color.White)
                        }
                        Spacer(Modifier.width(7.dp))
                        Text("YouTube", style = DockLabelStyle.copy(fontWeight = FontWeight.Black, fontSize = 18.sp), color = Color.White, maxLines = 1)
                    }
                    name.contains("megogo") -> {
                        Box(Modifier.size(18.dp).graphicsLayer { rotationZ = 45f }.background(BrandColors.Megogo, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(10.dp))
                        Text("MEGOGO", style = DockLabelStyle.copy(fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.2.sp), color = Color.White, maxLines = 1)
                    }
                    name.contains("spotify") -> {
                        Box(Modifier.size(24.dp).background(BrandColors.Spotify, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            Text("≋", style = TextStyle(fontWeight = FontWeight.Black, fontSize = 16.sp), color = LikhtarColors.TileFloor)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Spotify", style = DockLabelStyle.copy(fontWeight = FontWeight.Black, fontSize = 18.sp), color = BrandColors.Spotify, maxLines = 1)
                    }
                    name.contains("sweet") -> Text("sweet.tv", style = DockLabelStyle.copy(fontWeight = FontWeight.Black, fontSize = 18.sp), color = Color.White, maxLines = 1)
                    else -> {
                        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(26.dp)) }
                        if (icon != null) Spacer(Modifier.width(8.dp))
                        Text(app.label, style = DockLabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = LikhtarColors.GlassActive,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (isMoving) {
                Text(
                    "‹",
                    style = DockLabelStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = LikhtarColors.Glow.copy(alpha = if (canMoveLeft) 0.95f else 0.25f),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                )
                Text(
                    "›",
                    style = DockLabelStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = LikhtarColors.Glow.copy(alpha = if (canMoveRight) 0.95f else 0.25f),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                )
            }
        }
    }
}

/**
 * Ідентичність і час — цілісний блок лівої частини верхньої зони.
 *
 * Вордмарк, годинник і дата стоять одним стовпцем за спільною вертикальною лінією: саме
 * вона й робить із трьох рядків один блок, тому окремої підкладки під ними немає.
 * Привітання («Добрий день») пішло — воно повторювало те, що вже каже годинник.
 */
@Composable
private fun ClockBlock(
    time: String,
    date: String,
    modifier: Modifier = Modifier,
) {
    // Без бурштинової лінії збоку (прототип): світло на Головній одне — під фокусом.
    // Дата — рівно під чорнилом першої цифри: у великої цифри свій лівий відступ гліфа,
    // і дата від краю блоку стояла лівіше за годинник.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fonts = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    val inkStart = remember(time.firstOrNull()) {
        val typeface = fonts.resolve(HomeClockStyle.fontFamily, HomeClockStyle.fontWeight ?: FontWeight.Normal).value as? android.graphics.Typeface
        val paint = android.graphics.Paint().apply {
            this.typeface = typeface
            textSize = with(density) { HomeClockStyle.fontSize.toPx() }
            HomeClockStyle.fontFeatureSettings?.let { fontFeatureSettings = it }
        }
        val ink = android.graphics.Rect()
        if (time.isNotEmpty()) paint.getTextBounds(time, 0, 1, ink)
        with(density) { ink.left.coerceAtLeast(0).toDp() }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = time, style = HomeClockStyle, color = LikhtarColors.Ink)
        Text(
            text = date,
            style = LikhtarType.Lead.copy(shadow = TextShade.copy(blurRadius = 10f)),
            color = LikhtarColors.Ink,
            modifier = Modifier.padding(start = inkStart),
        )
    }
}

/**
 * Віджет погоди: місто, температура, символ стану й три найближчі позначки часу.
 *
 * Тексту стану («Ясно», «Мінлива хмарність») тут навмисно немає — його несе символ.
 * Українські формулювання довгі, вони обрізалися рівно на швидкості вітру, тобто на
 * половині сенсу, а з дивана однаково швидше читається піктограма.
 *
 * Картка натискна: OK відкриває детальну погоду. Фокус не інвертує її в біле — на
 * щільному блоці з цифрами це читалося б як інший екран; замість цього світла рамка.
 */
@Composable
private fun WeatherCard(weather: WeatherSnapshot, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    // Погода текстом, без скляної картки (прототип): шпалери видно цілком. Фокусована
    // вона лише як вхід на сторінку погоди — тоді під нею м'яка тепла підкладка.
    FocusableSurface(
        onClick = onOpen,
        modifier = modifier,
        shape = RoundedCornerShape(LikhtarDimens.CornerXLarge),
        background = Color.Transparent,
        focusedBackground = LikhtarColors.Background.copy(alpha = 0.7f),
        borderColor = Color.Transparent,
        focusedScale = LikhtarDimens.PanelFocusScale,
    ) { focused ->
        // Без фокуса підкладки немає — текст на самому фото, тож тінь під ним, як під годинником.
        val shade = if (focused) null else TextShade
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                LikhtarIcons.Sky(
                    sky = weather.hourly.firstOrNull()?.sky ?: Sky.CLEAR,
                    night = weather.hourly.firstOrNull()?.isNight() ?: false,
                    tint = LikhtarColors.Glow,
                    size = 34.dp,
                    weight = 1.2f,
                )
                Text(text = plainTemperature(weather.temperature), style = WeatherTempStyle.copy(shadow = shade), color = LikhtarColors.Ink)
            }
            Text(
                text = weather.city + weather.condition.substringBefore(" · ").takeIf { it.isNotBlank() }?.let { ", " + it.lowercase() }.orEmpty(),
                style = WeatherCityStyle.copy(shadow = shade?.copy(blurRadius = 10f)),
                color = LikhtarColors.InkMuted,
            )
            if (weather.hourly.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                    weather.hourly.take(CARD_HOURS).forEach { HourCell(it, shade?.copy(blurRadius = 10f)) }
                }
            }
        }
    }
}

@Composable
private fun HourCell(hour: HourForecast, shade: androidx.compose.ui.graphics.Shadow?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(text = hour.time, style = HourTimeStyle.copy(shadow = shade), color = LikhtarColors.InkMuted)
        Text(text = plainTemperature(hour.temperature), style = HourTempStyle.copy(shadow = shade), color = LikhtarColors.Ink)
    }
}

/**
 * Ніч рахуємо з самої години прогнозу: у моделі погоди ознаки дня й ночі немає, а без неї
 * о 21:00 у картці світило б сонце.
 */
/**
 * Температура без «плюса»: у макеті стоїть «18°», а не «+18°».
 *
 * Мінус лишається — без нього −5° і 5° стали б однією й тією ж цифрою.
 */
internal fun plainTemperature(raw: String): String = raw.removePrefix("+")

internal fun isNightHour(time: String?): Boolean {
    val hour = time?.substringBefore(':')?.trim()?.toIntOrNull() ?: return false
    return hour >= 21 || hour < 6
}

/**
 * Ніч цієї години: за Open-Meteo (`is_day`), а годинник — лише запасний. Правило «до 6 —
 * ніч» малювало сонце о 06:00, коли схід о 06:40, а влітку — місяць о 21:00.
 */
internal fun HourForecast.isNight(): Boolean = night ?: isNightHour(time)

/**
 * Док розділів — навігація Головної.
 *
 * Свій FocusRequester на кожну плитку, щоб замкнути ряд у кільце: ← з першої веде на
 * останню, → з останньої на першу. Дефолтний пошук фокуса цього не вміє — він просто
 * впирається в край.
 */
@Composable
private fun Dock(
    tiles: List<HomeTile>,
    onSelectSection: (LikhtarSection) -> Unit,
    onFocused: (LikhtarSection) -> Unit,
    firstTileFocusRequester: FocusRequester,
    /** Пункт, чия полиця відкрита, — сюди веде ↓ з полиці. */
    shelfOwnerFocusRequester: FocusRequester,
    upTarget: FocusRequester?,
    modifier: Modifier = Modifier,
    /** Розділ, чия полиця зараз відкрита: фокус пішов на полицю — пункт лишається підсвіченим. */
    selected: LikhtarSection? = null,
    hasFocus: Boolean = true,
    onFocusInside: (Boolean) -> Unit = {},
) {
    val requesters = remember(tiles.size) { List(tiles.size) { FocusRequester() } }
    // З «Іграми» розділів вісім, і на 960 dp українські підписи вже не влазять: поля тісніші.
    val compact = tiles.size > COMPACT_DOCK_FROM

    // Одна смуга-«пігулка», як верхнє меню розділів (прототип): навігація однакова
    // скрізь у лаунчері. Фокус — тепла біла пігулка.
    // Смуга — за вмістом і по центру екрана: розпірка після LIKHTAR HOME відганяла розділи
    // до правого краю, і між ними зяяла дірка.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .height(DockHeight)
            .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
            .background(LikhtarColors.BackgroundDeep.copy(alpha = 0.74f))
            .border(0.75.dp, LikhtarColors.Paper.copy(alpha = 0.08f), RoundedCornerShape(LikhtarDimens.CornerPill))
            .onFocusChanged { onFocusInside(it.hasFocus) }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
    ) {
        tiles.forEachIndexed { index, tile ->
            val previous = requesters[(index - 1 + tiles.size) % tiles.size]
            val next = requesters[(index + 1) % tiles.size]
            NavTile(
                tile = tile,
                onClick = { onSelectSection(tile.section) },
                onFocused = onFocused,
                marked = !hasFocus && tile.section == selected,
                compact = compact,
                modifier = Modifier
                    .focusRequester(requesters[index])
                    .then(if (index == 0) Modifier.focusRequester(firstTileFocusRequester) else Modifier)
                    .then(if (tile.section == selected) Modifier.focusRequester(shelfOwnerFocusRequester) else Modifier)
                    .focusDirections {
                        left = previous
                        right = next
                    }
                    .focusUp(upTarget),
            )
            if (tile.section == LikhtarSection.HOME) Spacer(Modifier.width(DockHomeGap))
        }
    }
}

@Composable
private fun NavTile(
    tile: HomeTile,
    onClick: () -> Unit,
    onFocused: (LikhtarSection) -> Unit,
    modifier: Modifier = Modifier,
    marked: Boolean = false,
    compact: Boolean = false,
) {
    val pad = if (compact) 8.dp else 12.dp
    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .height(DockItemHeight)
            .lightSource()
            .onFocusChanged { if (it.isFocused) onFocused(tile.section) },
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = if (marked) LikhtarColors.Paper.copy(alpha = 0.14f) else Color.Transparent,
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusRingColor = Color.Transparent,
        focusedScale = LikhtarDimens.ControlFocusScale,
        instantFill = true,
    ) { focused ->
        val ink = if (focused) LikhtarColors.OnWarmWhite else LikhtarColors.Ink
        Row(
            // Поля 12, не 15: з «Ліхтариком» замість «Пошуку» в доці «Застосунки» обрізались.
            modifier = Modifier.padding(start = if (tile.section == LikhtarSection.HOME) 8.dp else pad, end = pad).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (tile.section == LikhtarSection.HOME) {
                LikhtarIcons.Lantern(size = BrandIconSize, onLight = focused)
                Text(text = "LIKHTAR HOME", style = DockLabelStyle, color = ink, maxLines = 1)
            } else {
                DockGlyph(tile = tile, focused = focused)
                Text(
                    text = tile.labelRes?.let { stringResource(it) } ?: tile.label,
                    style = DockLabelStyle,
                    color = ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Значок пункту смуги: лінійний набір [LikhtarIcons] бурштином — світлом, як у прототипі;
 * на фокусі темніє, бо стоїть на теплій білій пігулці.
 */
@Composable
private fun DockGlyph(tile: HomeTile, focused: Boolean, size: Dp = DockIconSize) {
    // Заливні іконки Solar бурштином — світлом, як у прототипі; на білій пігулці темніють.
    val icon = when (tile.section) {
        LikhtarSection.CINEMA -> R.drawable.ic_dock_cinema
        LikhtarSection.LIVE_TV -> R.drawable.ic_dock_tv
        LikhtarSection.REALITY -> R.drawable.ic_dock_reality
        LikhtarSection.SMART_HOME -> R.drawable.ic_dock_smart
        LikhtarSection.SEARCH -> R.drawable.ic_dock_search
        LikhtarSection.GAMES -> R.drawable.ic_dock_games
        else -> R.drawable.ic_dock_apps
    }
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(if (focused) LikhtarColors.AmberOnPaper else LikhtarColors.Glow),
        modifier = Modifier.size(size),
    )
}

private val DockHeight = 52.dp
private const val COMPACT_DOCK_FROM = 7

/** Проміжок між LIKHTAR HOME і розділами — трохи більший за проміжок між розділами. */
private val DockHomeGap = 4.dp
private val DockItemHeight = 40.dp
private val DockLabelStyle = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

/**
 * Контекстні ряди доку.
 *
 * Спільний каркас на всі: заголовок над рядом і горизонтальний набір карток. Заголовка
 * немає лише в самої Головної — там картки й так називають себе повністю.
 */
/** Хто у фокусі на Головній, повідомляє сюди свій центр у вікні — під ним лягає світло. */
private val LocalLight = staticCompositionLocalOf<(Offset) -> Unit> { {} }

@Composable
private fun Modifier.lightSource(): Modifier {
    val report = LocalLight.current
    var cx by remember { mutableStateOf(Offset.Unspecified) }
    var focused by remember { mutableStateOf(false) }
    return this
        .onGloballyPositioned {
            cx = it.positionInWindow() + Offset(it.size.width / 2f, it.size.height / 2f)
            if (focused && cx.isSpecified) report(cx) // ряд прокрутився під фокусом — світло їде за карткою
        }
        .onFocusChanged {
            focused = it.hasFocus
            if (it.hasFocus && cx.isSpecified) report(cx)
        }
}

@Composable
private fun ContextSection(
    label: String?,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let {
            Text(text = it, style = ShelfTitleStyle, color = Color.White)
        }
        // Спільна рейка, як у решти розділів: прокрутка (шостий канал не стискається), запас під
        // збільшення картки у фокусі, ряд по колу окремим натисканням, вхід на першу картку.
        // Досі тут був власний Row із саморобними краями й запасом — те саме, але окремо.
        com.likhtar.home.core.designsystem.LikhtarRail(
            gap = 14.dp,
            verticalAlignment = Alignment.CenterVertically,
            bleed = ScrollEdge,
            content = content,
        )
    }
}

/**
 * Контекст самої Головної: Ліхтарик і налаштування. Новин (RSS) більше немає — читати їх
 * на ТБ незручно; стрічку, якщо буде, зробимо не з RSS.
 */
@Suppress("ModifierParameter") // crossZone — модифікатор для дітей (фокус між зонами), не для кореня
@Composable
private fun HomeContextRow(
    showTorch: Boolean,
    update: com.likhtar.home.core.data.AppRelease?,
    onUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTorch: () -> Unit,
    onAsk: (String) -> Unit,
    inputs: List<TvInputTile>,
    onOpenInput: (TvInputTile) -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    // «Пристрої» — над пігулками: ↑ з доку лишається на пігулках, ↓/↑ між двома рядами явні.
    val devicesFirst = remember { FocusRequester() }
    // Column: AnimatedContent кладе вміст у Box, і два ряди лягли б один на одного.
    Column {
    if (inputs.isNotEmpty()) {
        ContextSection(label = stringResource(R.string.home_devices)) {
            items(inputs.size, key = { inputs[it].id }) { index ->
                val input = inputs[index]
                HomePill(
                    text = input.name,
                    icon = input.icon(),
                    small = input.port.ifBlank { null },
                    onClick = { onOpenInput(input) },
                    modifier = crossZone.focusDown(first).thenFirst(index, devicesFirst),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }
    // «Ліхтарик підкаже» (прототип): готові запити одразу запускають Ліхтарика.
    var support by remember { mutableStateOf(false) }
    if (support) com.likhtar.home.core.designsystem.SupportDialog(onDismiss = { support = false })
    // «Новинки українською» — лише українському інтерфейсу: поляк бачив «Nowości po ukraińsku»
    // поруч із власними підказками, як решту українського пласта.
    val ukrainian = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "uk"
    // Три випадкові запити з набору, щоразу інші, коли рядок з'являється.
    val pool = androidx.compose.ui.res.stringArrayResource(R.array.home_asks).toList() +
        listOfNotNull(stringResource(R.string.home_ask_ukrainian).takeIf { ukrainian })
    val asks = remember { pool.shuffled().take(ASKS_SHOWN) }
    // Порядок: сам Ліхтарик, «Підтримати автора», а далі решта — нова версія, підказки й
    // налаштування. «Налаштування» лишаються завжди: вимкнувши Ліхтарик, людина мусить мати чим
    // його повернути.
    val pills: List<Triple<String, Int, () -> Unit>> = buildList {
        if (showTorch) add(Triple(stringResource(R.string.home_open_torch), R.drawable.ic_dock_search, onOpenTorch))
        add(Triple(stringResource(R.string.home_support), com.likhtar.home.core.designsystem.R.drawable.ic_solar_heart, { support = true }))
        update?.let { add(Triple(stringResource(R.string.home_update, it.version), com.likhtar.home.core.designsystem.R.drawable.ic_solar_download, onUpdate)) }
        if (showTorch) asks.forEach { text -> add(Triple(text, R.drawable.ic_magic, { onAsk(text) })) }
        add(Triple(stringResource(R.string.home_settings), R.drawable.ic_settings_solar, onOpenSettings))
    }
    ContextSection(label = if (showTorch) stringResource(R.string.home_torch_suggests) else null) {
        items(pills.size) { index ->
            val (text, icon, onClick) = pills[index]
            HomePill(
                text = text, icon = icon, onClick = onClick,
                modifier = crossZone.focusUp(devicesFirst.takeIf { inputs.isNotEmpty() }).thenFirst(index, first),
            )
        }
    }
    }
}

private fun TvInputTile.icon(): Int = when (kind) {
    TvInputTile.Kind.GAME -> R.drawable.ic_dock_games
    TvInputTile.Kind.PLAYER -> com.likhtar.home.core.designsystem.R.drawable.ic_solar_play
    TvInputTile.Kind.OTHER -> R.drawable.ic_dock_tv
}

/** Пігулка полиці: іконка бурштином і підпис; фокус — тепла біла пігулка. */
@Composable
private fun HomePill(
    text: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Другий рядок дрібно: стан пристрою; [lit] — бурштином (увімкнено). */
    small: String? = null,
    lit: Boolean = false,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.lightSource().height(48.dp),
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = LikhtarColors.Surface.copy(alpha = 0.82f),
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusRingColor = Color.Transparent,
        focusedScale = LikhtarDimens.ControlFocusScale,
        instantFill = true,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 17.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(if (focused) LikhtarColors.AmberMuted else LikhtarColors.Glow),
                modifier = Modifier.size(17.dp),
            )
            Column {
                Text(text, style = DockLabelStyle.copy(fontSize = 13.sp), color = if (focused) LikhtarColors.OnWarmWhite else LikhtarColors.Ink, maxLines = 1)
                small?.let {
                    Text(
                        it,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 11.5.sp),
                        color = when {
                            focused -> LikhtarColors.AmberMuted
                            lit -> LikhtarColors.Glow
                            else -> LikhtarColors.TextDim
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Фокус картки як у прототипі: біле кільце й теплий ореол навколо. Кільце — у тому ж шарі,
 * що росте разом із карткою, ореол — позаду, у фазі малювання; композиції не коштує.
 */
@Composable
private fun Modifier.focusHalo(shape: androidx.compose.ui.graphics.Shape): Modifier {
    // Теплий ореол під карткою у фокусі (прототип: тінь 0 26 60 −16 бурштином). Кільце — у
    // самій картці ([FocusRing]), щоб росло разом із нею.
    var focused by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "halo")
    return this
        .onFocusChanged { focused = it.hasFocus }
        .drawBehind {
            if (glow <= 0f) return@drawBehind
            drawOval(
                brush = Brush.radialGradient(
                    listOf(LikhtarColors.Amber.copy(alpha = 0.5f * glow), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.85f),
                    radius = size.width * 0.62f,
                ),
                topLeft = Offset(-size.width * 0.12f, size.height * 0.2f),
                size = Size(size.width * 1.24f, size.height * 1.15f),
            )
        }
}

/** Біле кільце 2.5 dp по краю картки у фокусі — як у прототипі. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.FocusRing(focused: Boolean, corner: Dp = CardCorner) {
    if (focused) {
        Box(Modifier.matchParentSize().border(2.5.dp, LikhtarColors.Paper, RoundedCornerShape(corner)))
    }
}

private val CardCorner = 9.dp
private val ShelfTitleStyle = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
private val LightEase = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.7f, 0.1f, 1f)

/** ТБ Ефір: канали, які вмикали з лаунчера. */
@Suppress("ModifierParameter") // crossZone — модифікатор для дітей (фокус між зонами), не для кореня
@Composable
private fun ChannelsContextRow(
    channels: List<RecentChannel>,
    pinned: Set<String>,
    onOpen: (RecentChannel) -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    ContextSection(label = stringResource(R.string.home_recent_channels)) {
        if (channels.isEmpty()) {
            item { ContextHint(stringResource(R.string.home_recent_channels_empty)) }
        } else {
            items(channels.size) { index ->
                val channel = channels[index]
                ChannelCard(
                    channel = channel,
                    pinned = channel.url in pinned,
                    onClick = { onOpen(channel) },
                    modifier = crossZone.thenFirst(index, first),
                )
            }
        }
    }
}

/**
 * Канал — типографічна картка, як у ТБ Ефірі (прототип): кольорова підкладка з назви,
 * знак каналу (якщо є) і назва. Колір — стабільний із назви, кожен канал впізнається.
 */
@Composable
private fun ChannelCard(channel: RecentChannel, pinned: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (c1, c2) = remember(channel.name) {
        val palette = listOf(
            0xFFFF6AA8 to 0xFF7B5CFF, 0xFFFF6A3D to 0xFFFFB347, 0xFF1FA2FF to 0xFF12D8FA,
            0xFFF857A6 to 0xFFFF5858, 0xFF11998E to 0xFF38EF7D, 0xFF8E2DE2 to 0xFF4A00E0,
        )
        val (a, b) = palette[Math.floorMod(channel.name.hashCode(), palette.size)]
        Color(a) to Color(b)
    }
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.lightSource().focusHalo(RoundedCornerShape(CardCorner)).size(width = ContinueCardWidth, height = ContinueCardHeight),
        shape = RoundedCornerShape(CardCorner),
        background = Color.Transparent,
        focusedBackground = Color.Transparent,
        borderColor = Color.Transparent,
        focusedScale = LikhtarDimens.CardFocusScale,
    ) { focused ->
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c1, c2)))) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to LikhtarColors.Shade.copy(alpha = 0.55f))))
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (channel.image.isNotBlank()) {
                    AsyncImage(
                        model = channel.image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(30.dp).padding(bottom = 6.dp),
                    )
                }
                Text(
                    channel.name,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 18.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            if (pinned) {
                Text(
                    stringResource(R.string.home_channel_pinned),
                    style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 9.sp),
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 11.dp, bottom = 8.dp),
                )
            }
            FocusRing(focused)
        }
    }
}

/** Пошук: запити, з яких щось відкривали. Перша пігулка завжди веде в чистий пошук. */
@Suppress("ModifierParameter") // crossZone — модифікатор для дітей (фокус між зонами), не для кореня
@Composable
private fun SearchContextRow(
    queries: List<String>,
    onSearch: (String) -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    ContextSection(label = stringResource(R.string.home_recent_searches)) {
        item {
            HomePill(
                text = stringResource(R.string.home_new_search),
                icon = R.drawable.ic_dock_search,
                onClick = { onSearch("") },
                modifier = crossZone.focusRequester(first),
            )
        }
        items(queries.size) { index ->
            val query = queries[index]
            HomePill(text = query, icon = R.drawable.ic_history, onClick = { onSearch(query) }, modifier = crossZone)
        }
    }
}

/** Смарт дім: компактний зріз того, що зараз удома. */
@Suppress("ModifierParameter") // crossZone — модифікатор для дітей (фокус між зонами), не для кореня
@Composable
private fun SmartHomeContextRow(
    devices: List<SmartDevice>,
    onOpen: () -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    ContextSection(label = stringResource(R.string.home_smart_now)) {
        if (devices.isEmpty()) {
            item { ContextHint(stringResource(R.string.home_smart_empty)) }
        } else {
            items(devices.size) { index ->
                val device = devices[index]
                val on = device.state.equals("on", ignoreCase = true)
                HomePill(
                    text = device.name,
                    icon = if (on) R.drawable.ic_bulb_on else R.drawable.ic_bulb,
                    small = deviceStateLabel(device),
                    lit = on,
                    onClick = onOpen,
                    modifier = crossZone.thenFirst(index, first),
                )
            }
        }
    }
}

/** Стан пристрою людською мовою: «увімкнено», а не «on». */
@Composable
private fun deviceStateLabel(device: SmartDevice): String {
    device.detail?.let { return it }
    val unit = device.unit.orEmpty()
    return when (device.state.lowercase()) {
        "on" -> stringResource(R.string.home_device_on)
        "off" -> stringResource(R.string.home_device_off)
        "unavailable", "unknown" -> stringResource(R.string.home_device_unavailable)
        else -> "${device.state} $unit".trim()
    }
}

/**
 * Порожній ряд говорить, чого чекати, і не бере фокус.
 *
 * Історії наповнюються самим користуванням: доки нічого не вмикали й не шукали, рядові
 * нема що показати, і вдавати картки було б обіцянкою, якої екран не виконує.
 */
@Composable
private fun ContextHint(text: String) {
    Text(
        text = text,
        style = LikhtarType.Body2,
        color = LikhtarColors.InkFaint,
        modifier = Modifier.height(ContextCardHeight).padding(top = 14.dp),
    )
}

/**
 * Горизонтальні поля Головної — ті самі, що в решти розділів. Своє значення тут було
 * 36dp проти [LikhtarDimens.ScreenPadding] = 32dp, і на кожному переході Головна↔розділ
 * увесь вміст зсувався на 4dp убік.
 *
 * Вертикальні поля лишаються власними: у розділів згори стоїть меню, а тут його немає.
 */
private val HomePadding = 48.dp
private val ContentTop = 36.dp
private val DockBottom = 36.dp


private const val CARD_HOURS = 3



private const val AMBIENT_FADE_MS = 900



/**
 * Тінь лише під самим текстом годинника й дати — щоб біле читалось на світлих шпалерах.
 * Великі темні плями під ним сприймали за поломку телевізора.
 */
private val TextShade = androidx.compose.ui.graphics.Shadow(
    color = LikhtarColors.Shade.copy(alpha = 0.6f),
    offset = Offset(0f, 2f),
    blurRadius = 18f,
)

/**
 * Годинник Головної — на 10% дрібніший за еталонний `LikhtarType.Clock`.
 *
 * Міняємо на місці вжитку, а не в типографії: той самий стиль носить велику цифру на
 * окремому екрані погоди, і там кегль не чіпаємо.
 */
private val HomeClockStyle = LikhtarType.Clock.copy(
    fontSize = 84.sp,
    lineHeight = 76.sp,
    letterSpacing = (-2.5).sp,
    shadow = TextShade,
)


/** Значок плитки доку. */
private val DockIconSize = 17.dp

/** Розміри контекстного ряду: під чотири картки в рядку на 1080p. */
private val ContextCardWidth = 196.dp
private val ContextCardHeight = 52.dp

/**
 * Кінозал: недодивлене, і більше нічого.
 *
 * Плиток застосунків і «Відкрити всі» тут немає навмисно. Раніше цей ряд був спільною
 * нижньою полицею Головної й тягнув їх із собою; тепер він — контекст рівно одного
 * пункту доку, а застосунки мають у доці власну плитку.
 */
@Suppress("ModifierParameter") // crossZone — модифікатор для дітей (фокус між зонами), не для кореня
@Composable
private fun CinemaContextRow(
    cards: List<ResumeCard>,
    onOpen: (ResumeCard) -> Unit,
    onOpenHistory: () -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    ContextSection(label = stringResource(R.string.home_continue_watching)) {
        if (cards.isEmpty()) {
            item { Text(stringResource(R.string.home_continue_watching_empty), style = LikhtarType.Body2, color = LikhtarColors.InkFaint) }
        } else {
            // Чотири картки і вхід у повну історію п'ятим слотом. Кількість була
            // налаштуванням, але вибирати тут нічого: п'ять слотів — це вся ширина,
            // яку ряд має під дком, а решта недодивленого живе в «Історії».
            val shown = cards.take(CONTINUE_CARDS)
            items(shown.size) { index ->
                val card = shown[index]
                ContinueCard(
                    card = card,
                    onClick = { onOpen(card) },
                    modifier = crossZone.thenFirst(index, first),
                )
            }
            item { ContinueAllCard(onClick = onOpenHistory, modifier = crossZone) }
        }
    }
}

/**
 * Ряд над «Реаліті»: випуски, на яких зупинились, — те саме «Продовжити», що в самому
 * розділі. Досі над плиткою не було нічого: гілки для Реаліті в доці просто не існувало.
 */
@Suppress("ModifierParameter") // crossZone — модифікатор для дітей (фокус між зонами), не для кореня
@Composable
private fun RealityContextRow(
    cards: List<ResumeCard>,
    onOpen: (ResumeCard) -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
) {
    ContextSection(label = stringResource(R.string.home_continue_reality)) {
        if (cards.isEmpty()) {
            item { ContextHint(stringResource(R.string.home_continue_reality_empty)) }
        } else {
            val shown = cards.take(CONTINUE_CARDS + 1)
            items(shown.size) { index ->
                val card = shown[index]
                ContinueCard(card = card, onClick = { onOpen(card) }, modifier = crossZone.thenFirst(index, first))
            }
        }
    }
}

/** Останній слот ряду: решта недодивленого живе на сторінці «Історія». */
@Composable
private fun ContinueAllCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        // Вузька: чотири картки по 190 і ця — рівно ширина ряду (896 dp). Повної ширини п'ять
        // карток не влазили ніколи — Row мовчки стискав цю до ~95 dp, а відколи ряд
        // прокручується, її різав край екрана.
        modifier = modifier.lightSource().size(width = ContinueAllWidth, height = ContinueCardHeight),
        shape = RoundedCornerShape(CardCorner),
        background = LikhtarColors.Surface.copy(alpha = 0.82f),
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusRingColor = Color.Transparent,
        focusedScale = LikhtarDimens.CardFocusScale,
    ) { focused ->
        Text(
            text = stringResource(R.string.home_all_history),
            style = LikhtarType.Label,
            color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextSecondary,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Картка недодивленого: кадр, назва, скільки лишилось і смужка прогресу. */
@Composable
private fun ContinueCard(card: ResumeCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.lightSource().focusHalo(RoundedCornerShape(CardCorner)).size(width = ContinueCardWidth, height = ContinueCardHeight),
        shape = RoundedCornerShape(CardCorner),
        background = ContinueCardFill,
        focusedBackground = ContinueCardFill,
        borderColor = Color.Transparent,
        focusedScale = LikhtarDimens.CardFocusScale,
    ) { focused ->
        Box(modifier = Modifier.fillMaxSize()) {
            card.imageUrl?.let { image ->
                AsyncImage(
                    model = image,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Градієнт знизу: назва мусить читатися на будь-якому кадрі. Назва текстом чи
            // мініатюра YouTube — щільна смуга, як у Кінозалі: на кадрах українських шоу
            // вже надруковано «13-16 СЕРІЇ…», і наш підпис ліг поверх нього.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (card.logoUrl == null || hasBakedText(card.imageUrl)) {
                            BakedTextScrim
                        } else {
                            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.9f)))
                        },
                    ),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                ResumeTitle(card.title, card.logoUrl, LikhtarType.Label, logoHeight = 30.dp)
                Text(
                    text = card.subtitle,
                    style = LikhtarType.MonoTiny,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            card.progress?.let { progress ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(LikhtarColors.Cyan),
                    )
                }
            }
        }
        FocusRing(focused)
    }
}

/** Чотири картки плюс «Вся історія» — рівно та ширина, що є під ряд. */
private const val CONTINUE_CARDS = 4

private val ContinueCardWidth = 200.dp
private val ContinueCardHeight = 112.dp
private val ContinueAllWidth = 96.dp
private val ContinueCardFill = LikhtarColors.ContinueFill

/** Знак бренду в доці більший за решту значків: це логотип, а не піктограма розділу. */
private val BrandIconSize = 26.dp

/**
 * Застосунки: п'ять закріплених або останніх запущених.
 *
 * Раніше останній застосунок стояв окремою плиткою доку — між «Пошуком» і
 * «Застосунками», зі стрілкою для циклу. Він плутав два різні рівні: док — це розділи
 * лаунчера, а конкретний застосунок розділом не є. Тепер він там, де й решта контексту.
 */
@Suppress("ModifierParameter") // crossZone — модифікатор для дітей (фокус між зонами), не для кореня
@Composable
private fun AppsContextRow(
    apps: List<LauncherApp>,
    onLaunch: (LauncherApp) -> Unit,
    first: FocusRequester,
    crossZone: Modifier,
    label: Int = R.string.home_your_apps,
) {
    ContextSection(label = stringResource(label)) {
        if (apps.isEmpty()) {
            item { ContextHint(stringResource(R.string.home_your_apps_empty)) }
        } else {
            items(apps.size) { index ->
                val app = apps[index]
                AppCard(
                    app = app,
                    onClick = { onLaunch(app) },
                    modifier = crossZone.thenFirst(index, first),
                )
            }
        }
    }
}

/** Плитка застосунку, як у прототипі: темна 16:9, TV-банер застосунку цілком або іконка з назвою. */
@Composable
private fun AppCard(app: LauncherApp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val artwork = rememberAppArtwork(app.packageName, app.activityName)
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.lightSource().focusHalo(RoundedCornerShape(CardCorner)).size(width = AppCardWidth, height = AppCardWidth * 9 / 16),
        shape = RoundedCornerShape(CardCorner),
        background = LikhtarColors.TileFloor,
        focusedBackground = LikhtarColors.TileFloor,
        borderColor = Color.Transparent,
        focusedScale = LikhtarDimens.CardFocusScale,
    ) { focused ->
        when {
            artwork?.banner == true -> Image(
                bitmap = artwork.image,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Column(
                modifier = Modifier.fillMaxSize().background(artwork?.backdrop ?: LikhtarColors.TileFloor).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                artwork?.let { Image(bitmap = it.image, contentDescription = null, modifier = Modifier.size(34.dp)) }
                Text(
                    app.label,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        FocusRing(focused)
    }
}

private val AppCardWidth = 150.dp

/**
 * Кеглі погодного віджета зняті пропорціями з еталона, а не підібрані на око.
 *
 * У картці свої стилі, а не спільні токени: `LikhtarType.WeatherTemp` (33sp) лишається
 * бічній панелі, де поруч стоїть щільна таблиця годин. Тут температура — головна цифра
 * екрана поруч із годинником, і вона мусить із ним перегукуватись.
 */
private val WeatherTempStyle = TextStyle(
    fontFamily = LikhtarType.DisplayFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 36.sp,
    lineHeight = 38.sp,
    letterSpacing = (-1.0).sp,
    fontFeatureSettings = "tnum",
)

private val WeatherCityStyle = TextStyle(
    fontFamily = LikhtarType.BodyFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
)

private val HourTimeStyle = TextStyle(
    fontFamily = LikhtarType.MonoFamily,
    fontSize = 11.5.sp,
)

private val HourTempStyle = TextStyle(
    fontFamily = LikhtarType.DisplayFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    fontFeatureSettings = "tnum",
)


/** Реквестер вішаємо лише на першу картку ряду — саме на неї веде ↑ з доку. */
private fun Modifier.thenFirst(index: Int, first: FocusRequester): Modifier =
    if (index == 0) this.focusRequester(first) else this

/** Запас ряду над доком під збільшення сфокусованої картки (див. ContextSection). */
private val ScrollEdge = 16.dp

/** Скільки випадкових запитів Ліхтарика показує рядок під «LIKHTAR HOME». */
private const val ASKS_SHOWN = 3
