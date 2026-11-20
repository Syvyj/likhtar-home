package com.likhtar.home.feature.apps
import com.likhtar.home.core.designsystem.CloudColors

import androidx.compose.runtime.saveable.rememberSaveable
import com.likhtar.home.core.designsystem.tryFocus
import com.likhtar.home.core.designsystem.RailRestore
import com.likhtar.home.core.designsystem.ScrollRailTo
import com.likhtar.home.core.designsystem.leave
import com.likhtar.home.core.designsystem.rememberRailRestore
import com.likhtar.home.core.designsystem.returnPoint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxHeight
import com.likhtar.home.core.designsystem.BrandColors
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import com.likhtar.home.core.designsystem.FocusRing
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.focusGlow
import com.likhtar.home.core.data.RetroGame
import com.likhtar.home.core.data.cloud.toCloudGame
import com.likhtar.home.core.model.AppCategory
import com.likhtar.home.core.model.LauncherApp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import com.likhtar.home.core.designsystem.centerItem
import com.likhtar.home.core.designsystem.NoBringIntoView
import com.likhtar.home.core.designsystem.LikhtarRail
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.AppTile
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarScreenScaffold
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.onWallpaper
import com.likhtar.home.core.designsystem.focusDirections
import com.likhtar.home.core.designsystem.PinPadDialog
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import com.likhtar.home.core.designsystem.requestWithRetry
import com.likhtar.home.core.designsystem.rememberAppArtwork
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.model.LauncherAppWithState
import com.likhtar.home.core.model.effectiveCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * Сторінка «Застосунки»: чисті іконки, довге натискання відкриває панель дій,
 * блокування підтверджується PIN. Приховані застосунки лишаються тут (приглушені),
 * але не потрапляють в інші розділи.
 *
 * [games] — той самий екран як розділ «Ігри»: лише ігри й емулятори, нещодавні першими.
 */
@Composable
fun AppsScreen(
    onSelectSection: (LikhtarSection) -> Unit,
    games: Boolean = false,
    /** Хмарні ігри: сторінка каталогу ([search] — одразу з пошуком) і картка гри. */
    onOpenCloudCatalog: (search: Boolean, mine: Boolean) -> Unit = { _, _ -> },
    onOpenCloudGame: (String) -> Unit = {},
    /** «Які сервіси в тебе є?»: [first] — перший запуск. */
    onOpenCloudSetup: (first: Boolean) -> Unit = {},
    /** «Відкрити всі» з ряду приставки. */
    onOpenRetroSystem: (String) -> Unit = {},
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Хмарний каталог лише в «Іграх»: у «Застосунках» він не потрібен і не вантажиться.
    val cloudModel: com.likhtar.home.feature.apps.cloud.CloudGamesViewModel? = if (games) hiltViewModel() else null
    // Хмарні ігри разом з образами з теки ROMs — у тих самих рядах.
    val cloudAll = cloudModel?.games?.collectAsStateWithLifecycle()?.value.orEmpty()
    val library = cloudModel?.library?.collectAsStateWithLifecycle()?.value ?: com.likhtar.home.core.data.cloud.CloudLibrary()
    // Видно лише ігри підключених сервісів («Мої сервіси»).
    val cloud = remember(cloudAll, library.services) { cloudAll.filter(library::visible) }
    var cloudFocus by remember { mutableStateOf<com.likhtar.home.feature.apps.cloud.CloudHomeFocus>(com.likhtar.home.feature.apps.cloud.CloudHomeFocus.Actions) }
    var cloudMenu by remember { mutableStateOf<com.likhtar.home.core.data.cloud.CloudGame?>(null) }
    // Перший запуск хмарних ігор — «Які сервіси в тебе є?» (макет: #setup).
    LaunchedEffect(cloudAll.any { it.rom == null }, library.setupDone) { if (cloudAll.any { it.rom == null } && !library.setupDone) onOpenCloudSetup(true) }
    val hasApps = if (games) state.apps.any { it.isGame } || state.roms.isNotEmpty() || cloud.isNotEmpty() else state.apps.isNotEmpty()
    // Образи перечитуємо щоразу, як розділ на екрані: щойно скопійовані й дозвіл, даний у системі.
    if (games) androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshRoms()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    // Кожен — лише у своєму випадку: зі спільним ключем на зміні (хмарний каталог дочитався пізніше)
    // перезапускались обидва, і меню перехоплювало фокус у першого ряду.
    val menuFocusRequester = remember { FocusRequester() }
    val firstAppFocusRequester = remember { FocusRequester() }
    // І коли дочитався хмарний каталог: ряди зсуваються («Граю зараз» стає першим), фокус з ретро-ряду
    // губився й падав у меню (холодний старт).
    // Повернення з картки гри, «Відкрити всі» тощо — на ту саму картку, спільним RailRestore, як у
    // Кінозалі (його ставить AppsPage); тоді перший ряд не просимо — він уже за краєм, і фокус падав у меню.
    val returnTile = rememberSaveable { mutableStateOf<String?>(null) }
    val restore = rememberRailRestore(returnTile) { it != null }
    LaunchedEffect(hasApps, cloud.isNotEmpty()) { if (!restore.pending) (if (hasApps) firstAppFocusRequester else menuFocusRequester).requestWithRetry() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Меню дій керується станом, а не фокусом. Фокус увесь час лишається на
            // плитці, з якої меню відкрили: «протікати» вниз нічому (усі клавіші
            // перехоплено тут, вище сітки), а після закриття фокус уже на місці —
            // жодних деактивацій і відновлень. PIN-діалог — окреме вікно, його
            // клавіші сюди не доходять.
            .onPreviewKeyEvent { event ->
                if (!state.actionsOpen) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                if (event.type == KeyEventType.KeyUp) {
                    // Ковтаємо й відпускання: перше з них — «хвіст» того самого
                    // утримання OK, що відкрило меню; дійшовши до плитки, воно
                    // запустило б застосунок.
                    return@onPreviewKeyEvent event.key in MENU_KEYS
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key !in MENU_KEYS) return@onPreviewKeyEvent false
                // Автоповтори утримання не мають клацати по пунктах меню.
                if (native.repeatCount > 0) return@onPreviewKeyEvent true
                viewModel.moveActions(
                    when (event.key) {
                        Key.DirectionUp -> "up"
                        Key.DirectionDown -> "down"
                        Key.DirectionLeft -> "left"
                        Key.DirectionRight -> "right"
                        Key.Back -> "back"
                        else -> "enter"
                    },
                )
            },
    ) {
        // Тепле тло likhtar-apps-modern.html / likhtar-games-modern.html від самого верху під меню.
        if (cloud.isNotEmpty()) {
            Box(Modifier.fillMaxSize().background(LikhtarColors.Background))
            com.likhtar.home.feature.apps.cloud.CloudHomeBackdrop((cloudFocus as? com.likhtar.home.feature.apps.cloud.CloudHomeFocus.Game)?.game)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LikhtarColors.Background)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(if (games) LikhtarColors.HeroGlowGames else LikhtarColors.HeroGlowApps, Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(1440f, 160f),
                            radius = 780f,
                        ),
                    ),
            )
        }
        LikhtarScreenScaffold(
            section = if (games) LikhtarSection.GAMES else LikhtarSection.APPS,
            onSelectSection = onSelectSection,
            menuFocusRequester = menuFocusRequester,
            contentFocusRequester = firstAppFocusRequester,
        ) {
            AppsPage(
                state = state,
                games = games,
                firstAppFocusRequester = firstAppFocusRequester,
                menuFocusRequester = menuFocusRequester,
                onFocused = viewModel::onAppFocused,
                onClick = viewModel::launch,
                onLongClick = { viewModel.openActions(it.app.id) },
                onGameClick = { game ->
                    viewModel.launchGame(game)?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
                },
                onFileAccess = viewModel::openFileAccess,
                onCloudClick = { cloud ->
                    viewModel.openCloud(cloud)?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
                },
                cloudGames = cloud,
                cloudLibrary = library,
                cloudFocus = cloudFocus,
                onCloudFocus = { cloudFocus = it },
                onCloudMenu = { cloudMenu = it },
                onOpenCloudCatalog = onOpenCloudCatalog,
                onOpenCloudGame = onOpenCloudGame,
                onOpenCloudSetup = { onOpenCloudSetup(false) },
                onOpenRetroSystem = onOpenRetroSystem,
                restore = restore,
            )
        }

        cloudMenu?.let { g -> if (cloudModel != null) com.likhtar.home.feature.apps.cloud.CloudGameMenu(g, cloudModel, onDismiss = { cloudMenu = null }) }

        state.selected?.takeIf { state.actionsOpen }?.let { entry ->
            ActionsDialog(
                entry = entry,
                selected = state.actionIndex,
                message = state.message,
            )
        }

        state.pinRequest?.let { request ->
            PinPadDialog(
                prompt = request.prompt,
                error = request.error,
                onSubmit = viewModel::submitPin,
                onDismiss = viewModel::dismissPin,
            )
        }
    }
}

/**
 * Сітка по групах: «Кіно та відео», «Ігри», «Інструменти», «Інше» і, якщо є, «Приховані».
 *
 * Заголовок займає весь рядок сітки (`span`), тому картки під ним лишаються рівно в тих
 * самих шести колонках — групи не ламають вертикальну навігацію.
 */
/** Заголовок рядка в стилі likhtar-apps-modern.html: назва та тихий лічильник поруч. */
@Composable
private fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            color = LikhtarColors.GlassActive,
        )
        Text(
            text = count.toString(),
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Normal, fontSize = 12.sp),
            color = LikhtarColors.WarmMuted,
        )
    }
}

/**
 * Застосунки в подачі Кінозалу: згори шапка
 * сфокусованого — кадр того, що зараз на сервісі, або його банер на його ж кольорі; нижче
 * рейки — «Нещодавні», далі категорії. Тло рівне, графітове: на яскравих шпалерах плитки
 * губились.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppsPage(
    state: AppsUiState,
    games: Boolean,
    firstAppFocusRequester: FocusRequester,
    menuFocusRequester: FocusRequester,
    onFocused: (String) -> Unit,
    onClick: (LauncherAppWithState) -> Unit,
    onLongClick: (LauncherAppWithState) -> Unit,
    onGameClick: (RetroGame) -> Unit = {},
    onFileAccess: () -> Unit = {},
    onCloudClick: (CloudGame) -> Unit = {},
    cloudGames: List<com.likhtar.home.core.data.cloud.CloudGame> = emptyList(),
    cloudLibrary: com.likhtar.home.core.data.cloud.CloudLibrary = com.likhtar.home.core.data.cloud.CloudLibrary(),
    cloudFocus: com.likhtar.home.feature.apps.cloud.CloudHomeFocus = com.likhtar.home.feature.apps.cloud.CloudHomeFocus.Actions,
    onCloudFocus: (com.likhtar.home.feature.apps.cloud.CloudHomeFocus) -> Unit = {},
    onCloudMenu: (com.likhtar.home.core.data.cloud.CloudGame) -> Unit = {},
    onOpenCloudCatalog: (Boolean, Boolean) -> Unit = { _, _ -> },
    onOpenCloudGame: (String) -> Unit = {},
    onOpenCloudSetup: () -> Unit = {},
    onOpenRetroSystem: (String) -> Unit = {},
    restore: RailRestore,
) {
    val cloudMode = games && cloudGames.isNotEmpty()
    val actionsFocus = remember { FocusRequester() }
    var contentTop by remember { mutableStateOf(0.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // «Усі застосунки»: той самий екран, але без груп — усе встановлене за абеткою (і приховане,
    // притемнене), рядами по шість. BACK — назад до груп.
    var showAll by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showAll) { showAll = false }
    val allTitle = stringResource(R.string.apps_all)
    val rows = if (games) buildList {
        // Ігрова кімната, а не бібліотека: що грали — згори, далі хмара, образи за приставками,
        // а встановлені ігри й емулятори (нещодавні першими) — унизу.
        // Хмарні ігри — верхній блок «Ігор» (docs/cloud-games-design.md): пошук, усі ігри, популярні.
        if (cloudGames.isNotEmpty()) {
            // «Мої ігри» — безкоштовні, Game Pass і «У мене є»; далі мої списки.
            val byKey = cloudGames.associateBy { it.key }
            // Зниклий образ (витягли флешку) лишається в списку тьмяною карткою, а не зникає мовчки.
            fun resolve(key: String) = byKey[key] ?: com.likhtar.home.core.data.cloud.missingRom(key)
            val now = cloudLibrary.playing.mapNotNull(::resolve)
            val fav = cloudLibrary.favorites.mapNotNull(::resolve)
            val mine = cloudGames.filter(cloudLibrary::isMine).sortedByDescending { it.popularity }
            // «Мої ігри» першими: вони є з першого дня (безкоштовні, Game Pass), а «Граю зараз» і
            // «Улюблене» новачкові порожні — розділ не має відкриватися порожнечею.
            add(AppSection(stringResource(R.string.cloud_mine), emptyList(), cloudGames = mine, cloudKind = "mine", count = mine.size))
            add(AppSection(stringResource(R.string.cloud_now), emptyList(), cloudGames = now, cloudKind = "now", count = now.size))
            add(AppSection(stringResource(R.string.cloud_favorites), emptyList(), cloudGames = fav, cloudKind = "fav", count = fav.size))
        }
        if (state.recentRoms.isNotEmpty()) add(AppSection(stringResource(R.string.apps_games_continue), emptyList(), state.recentRoms))
        add(AppSection(stringResource(R.string.apps_games_cloud), emptyList(), clouds = CloudGame.entries))
        // Кілька образів — одним рядом; велика колекція — рядами за приставками.
        if (state.roms.size <= ONE_ROW_ROMS) {
            if (state.roms.isNotEmpty()) add(AppSection(stringResource(R.string.apps_games_retro), emptyList(), state.roms))
        } else {
            state.roms.groupBy { it.system }.forEach { (system, list) -> add(AppSection(system.title, emptyList(), list, system = system.title)) }
        }
        val cloudPackages = CloudGame.entries.flatMap { it.packages }.toSet()
        val recentIds = state.recent.map { it.app.id }
        val installed = state.apps.filter { it.isGame && it.app.packageName !in cloudPackages }
            .sortedWith(compareBy({ recentIds.indexOf(it.app.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.app.label.lowercase() }))
        if (installed.isNotEmpty()) add(AppSection(stringResource(R.string.apps_games_installed), installed))
    } else if (showAll) {
        state.apps.sortedBy { it.app.label.lowercase() }.chunked(ALL_PER_ROW).mapIndexed { i, chunk ->
            AppSection(if (i == 0) allTitle else "", chunk, count = state.apps.size)
        }
    } else buildList {
        if (state.recent.isNotEmpty()) add(AppSection(stringResource(R.string.apps_section_recent), state.recent))
        addAll(state.sections)
    }
    val lead = if (games) (stringResource(R.string.apps_games_file_access) to onFileAccess).takeIf { !state.romsReadable } else null
    val viewToggleFocusRequester = remember { FocusRequester() }
    val gridToggleFocusRequester = remember { FocusRequester() }
    val heroToggle = lead.takeIf { !cloudMode }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Гра у фокусі — шапка про неї; фокус на застосунку повертає шапку застосунку.
    var focusedGame by remember { mutableStateOf<RetroGame?>(null) }
    var focusedCloud by remember { mutableStateOf<CloudGame?>(null) }
    val installedPackages = remember(state.apps) { state.apps.map { it.app.packageName }.toSet() }
    val installedApps = remember(state.apps) { state.apps.associate { it.app.packageName to it.app } }
    // Вхід кожного ряду — його перша плитка. ↑/↓ ведемо самі: стандартний пошук фокуса
    // тут водив ↓ уздовж ряду.
    val entries = remember(rows.size) { List(rows.size) { i -> if (i == 0) firstAppFocusRequester else FocusRequester() } }
    fun go(to: Int): Boolean {
        if (to < 0) return when {
            cloudMode -> actionsFocus.tryFocus()
            !games -> (if (showAll) gridToggleFocusRequester else viewToggleFocusRequester).tryFocus()
            heroToggle != null -> viewToggleFocusRequester.tryFocus()
            else -> menuFocusRequester.tryFocus()
        }
        if (to > rows.lastIndex) return true
        // Фокус — одразу, ряд доїжджає під ним (centerItem з фокусу плитки), як у Кінозалі.
        // Спершу докрутка з чеканням кінця руху давала рамці запізнитись.
        scope.launch {
            if (list.layoutInfo.visibleItemsInfo.none { it.index == to } || !entries[to].tryFocus()) {
                list.scrollToItem(to)
                withFrameNanos { }
                entries[to].tryFocus()
            }
        }
        return true
    }
    // Повернулись на екран — фокус на картку, з якої пішли; не знайшлась — на перший ряд.
    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isNotEmpty()) restore.restore(list) { entries.firstOrNull()?.requestWithRetry() }
    }
    Box(
        modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { contentTop = with(density) { it.positionInRoot().y.toDp() } },
    ) {
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.apps_games_empty) + "\n\n" + stringResource(R.string.apps_games_roms_hint) +
                    if (state.romsReadable) "" else "\n\n" + stringResource(R.string.apps_games_empty_access),
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 16.sp, lineHeight = 24.sp),
                color = SectionTitleInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).width(520.dp),
            )
            return@Box
        }
        if (games && !cloudMode) {
            when {
                focusedGame != null -> GameHero(focusedGame!!)
                focusedCloud != null -> CloudHero(focusedCloud!!, installed = focusedCloud!!.packages.any { it in installedPackages })
                else -> state.selected?.let { AppsHero(entry = it, hero = state.hero, games = true) }
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            if (cloudMode) {
                com.likhtar.home.feature.apps.cloud.CloudHomeHeader(
                    focus = cloudFocus,
                    total = cloudGames.size,
                    contentTop = contentTop,
                    actionsFocus = actionsFocus,
                    onSearch = { onOpenCloudCatalog(true, false) },
                    onAll = { onOpenCloudCatalog(false, false) },
                    onServices = onOpenCloudSetup,
                    onDownFromActions = { go(0) },
                    library = cloudLibrary,
                )
            } else if (!games) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = LikhtarDimens.ScreenPadding, end = LikhtarDimens.ScreenPadding, top = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = allTitle,
                            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                            color = LikhtarColors.GlassActive,
                        )
                        Text(
                            text = stringResource(R.string.apps_hero_hint),
                            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp),
                            color = LikhtarColors.WarmMuted,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(LikhtarColors.SurfaceDeep.copy(alpha = 0.72f))
                            .border(1.dp, LikhtarColors.PillBorder, RoundedCornerShape(99.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FocusableSurface(
                            onClick = { showAll = false },
                            modifier = Modifier
                                .focusRequester(viewToggleFocusRequester)
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (ev.key) {
                                        Key.DirectionUp -> menuFocusRequester.tryFocus()
                                        Key.DirectionDown -> firstAppFocusRequester.tryFocus()
                                        Key.DirectionRight -> gridToggleFocusRequester.tryFocus()
                                        else -> false
                                    }
                                },
                            shape = RoundedCornerShape(99.dp),
                            background = if (!showAll) LikhtarColors.BorderStrong.copy(alpha = 0.69f) else Color.Transparent,
                            focusedBackground = LikhtarColors.GlassActive,
                            borderColor = Color.Transparent,
                            focusedScale = 1.04f,
                        ) { focused ->
                            Text(
                                text = stringResource(R.string.apps_by_groups),
                                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp),
                                color = when {
                                    focused -> LikhtarColors.OnGlassActive
                                    !showAll -> Color.White
                                    else -> LikhtarColors.WarmInkSoft
                                },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                        FocusableSurface(
                            onClick = { showAll = true },
                            modifier = Modifier
                                .focusRequester(gridToggleFocusRequester)
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (ev.key) {
                                        Key.DirectionUp -> menuFocusRequester.tryFocus()
                                        Key.DirectionDown -> firstAppFocusRequester.tryFocus()
                                        Key.DirectionLeft -> viewToggleFocusRequester.tryFocus()
                                        else -> false
                                    }
                                },
                            shape = RoundedCornerShape(99.dp),
                            background = if (showAll) LikhtarColors.BorderStrong.copy(alpha = 0.69f) else Color.Transparent,
                            focusedBackground = LikhtarColors.GlassActive,
                            borderColor = Color.Transparent,
                            focusedScale = 1.04f,
                        ) { focused ->
                            Text(
                                text = stringResource(R.string.apps_view_grid),
                                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp),
                                color = when {
                                    focused -> LikhtarColors.OnGlassActive
                                    showAll -> Color.White
                                    else -> LikhtarColors.WarmInkSoft
                                },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(HeroHeight)) {
                    heroToggle?.let { (toggleLabel, onToggle) ->
                        FocusableSurface(
                            onClick = onToggle,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = LikhtarDimens.ScreenPadding, bottom = 36.dp)
                                .focusRequester(viewToggleFocusRequester)
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (ev.key) {
                                        Key.DirectionUp -> menuFocusRequester.tryFocus()
                                        Key.DirectionDown -> firstAppFocusRequester.tryFocus()
                                        else -> false
                                    }
                                },
                            shape = RoundedCornerShape(99.dp),
                            background = LikhtarColors.SurfaceAlt,
                            focusedBackground = LikhtarColors.GlassActive,
                            borderColor = LikhtarColors.PillBorder,
                            focusedScale = 1.05f,
                        ) { focused ->
                            Text(
                                text = toggleLabel,
                                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
                                color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.GlassActive,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
            // Колонка сама фокус не доганяє — ряд у фокусі ставить centerItem, як у Кінозалі.
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoView) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().clipToBounds().padding(horizontal = LikhtarDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 60.dp),
                ) {
                    itemsIndexed(rows, key = { i, row -> "$showAll-$i-${row.title}" }) { rowIndex, row ->
                        AppsRail(
                            row = row,
                            // Плитка «Образи з флешки» — лише в хмарному режимі Ігор; у Застосунках перемикач живе в шапці.
                            lead = if (cloudMode) lead.takeIf { rowIndex == rows.indexOfFirst { it.cloudKind.isEmpty() } } else null,
                            enter = entries.getOrElse(rowIndex) { firstAppFocusRequester },
                            onVertical = { down -> go(if (down) rowIndex + 1 else rowIndex - 1) },
                            onFocused = { id ->
                                focusedGame = null
                                focusedCloud = null
                                onFocused(id)
                                scope.launch { list.centerItem(rowIndex, topUntil = 0) }
                            },
                            onClick = onClick,
                            onLongClick = onLongClick,
                            onGameFocused = { game ->
                                // Хмарна головна — герой про образ у фокусі, як про хмарну гру.
                                if (cloudMode) onCloudFocus(com.likhtar.home.feature.apps.cloud.CloudHomeFocus.Game(game.toCloudGame(), play = true))
                                focusedGame = game
                                focusedCloud = null
                                scope.launch { list.centerItem(rowIndex, topUntil = 0) }
                            },
                            onGameClick = onGameClick,
                            installedPackages = installedPackages,
                            installedApps = installedApps,
                            onOpenRetroSystem = onOpenRetroSystem,
                            onCloudFocused = { cloud ->
                                focusedCloud = cloud
                                focusedGame = null
                                scope.launch { list.centerItem(rowIndex, topUntil = 0) }
                            },
                            onCloudClick = onCloudClick,
                            onCloudFocus = { f ->
                                onCloudFocus(f)
                                scope.launch { list.centerItem(rowIndex, topUntil = 0) }
                            },
                            onCloudMenu = onCloudMenu,
                            cloudServices = cloudLibrary.services,
                            onOpenCloudGame = onOpenCloudGame,
                            onOpenCloudCatalog = onOpenCloudCatalog,
                            restore = restore,
                            rowIndex = rowIndex,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppsRail(
    row: AppSection,
    enter: FocusRequester,
    /** Плитка-перемикач перед застосунками (лише в першому ряду): підпис і дія. */
    lead: Pair<String, () -> Unit>?,
    onVertical: (down: Boolean) -> Boolean,
    onFocused: (String) -> Unit,
    onClick: (LauncherAppWithState) -> Unit,
    onLongClick: (LauncherAppWithState) -> Unit,
    onGameFocused: (RetroGame) -> Unit = {},
    onGameClick: (RetroGame) -> Unit = {},
    installedPackages: Set<String> = emptySet(),
    installedApps: Map<String, com.likhtar.home.core.model.LauncherApp> = emptyMap(),
    onOpenRetroSystem: (String) -> Unit = {},
    onCloudFocused: (CloudGame) -> Unit = {},
    onCloudClick: (CloudGame) -> Unit = {},
    onCloudFocus: (com.likhtar.home.feature.apps.cloud.CloudHomeFocus) -> Unit = {},
    onCloudMenu: (com.likhtar.home.core.data.cloud.CloudGame) -> Unit = {},
    cloudServices: Set<com.likhtar.home.core.data.cloud.CloudService> = com.likhtar.home.core.data.cloud.CloudService.entries.toSet(),
    onOpenCloudGame: (String) -> Unit = {},
    onOpenCloudCatalog: (Boolean, Boolean) -> Unit = { _, _ -> },
    restore: RailRestore,
    rowIndex: Int,
) {
    // Точка повернення — ключ елемента ряду з назвою ряду: та сама гра буває в «Граю зараз» і «Моїх».
    fun rk(key: String) = "${row.title}|$key"
    fun Modifier.back(key: String) = returnPoint(restore, rk(key), rowIndex)
    fun leaving(key: String, action: () -> Unit): () -> Unit = { restore.leave(rk(key)); action() }
    // Вхід у ряд — на першу плитку, як у рядах Кінозалу.
    // Ряд, з якого пішли, — знову на початок: інакше перша плитка вже не скомпонована, і
    // вхід у ряд (↓ з меню, ↑/↓ з сусіднього) мовчки не спрацьовував — як у Кінозалі.
    val railState = rememberLazyListState()
    val retroShown = if (row.system != null) row.games.take(RETRO_ROW) else row.games
    // Порядок ключів — як у LikhtarRail нижче: ряд докручується до точки повернення.
    restore.ScrollRailTo(railState) { tile ->
        buildList {
            if (lead != null) add("lead")
            if (row.cloudKind.isNotEmpty()) {
                row.cloudGames.take(CLOUD_ROW).forEach { add("cloud-" + it.key) }
                if (row.cloudGames.size > CLOUD_ROW) add("cloud-more")
                if (row.cloudKind != "mine") add("cloud-add")
            }
            row.clouds.forEach { add(row.title + it.name) }
            retroShown.forEach { add(row.title + it.path) }
            if (row.system != null && row.games.size > RETRO_ROW) add("retro-more")
            row.apps.forEach { add(row.title + it.app.id) }
        }.map(::rk).indexOf(tile)
    }
    var reset by remember { mutableStateOf(false) }
    LaunchedEffect(reset) {
        if (reset) {
            railState.scrollToItem(0)
            reset = false
        }
    }
    Column(
        modifier = Modifier
            // Не під час повернення: перша подія фокуса (ще без фокуса) скидала ряд на початок
            // поверх докрутки до точки повернення.
            .onFocusChanged { if (!it.hasFocus && !restore.pending) reset = true }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> onVertical(true)
                    Key.DirectionUp -> onVertical(false)
                    else -> false
                }
            },
    ) {
        if (row.title.isNotEmpty()) SectionHeader(title = row.title, count = row.count)
        LikhtarRail(state = railState) {
            lead?.let { (label, onLead) ->
                item(key = "lead") {
                    LeadTile(label, leaving("lead", onLead), Modifier.padding(horizontal = 6.dp, vertical = 6.dp).focusRequester(enter).back("lead"))
                }
            }
            if (row.cloudKind.isNotEmpty()) {
                val cardPad = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                itemsIndexed(row.cloudGames.take(CLOUD_ROW), key = { _, g -> "cloud-" + g.key }) { index, g ->
                    com.likhtar.home.feature.apps.cloud.CloudCard(
                        g, com.likhtar.home.feature.apps.cloud.CardShape.POSTER, leaving("cloud-" + g.key) { onOpenCloudGame(g.key) },
                        cardPad.then(if (index == 0 && lead == null) Modifier.focusRequester(enter) else Modifier)
                            .back("cloud-" + g.key)
                            .onFocusChanged { if (it.isFocused) onCloudFocus(com.likhtar.home.feature.apps.cloud.CloudHomeFocus.Game(g)) },
                        onLongClick = leaving("cloud-" + g.key) { onCloudMenu(g) },
                        services = cloudServices,
                    )
                }
                if (row.cloudGames.size > CLOUD_ROW) item(key = "cloud-more") {
                    com.likhtar.home.feature.apps.cloud.OpenAllCard(
                        row.count, com.likhtar.home.feature.apps.cloud.CardShape.POSTER, leaving("cloud-more") { onOpenCloudCatalog(false, row.cloudKind == "mine") },
                        cardPad.back("cloud-more").onFocusChanged { if (it.isFocused) onCloudFocus(com.likhtar.home.feature.apps.cloud.CloudHomeFocus.List(row.title, row.count, openAll = true)) },
                    )
                }
                if (row.cloudKind != "mine") item(key = "cloud-add") {
                    com.likhtar.home.feature.apps.cloud.AddToListCard(
                        if (row.cloudGames.isEmpty()) stringResource(R.string.cloud_empty_list, row.title) else stringResource(R.string.cloud_add),
                        leaving("cloud-add") { onOpenCloudCatalog(false, false) },
                        cardPad.then(if (row.cloudGames.isEmpty() && lead == null) Modifier.focusRequester(enter) else Modifier)
                            .back("cloud-add")
                            .onFocusChanged { if (it.isFocused) onCloudFocus(com.likhtar.home.feature.apps.cloud.CloudHomeFocus.List(row.title, row.count, openAll = false)) },
                    )
                }
            }
            itemsIndexed(row.clouds, key = { _, cloud -> row.title + cloud.name }) { index, cloud ->
                CloudTile(
                    cloud = cloud,
                    app = cloud.packages.firstNotNullOfOrNull { installedApps[it] },
                    onClick = leaving(row.title + cloud.name) { onCloudClick(cloud) },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .back(row.title + cloud.name)
                        .onFocusChanged { if (it.isFocused) onCloudFocused(cloud) }
                        .then(if (index == 0 && lead == null) Modifier.focusRequester(enter) else Modifier),
                )
            }
            itemsIndexed(retroShown, key = { _, game -> row.title + game.path }) { index, game ->
                GameTile(
                    game = game,
                    onClick = leaving(row.title + game.path) { onGameClick(game) },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .back(row.title + game.path)
                        .onFocusChanged { if (it.isFocused) onGameFocused(game) }
                        .then(if (index == 0 && lead == null) Modifier.focusRequester(enter) else Modifier),
                )
            }
            if (row.system != null && row.games.size > RETRO_ROW) item(key = "retro-more") {
                com.likhtar.home.feature.apps.cloud.OpenAllCard(
                    row.games.size, com.likhtar.home.feature.apps.cloud.CardShape.POSTER, leaving("retro-more") { onOpenRetroSystem(row.system) },
                    Modifier.padding(horizontal = 6.dp, vertical = 6.dp).size(GameTileWidth, GameTileHeight).back("retro-more"),
                )
            }
            itemsIndexed(row.apps, key = { _, entry -> row.title + entry.app.id }) { index, entry ->
                ProtoAppTile(
                    app = entry.app,
                    categoryLabel = stringResource(entry.effectiveCategory.titleRes),
                    pinnedToHome = entry.state.pinnedToHome,
                    locked = entry.state.locked,
                    dimmed = entry.state.hidden,
                    tags = buildList {
                        if (entry.state.pinnedToCinema) add(stringResource(R.string.apps_tag_cinema))
                        if (entry.state.pinnedToLiveTv) add(stringResource(R.string.apps_tag_live_tv))
                    },
                    onClick = leaving(row.title + entry.app.id) { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .back(row.title + entry.app.id)
                        .onFocusChanged { if (it.isFocused) onFocused(entry.app.id) }
                        .then(if (index == 0 && lead == null) Modifier.focusRequester(enter) else Modifier),
                )
            }
        }
    }
}

/**
 * Шапка в стилі likhtar-apps-modern.html: бурштиновий eyebrow, великий заголовок застосунку
 * кольором марки, категорія / статус і коротка підказка дій.
 */
@Composable
private fun AppsHero(entry: LauncherAppWithState, hero: AppHero, games: Boolean = false) {
    val artwork = rememberAppArtwork(entry.app.packageName, entry.app.activityName)
    val brand = appBrand(entry.app)
    Box(modifier = Modifier.fillMaxWidth().height(HeroHeight + 20.dp)) {
        if (hero.backdrop != null) {
            AsyncImage(
                model = hero.backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.TopEnd).width(560.dp).fillMaxHeight(),
            )
            Box(modifier = Modifier.fillMaxSize().background(HeroSideScrim))
            Box(modifier = Modifier.fillMaxSize().background(HeroBottomFade))
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = LikhtarDimens.ScreenPadding, bottom = 36.dp)
                .width(540.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = stringResource(if (games) R.string.apps_games_eyebrow else R.string.apps_eyebrow),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 1.4.sp),
                color = LikhtarColors.LanternAmber,
            )
            Text(
                text = brand?.wordmark ?: entry.app.label,
                style = (brand?.style ?: TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.ExtraBold))
                    .copy(fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
                color = brand?.color ?: LikhtarColors.GlassActive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    stringResource(entry.effectiveCategory.titleRes),
                    hero.note,
                    stringResource(R.string.apps_state_locked).takeIf { entry.state.locked },
                    stringResource(R.string.apps_state_hidden).takeIf { entry.state.hidden },
                ).joinToString(" · "),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 14.sp),
                color = LikhtarColors.WarmMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.apps_hero_hint),
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp),
                color = LikhtarColors.WarmMuted,
            )
        }
    }
}

/** Знак марки (.wm): «NETFLIX» червоним, «prime video» блакитним… — як StreamingBrand Кінозалу. */
internal data class AppBrand(val wordmark: String, val color: Color, val style: TextStyle)

private fun brandStyle(weight: Int, spacing: Float = -0.02f) = TextStyle(
    fontFamily = LikhtarType.Body,
    fontWeight = FontWeight(weight),
    letterSpacing = TextUnit(spacing, TextUnitType.Em),
)

internal fun appBrand(app: LauncherApp): AppBrand? {
    val n = app.label.lowercase()
    val p = app.packageName.lowercase()
    // Спершу пакет — він сталий і однаковий будь-якою мовою; назва — запасний шлях для збірок
    // з іншим пакетом (назва застосунку локалізується й перейменовується з оновленнями).
    when (p) {
        "com.netflix.ninja" -> return AppBrand("NETFLIX", BrandColors.Netflix, brandStyle(800, 0.04f))
        "com.google.android.youtube.tv" -> return AppBrand("YouTube", BrandColors.YouTube, brandStyle(700))
        "com.amazon.amazonvideo.livingroom" -> return AppBrand("prime video", BrandColors.Prime, brandStyle(600, -0.01f))
        "com.disney.disneyplus" -> return AppBrand("Disney+", BrandColors.Disney, brandStyle(700))
        "com.wbd.stream", "com.hbo.hbonow" -> return AppBrand("Max", Color.White, brandStyle(800))
        "com.apple.atve.androidtv.appletv" -> return AppBrand("Apple TV", BrandColors.AppleTv, brandStyle(600, -0.01f))
        "com.megogo.application" -> return AppBrand("MEGOGO", Color.White, brandStyle(800))
        "tv.sweet.tvplayer" -> return AppBrand("sweet.tv", BrandColors.Sweet, brandStyle(700))
        "org.xbmc.kodi" -> return AppBrand("Kodi", BrandColors.Kodi, brandStyle(700))
        "com.spotify.tv.android" -> return AppBrand("Spotify", BrandColors.Spotify, brandStyle(700))
    }
    return when {
        "netflix" in n -> AppBrand("NETFLIX", BrandColors.Netflix, brandStyle(800, 0.04f))
        "youtube" in n && "music" !in n -> AppBrand("YouTube", BrandColors.YouTube, brandStyle(700))
        "prime video" in n || "amazon.avod" in p -> AppBrand("prime video", BrandColors.Prime, brandStyle(600, -0.01f))
        "disney" in n -> AppBrand("Disney+", BrandColors.Disney, brandStyle(700))
        n == "max" || "hbo" in n -> AppBrand("Max", Color.White, brandStyle(800))
        "apple tv" in n -> AppBrand("Apple TV", BrandColors.AppleTv, brandStyle(600, -0.01f))
        "megogo" in n -> AppBrand("MEGOGO", Color.White, brandStyle(800))
        "sweet" in n -> AppBrand("sweet.tv", BrandColors.Sweet, brandStyle(700))
        n == "kodi" -> AppBrand("Kodi", BrandColors.Kodi, brandStyle(700))
        "just player" in n || "just+" in n -> AppBrand(app.label, BrandColors.JustPlayer, brandStyle(600, -0.01f))
        n in TEXT_BRANDS -> AppBrand(TEXT_BRANDS.getValue(n), LikhtarColors.TextPrimary, brandStyle(700))
        "spotify" in n -> AppBrand("Spotify", BrandColors.Spotify, brandStyle(700))
        else -> null
    }
}

/** Знак застосунку: марка — текстом її кольором; інакше — його власна іконка й назва. */
@Composable
private fun AppMark(
    app: LauncherApp,
    brand: AppBrand?,
    artwork: com.likhtar.home.core.designsystem.AppArtwork?,
    wordmarkSize: TextUnit,
    iconHeight: Dp,
) {
    // Справжній TV-банер застосунку — на всю плитку, як на Головній; наш знак марки — лише
    // запасний, коли банера немає.
    if (artwork?.banner == true) {
        Image(
            bitmap = artwork.image,
            contentDescription = app.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else if (brand != null) {
        Text(
            brand.wordmark,
            style = brand.style.copy(fontSize = wordmarkSize, lineHeight = wordmarkSize),
            color = brand.color,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            artwork?.let {
                Image(
                    bitmap = it.image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(iconHeight).widthIn(max = iconHeight * 1.8f).clip(RoundedCornerShape(6.dp)),
                )
            }
            Text(
                app.label,
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = wordmarkSize * 0.55f),
                color = LikhtarColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/**
 * Плитка в стилі likhtar-apps-modern.html: 146×82 dp (aspect-ratio 1.78), #1A1815, скруглення 14 dp,
 * легка рамка #18FFFFFF; бурштинова крапка для закріплених на Головній і замок угорі праворуч.
 */
@Composable
private fun ProtoAppTile(
    app: LauncherApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    categoryLabel: String = "",
    pinnedToHome: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    locked: Boolean = false,
    dimmed: Boolean = false,
    tags: List<String> = emptyList(),
) {
    val artwork = rememberAppArtwork(app.packageName, app.activityName)
    val brand = appBrand(app)
    var focused by remember { mutableStateOf(false) }
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(154.dp, 86.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { alpha = if (dimmed && !focused) 0.48f else 1f }
            .focusGlow(14.dp),
        shape = RoundedCornerShape(14.dp),
        background = LikhtarColors.PanelFloor,
        focusedBackground = LikhtarColors.Panel,
        borderColor = LikhtarColors.GlassDivider,
        focusedScale = 1.045f,
        focusLighten = 0f,
    ) { _ ->
        if (artwork?.banner == true) {
            AppMark(app, brand, artwork, wordmarkSize = 20.sp, iconHeight = 26.dp)
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                AppMark(app, brand, artwork, wordmarkSize = 20.sp, iconHeight = 24.dp)
                if (categoryLabel.isNotEmpty()) {
                    Text(
                        text = categoryLabel,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp),
                        color = LikhtarColors.WarmMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (pinnedToHome || locked) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 9.dp),
            ) {
                if (pinnedToHome) {
                    Text(
                        text = "●",
                        style = TextStyle(fontSize = 9.sp),
                        color = LikhtarColors.LanternAmber,
                    )
                }
                if (locked) {
                    androidx.tv.material3.Icon(
                        androidx.compose.ui.res.painterResource(com.likhtar.home.core.designsystem.R.drawable.ic_solar_lock),
                        null,
                        tint = LikhtarColors.LanternAmber,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        if (tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)) {
                tags.forEach {
                    Text(
                        it,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 7.5.sp),
                        color = LikhtarColors.WarmMuted,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(LikhtarColors.GlassActive.copy(alpha = 0.14f)).padding(horizontal = 5.dp, vertical = 1.5.dp),
                    )
                }
            }
        }
    }
}

/** Плитка-перемикач «Усі застосунки» / «Групами» — розміром із плитку застосунку. */
@Composable
private fun LeadTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(150.dp, 84.5.dp).focusGlow(9.dp),
        shape = RoundedCornerShape(9.dp),
        background = LikhtarColors.TileFloor,
        focusedBackground = LikhtarColors.TileFloor,
        borderColor = Color.Transparent,
        focusedScale = LikhtarDimens.CardFocusScale,
        focusLighten = 0f,
    ) { hasFocus ->
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            androidx.tv.material3.Icon(
                androidx.compose.ui.res.painterResource(com.likhtar.home.core.designsystem.R.drawable.ic_solar_widget),
                null,
                tint = LikhtarColors.Glow,
                modifier = Modifier.size(20.dp),
            )
            Text(
                label,
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                color = LikhtarColors.TextPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        FocusRing(hasFocus, 9.dp)
    }
}

private const val ALL_PER_ROW = 5

/** Скільки популярних хмарних ігор у ряду «Ігор»; далі — «Відкрити всі». */
private const val CLOUD_ROW = 10

/** Ряд приставки на «Іграх» — десять образів і «Відкрити всі». */
const val RETRO_ROW = 10

private val HeroHeight = 200.dp
// .scrim: 90deg .92 → .6 на 45 % → 0; 180deg .35 → 0 на 30 %, 0 на 55 % → тло.
private val HeroSideScrim = Brush.horizontalGradient(
    0.00f to LikhtarColors.Background.copy(alpha = 0.92f),
    0.45f to LikhtarColors.Background.copy(alpha = 0.6f),
    1.00f to LikhtarColors.Background.copy(alpha = 0f),
)
private val HeroBottomFade = Brush.verticalGradient(
    0.00f to LikhtarColors.Background.copy(alpha = 0.35f),
    0.30f to LikhtarColors.Background.copy(alpha = 0f),
    0.55f to LikhtarColors.Background.copy(alpha = 0f),
    1.00f to LikhtarColors.Background,
)

/**
 * Меню роботи із застосунком — окремий шар поверх сітки, а не колонка збоку.
 *
 * Ліворуч сам застосунок (іконка, назва, категорія й розмір APK), праворуч дії рядками.
 * Розмір беремо саме APK, а не «застосунок + дані»: останнє вимагає дозволу
 * `PACKAGE_USAGE_STATS`, який користувач мусить видати вручну в системних налаштуваннях,
 * — заради підпису під назвою це забагато.
 */
@Composable
private fun ActionsDialog(
    entry: LauncherAppWithState,
    selected: Int,
    message: String?,
) {
    // Меню не бере фокус і не слухає клавіші саме: усе це робить інтерцептор на корені
    // екрана + `AppsViewModel.moveActions`. Тут лише рендер за `selected`.
    val context = LocalContext.current
    // PackageManager + File.length() — це диск: рахуємо на IO, а не на main у remember.
    val sizeLabel by produceState<String?>(null, entry.app.packageName) {
        value = withContext(Dispatchers.IO) { apkSizeLabel(context, entry.app.packageName) }
    }
    val artwork = rememberAppArtwork(entry.app.packageName, entry.app.activityName)
    val state = entry.state

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LikhtarColors.CardBlack.copy(alpha = 0.93f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .width(640.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(LikhtarColors.Surface)
                .border(1.dp, LikhtarColors.PillBorder, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.width(180.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LikhtarColors.PanelFloor),
                    contentAlignment = Alignment.Center,
                ) {
                    AppMark(entry.app, appBrand(entry.app), artwork, wordmarkSize = 26.sp, iconHeight = 36.dp)
                }
                Text(
                    text = entry.app.label,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = LikhtarColors.GlassActive,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp, bottom = 5.dp),
                )
                Text(
                    text = listOfNotNull(stringResource(entry.effectiveCategory.titleRes), sizeLabel).joinToString(" · "),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 12.sp),
                    color = LikhtarColors.WarmMuted,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppsViewModel.ACTIONS.forEachIndexed { index, action ->
                    val (title, subtitle) = when (action) {
                        AppAction.LAUNCH ->
                            stringResource(R.string.apps_action_open) to stringResource(R.string.apps_action_open_sub)
                        AppAction.PIN_HOME ->
                            (if (state.pinnedToHome) stringResource(R.string.apps_action_unpin_home) else stringResource(R.string.apps_action_pin_home)) to
                                stringResource(R.string.apps_action_pin_home_sub)
                        AppAction.PIN_CINEMA ->
                            (if (state.pinnedToCinema) stringResource(R.string.apps_action_unpin_cinema) else stringResource(R.string.apps_action_pin_cinema)) to
                                stringResource(R.string.apps_action_pin_cinema_sub)
                        AppAction.PIN_LIVE_TV ->
                            (if (state.pinnedToLiveTv) stringResource(R.string.apps_action_unpin_live_tv) else stringResource(R.string.apps_action_pin_live_tv)) to
                                stringResource(R.string.apps_action_pin_live_tv_sub)
                        AppAction.HIDE ->
                            (if (state.hidden) stringResource(R.string.apps_action_show) else stringResource(R.string.apps_action_hide)) to stringResource(R.string.apps_action_hide_sub)
                        AppAction.LOCK ->
                            (if (state.locked) stringResource(R.string.apps_action_unlock) else stringResource(R.string.apps_action_lock)) to
                                stringResource(R.string.apps_action_lock_sub)
                        AppAction.CATEGORY ->
                            stringResource(R.string.apps_category, entry.effectiveCategory.title) to stringResource(R.string.apps_action_category_sub)
                        AppAction.FORCE_STOP ->
                            stringResource(R.string.apps_action_force_stop) to stringResource(R.string.apps_action_force_stop_sub)
                        AppAction.UNINSTALL ->
                            stringResource(R.string.apps_action_uninstall) to (sizeLabel?.let { stringResource(R.string.apps_action_uninstall_free, it) } ?: stringResource(R.string.apps_action_uninstall_sub))
                    }
                    ActionRow(
                        title = title,
                        subtitle = subtitle,
                        selected = index == selected,
                        danger = action == AppAction.UNINSTALL,
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.apps_actions_back_hint),
            style = LikhtarType.MonoSmall,
            color = LikhtarColors.WarmMuted,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = LikhtarDimens.ScreenPadding, bottom = 24.dp)
                .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                .background(LikhtarColors.Surface)
                .border(1.dp, LikhtarColors.PillBorder, RoundedCornerShape(LikhtarDimens.CornerPill))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        message?.let {
            Text(
                text = it,
                style = LikhtarType.Body2.copy(fontWeight = FontWeight.SemiBold),
                color = LikhtarColors.OnGlassActive,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LikhtarColors.GlassActive)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    danger: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) LikhtarColors.GlassActive else LikhtarColors.Panel)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
            color = when {
                selected && danger -> DangerOnLight
                selected -> LikhtarColors.OnGlassActive
                danger -> LikhtarColors.DangerMuted
                else -> LikhtarColors.GlassActive
            },
        )
        Text(
            text = subtitle,
            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp),
            color = if (selected) LikhtarColors.OnGlassActive.copy(alpha = 0.65f) else LikhtarColors.WarmMuted,
        )
    }
}

private fun apkSizeLabel(context: android.content.Context, packageName: String): String? {
    val bytes = runCatching {
        java.io.File(context.packageManager.getApplicationInfo(packageName, 0).sourceDir).length()
    }.getOrDefault(0L)
    if (bytes <= 0L) return null
    val megabytes = bytes / 1024f / 1024f
    return if (megabytes >= 1000f) {
        context.getString(R.string.apps_size_gb, "%.1f".format(megabytes / 1024f))
    } else {
        context.getString(R.string.apps_size_mb, megabytes.toInt().toString())
    }
}

private val SectionTitleInk = LikhtarColors.ChipInk.copy(alpha = 0.62f)
private val SectionCountInk = LikhtarColors.ChipInk.copy(alpha = 0.36f)

private val DialogScrim = LikhtarColors.Background.copy(alpha = 0.95f)
private val Danger = LikhtarColors.DangerSoft
private val DangerOnLight = LikhtarColors.DangerOnPaper

/** Клавіші, які меню дій забирає собі, поки відкрите. */
private val MENU_KEYS = setOf(
    Key.DirectionUp,
    Key.DirectionDown,
    Key.DirectionLeft,
    Key.DirectionRight,
    Key.DirectionCenter,
    Key.Enter,
    Key.NumPadEnter,
    Key.Back,
)

/** Гра для розділу «Ігри»: категорія з урахуванням ручної зміни, приховані — ні. */
private val LauncherAppWithState.isGame: Boolean
    get() = !state.hidden && effectiveCategory == AppCategory.GAMES

/**
 * Гра з образу — коробкою, як на полиці: обкладинка на всю плитку. Без обкладинки (homebrew,
 * свої назви файлів) — плитка в кольорі приставки з назвою.
 */
@Composable
private fun GameTile(game: RetroGame, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var noCover by remember(game.cover) { mutableStateOf(game.cover == null) }
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(GameTileWidth, GameTileHeight).focusGlow(9.dp),
        shape = RoundedCornerShape(7.dp),
        background = LikhtarColors.TileFloor,
        focusedBackground = LikhtarColors.TileFloor,
        borderColor = Color.Transparent,
        focusedScale = 1.08f,
        focusLighten = 0f,
    ) { hasFocus ->
        if (noCover) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(systemColor(game.system), systemColor(game.system).copy(alpha = 0.35f).compositeOverDark())),
                ).padding(8.dp),
            ) {
                Text(
                    text = game.system.title.uppercase(),
                    style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 0.8.sp),
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.TopStart),
                )
                // Назва знаком посередині — як у Кінозалі там, де немає логотипа.
                com.likhtar.home.feature.apps.cloud.GameTitle(
                    gameName(game.title),
                    LikhtarType.HeroTitle.copy(fontSize = 13.sp, lineHeight = 13.5.sp),
                    maxLines = 4,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            AsyncImage(
                model = game.cover,
                contentDescription = gameName(game.title),
                contentScale = ContentScale.Crop,
                onError = { noCover = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        FocusRing(hasFocus, 7.dp)
    }
}

/** Хмарний сервіс — плиткою в кольорі марки; невстановлений позначений «Встановити». */
@Composable
private fun CloudTile(cloud: CloudGame, app: com.likhtar.home.core.model.LauncherApp?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val installed = app != null
    // Встановлений сервіс — його справжній TV-банер, як у «Застосунках»; наша плитка — запасна.
    val artwork = app?.let { rememberAppArtwork(it.packageName, it.activityName) }
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(CloudTileHeight * 16 / 9, CloudTileHeight).focusGlow(9.dp),
        shape = RoundedCornerShape(9.dp),
        background = cloud.color,
        focusedBackground = cloud.color,
        borderColor = Color.Transparent,
        focusedScale = 1.06f,
        focusLighten = 0f,
    ) { hasFocus ->
        if (artwork?.banner == true) {
            Image(artwork.image, contentDescription = cloud.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else Box(
            Modifier.fillMaxSize()
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.35f))))
                .padding(12.dp),
        ) {
            Image(
                painter = painterResource(cloud.icon),
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopStart).size(46.dp).clip(RoundedCornerShape(11.dp)),
            )
            Text(
                text = cloud.title,
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 18.sp, letterSpacing = (-0.2).sp),
                color = Color.White,
                maxLines = 2,
                modifier = Modifier.align(Alignment.BottomStart),
            )
            if (!installed) {
                Text(
                    text = stringResource(R.string.apps_cloud_install_tag),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 8.sp),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 5.dp, vertical = 1.5.dp),
                )
            }
        }
        FocusRing(hasFocus, 9.dp)
    }
}

/** «Tetris (World) (Rev 1)» → «Tetris», «Legend of Zelda, The» → «The Legend of Zelda». */
private fun gameName(title: String): String {
    val bare = title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim().ifEmpty { title }
    return Regex("""^(.*), (The|A|An)(\b.*)?$""").matchEntire(bare)?.let { m -> "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}" } ?: bare
}

/** Колір приставки — для плиток без обкладинки й шапки без кадру. */
private fun systemColor(system: com.likhtar.home.core.data.RetroSystem): Color = when (system) {
    com.likhtar.home.core.data.RetroSystem.GB -> CloudColors.RetroGb
    com.likhtar.home.core.data.RetroSystem.GBC -> CloudColors.RetroGbc
    com.likhtar.home.core.data.RetroSystem.GBA -> CloudColors.RetroGba
    com.likhtar.home.core.data.RetroSystem.NES, com.likhtar.home.core.data.RetroSystem.N64 -> CloudColors.RetroNintendo
    com.likhtar.home.core.data.RetroSystem.SNES -> CloudColors.RetroSnes
    com.likhtar.home.core.data.RetroSystem.MEGA_DRIVE, com.likhtar.home.core.data.RetroSystem.MASTER_SYSTEM,
    com.likhtar.home.core.data.RetroSystem.GAME_GEAR -> CloudColors.RetroGameGear
    com.likhtar.home.core.data.RetroSystem.PC_ENGINE -> CloudColors.RetroPcEngine
    com.likhtar.home.core.data.RetroSystem.PLAYSTATION, com.likhtar.home.core.data.RetroSystem.PSP -> CloudColors.RetroPlayStation
}

private fun Color.compositeOverDark(): Color = compositeOver(LikhtarColors.Background)

/**
 * Шапка гри у фокусі: кадр із самої гри праворуч, пікселі без згладжування; поверх нього —
 * коробка. Немає кадру — тло в кольорі приставки.
 */
@Composable
private fun GameHero(game: RetroGame) {
    Box(modifier = Modifier.fillMaxWidth().height(HeroHeight + 30.dp).background(LikhtarColors.Background)) {
        Box(
            Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.62f)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, systemColor(game.system).copy(alpha = 0.5f)))),
        ) {
            game.snap?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Лівий край кадру тане в тло, а не обривається.
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to LikhtarColors.Background, 0.45f to Color.Transparent)))
        }
        Box(modifier = Modifier.fillMaxSize().background(HeroSideScrim))
        Box(modifier = Modifier.fillMaxSize().background(HeroBottomFade))
        game.cover?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = LikhtarDimens.ScreenPadding, bottom = 36.dp).size(110.dp, 150.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = LikhtarDimens.ScreenPadding, bottom = 44.dp)
                .width(560.dp),
        ) {
            Text(
                text = game.system.title.uppercase(),
                style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 1.2.sp),
                color = LikhtarColors.Glow,
            )
            Text(
                text = gameName(game.title),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.apps_games_hero_hint),
                style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp),
                color = LikhtarColors.TextPrimary.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Шапка хмарного сервісу: його колір, назва великим і що він дає. */
@Composable
private fun CloudHero(cloud: CloudGame, installed: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().height(HeroHeight + 30.dp)
            .background(Brush.horizontalGradient(listOf(LikhtarColors.Background, LikhtarColors.Background, cloud.color.copy(alpha = 0.55f)))),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(HeroBottomFade))
        Image(
            painter = painterResource(cloud.icon),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 150.dp, top = 10.dp).size(150.dp).clip(RoundedCornerShape(34.dp)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = LikhtarDimens.ScreenPadding, bottom = 44.dp)
                .width(600.dp),
        ) {
            Text(
                text = stringResource(R.string.apps_games_cloud).uppercase(),
                style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 1.2.sp),
                color = LikhtarColors.Glow,
            )
            Text(
                text = cloud.title,
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp),
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(cloud.noteRes),
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 14.sp),
                color = LikhtarColors.TextPrimary,
                modifier = Modifier.padding(top = 7.dp),
            )
            Text(
                text = stringResource(if (installed) R.string.apps_cloud_open else R.string.apps_cloud_get),
                style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp),
                color = LikhtarColors.TextPrimary.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private val GameTileHeight = 149.dp
private val CloudTileHeight = 112.dp
private const val ONE_ROW_ROMS = 12
private val GameTileWidth = 109.dp
