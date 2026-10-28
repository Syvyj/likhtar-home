package com.likhtar.home.feature.apps

import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hasApps = if (games) state.apps.any { it.isGame } || state.roms.isNotEmpty() else state.apps.isNotEmpty()
    // Образи перечитуємо щоразу, як розділ на екрані: щойно скопійовані й дозвіл, даний у системі.
    if (games) androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshRoms()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    val menuFocusRequester = rememberInitialFocusRequester(!hasApps)
    val firstAppFocusRequester = rememberInitialFocusRequester(hasApps)

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
            )
        }

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
/** Заголовок рядка: назва, тонка лінійка на всю ширину й лічильник праворуч. */
@Composable
private fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    // .sec h3 прототипу: 26 px / 600 приглушено, лінія, «N шт.» моно 20 px.
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            color = SectionTitleInk,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0f)),
                    ),
                ),
        )
        Text(
            text = pluralStringResource(R.plurals.apps_count, count, count),
            style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 10.sp),
            color = SectionCountInk,
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
) {
    // «Усі застосунки»: той самий екран, але без груп — усе встановлене за абеткою (і приховане,
    // притемнене), рядами по шість. BACK — назад до груп.
    var showAll by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showAll) { showAll = false }
    val allTitle = stringResource(R.string.apps_all)
    val rows = if (games) buildList {
        // Ігрова кімната, а не бібліотека: що грали — згори, далі хмара, образи за приставками,
        // а встановлені ігри й емулятори (нещодавні першими) — унизу.
        if (state.recentRoms.isNotEmpty()) add(AppSection(stringResource(R.string.apps_games_continue), emptyList(), state.recentRoms))
        add(AppSection(stringResource(R.string.apps_games_cloud), emptyList(), clouds = CloudGame.entries))
        // Кілька образів — одним рядом; велика колекція — рядами за приставками.
        if (state.roms.size <= ONE_ROW_ROMS) {
            if (state.roms.isNotEmpty()) add(AppSection(stringResource(R.string.apps_games_retro), emptyList(), state.roms))
        } else {
            state.roms.groupBy { it.system }.forEach { (system, list) -> add(AppSection(system.title, emptyList(), list)) }
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
    val lead = if (games) (stringResource(R.string.apps_games_file_access) to onFileAccess).takeIf { !state.romsReadable }
    else if (showAll) stringResource(R.string.apps_by_groups) to { showAll = false }
    else allTitle to { showAll = true }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Гра у фокусі — шапка про неї; фокус на застосунку повертає шапку застосунку.
    var focusedGame by remember { mutableStateOf<RetroGame?>(null) }
    var focusedCloud by remember { mutableStateOf<CloudGame?>(null) }
    val installedPackages = remember(state.apps) { state.apps.map { it.app.packageName }.toSet() }
    // Вхід кожного ряду — його перша плитка. ↑/↓ ведемо самі: стандартний пошук фокуса
    // тут водив ↓ уздовж ряду.
    val entries = remember(rows.size) { List(rows.size) { i -> if (i == 0) firstAppFocusRequester else FocusRequester() } }
    fun go(to: Int): Boolean {
        if (to < 0) return runCatching { menuFocusRequester.requestFocus() }.isSuccess
        if (to > rows.lastIndex) return true
        scope.launch {
            list.centerItem(to, topUntil = 0)
            withFrameNanos { }
            runCatching { entries[to].requestFocus() }
        }
        return true
    }
    Box(modifier = Modifier.fillMaxSize().background(LikhtarColors.Background)) {
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
        when {
            focusedGame != null -> GameHero(focusedGame!!)
            focusedCloud != null -> CloudHero(focusedCloud!!, installed = focusedCloud!!.packages.any { it in installedPackages })
            else -> state.selected?.let { AppsHero(entry = it, hero = state.hero) }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(HeroHeight))
            // Колонка сама фокус не доганяє — ряд у фокусі ставить centerItem, як у Кінозалі.
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoView) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().clipToBounds().padding(horizontal = LikhtarDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 60.dp),
                ) {
                    itemsIndexed(rows, key = { i, row -> "$showAll-$i-${row.title}" }) { rowIndex, row ->
                        AppsRail(
                            row = row,
                            lead = lead.takeIf { rowIndex == 0 },
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
                                focusedGame = game
                                focusedCloud = null
                                scope.launch { list.centerItem(rowIndex, topUntil = 0) }
                            },
                            onGameClick = onGameClick,
                            installedPackages = installedPackages,
                            onCloudFocused = { cloud ->
                                focusedCloud = cloud
                                focusedGame = null
                                scope.launch { list.centerItem(rowIndex, topUntil = 0) }
                            },
                            onCloudClick = onCloudClick,
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
    onCloudFocused: (CloudGame) -> Unit = {},
    onCloudClick: (CloudGame) -> Unit = {},
) {
    // Вхід у ряд — на першу плитку, як у рядах Кінозалу.
    // Ряд, з якого пішли, — знову на початок: інакше перша плитка вже не скомпонована, і
    // вхід у ряд (↓ з меню, ↑/↓ з сусіднього) мовчки не спрацьовував — як у Кінозалі.
    val railState = rememberLazyListState()
    var reset by remember { mutableStateOf(false) }
    LaunchedEffect(reset) {
        if (reset) {
            railState.scrollToItem(0)
            reset = false
        }
    }
    Column(
        modifier = Modifier
            .onFocusChanged { if (!it.hasFocus) reset = true }
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
                    LeadTile(label, onLead, Modifier.padding(horizontal = 6.dp, vertical = 6.dp).focusRequester(enter))
                }
            }
            itemsIndexed(row.clouds, key = { _, cloud -> row.title + cloud.name }) { index, cloud ->
                CloudTile(
                    cloud = cloud,
                    installed = cloud.packages.any { it in installedPackages },
                    onClick = { onCloudClick(cloud) },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .onFocusChanged { if (it.isFocused) onCloudFocused(cloud) }
                        .then(if (index == 0 && lead == null) Modifier.focusRequester(enter) else Modifier),
                )
            }
            itemsIndexed(row.games, key = { _, game -> row.title + game.path }) { index, game ->
                GameTile(
                    game = game,
                    onClick = { onGameClick(game) },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .onFocusChanged { if (it.isFocused) onGameFocused(game) }
                        .then(if (index == 0 && lead == null) Modifier.focusRequester(enter) else Modifier),
                )
            }
            itemsIndexed(row.apps, key = { _, entry -> row.title + entry.app.id }) { index, entry ->
                ProtoAppTile(
                    app = entry.app,
                    locked = entry.state.locked,
                    dimmed = entry.state.hidden,
                    tags = buildList {
                        if (entry.state.pinnedToCinema) add(stringResource(R.string.apps_tag_cinema))
                        if (entry.state.pinnedToLiveTv) add(stringResource(R.string.apps_tag_live_tv))
                    },
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .onFocusChanged { if (it.isFocused) onFocused(entry.app.id) }
                        .then(if (index == 0 && lead == null) Modifier.focusRequester(enter) else Modifier),
                )
            }
        }
    }
}

/**
 * Шапка (.hero прототипу) — вікно застосунку у фокусі: у сервісу Кінозалу — кадр його
 * хіта праворуч, у решти — його знак великим на темній плитці, а шапка ледь тоне в кольорі марки.
 * Назва — знаком марки її кольором; під нею категорія, хіт і підказка.
 */
@Composable
private fun AppsHero(entry: LauncherAppWithState, hero: AppHero) {
    val artwork = rememberAppArtwork(entry.app.packageName, entry.app.activityName)
    val brand = appBrand(entry.app)
    val tone = brand?.color ?: artwork?.backdrop ?: Color(entry.app.accent)
    // color-mix(тон 34 %, тло) — перехід кольору шапки за .45s (єдиний рух сторінки).
    val bg by androidx.compose.animation.animateColorAsState(
        if (hero.backdrop != null) LikhtarColors.Background else androidx.compose.ui.graphics.lerp(LikhtarColors.Background, tone, 0.34f),
        androidx.compose.animation.core.tween(450),
        label = "heroTone",
    )
    Box(modifier = Modifier.fillMaxWidth().height(HeroHeight + 30.dp).background(bg)) {
        if (hero.backdrop != null) {
            AsyncImage(
                model = hero.backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.TopEnd).width(620.dp).fillMaxHeight(),
            )
        } else {
            // .mark: 532×300 px, 180 px від правого краю, 170 від верху, темна плитка.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 90.dp, top = 30.dp)
                    .size(266.dp, 150.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LikhtarColors.TileFloor),
                contentAlignment = Alignment.Center,
            ) {
                AppMark(entry.app, brand, artwork, wordmarkSize = 48.sp, iconHeight = 58.dp)
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(HeroSideScrim))
        Box(modifier = Modifier.fillMaxSize().background(HeroBottomFade))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = LikhtarDimens.ScreenPadding, bottom = 44.dp)
                .width(500.dp),
        ) {
            Text(
                text = brand?.wordmark ?: entry.app.label,
                style = (brand?.style ?: TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold))
                    .copy(fontSize = 38.sp, lineHeight = 38.sp),
                color = brand?.color ?: Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    stringResource(entry.effectiveCategory.titleRes),
                    stringResource(R.string.apps_state_locked).takeIf { entry.state.locked },
                    stringResource(R.string.apps_state_hidden).takeIf { entry.state.hidden },
                ).joinToString(" · "),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                color = LikhtarColors.TextDim,
                modifier = Modifier.padding(top = 7.dp),
            )
            hero.note?.let {
                Text(
                    text = it,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 14.sp),
                    color = LikhtarColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            Text(
                text = stringResource(R.string.apps_hero_hint),
                style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 10.sp),
                color = LikhtarColors.TextPrimary.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 6.dp),
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
 * Плитка (.tile прототипу): 300×169 px, темна, знак марки 44 px чи іконка з назвою; замок —
 * бурштином угорі праворуч, мітки «Кінозал / ТВ Ефір» — унизу; приховані — .42.
 * Фокус — 1.07 з кільцем і теплою тінню.
 */
@Composable
private fun ProtoAppTile(
    app: LauncherApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
            .size(150.dp, 84.5.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { alpha = if (dimmed && !focused) 0.42f else 1f }
            .focusGlow(9.dp),
        shape = RoundedCornerShape(9.dp),
        background = LikhtarColors.TileFloor,
        focusedBackground = LikhtarColors.TileFloor,
        borderColor = Color.Transparent,
        focusedScale = 1.07f,
        focusLighten = 0f,
    ) { hasFocus ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppMark(app, brand, artwork, wordmarkSize = 22.sp, iconHeight = 27.dp)
        }
        if (locked) {
            androidx.tv.material3.Icon(
                androidx.compose.ui.res.painterResource(com.likhtar.home.core.designsystem.R.drawable.ic_solar_lock),
                null,
                tint = LikhtarColors.Glow,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(13.dp),
            )
        }
        if (tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)) {
                tags.forEach {
                    Text(
                        it,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 7.5.sp),
                        color = LikhtarColors.TextDim,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(LikhtarColors.Paper.copy(alpha = 0.12f)).padding(horizontal = 5.dp, vertical = 1.5.dp),
                    )
                }
            }
        }
        FocusRing(hasFocus, 9.dp)
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
        focusedScale = 1.07f,
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

private const val ALL_PER_ROW = 6

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
            .background(DialogScrim),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // .who: знак 380×214 px, назва 44 px / 700, категорія 24 px.
            Column(
                modifier = Modifier.width(190.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Box(
                    modifier = Modifier
                        .size(190.dp, 107.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LikhtarColors.TileFloor),
                    contentAlignment = Alignment.Center,
                ) {
                    AppMark(entry.app, appBrand(entry.app), artwork, wordmarkSize = 30.sp, iconHeight = 40.dp)
                }
                Text(
                    text = entry.app.label,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp, bottom = 3.dp),
                )
                Text(
                    text = listOfNotNull(stringResource(entry.effectiveCategory.titleRes), sizeLabel).joinToString(" · "),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 12.sp),
                    color = LikhtarColors.TextDim,
                )
            }

            // .list: 760 px, поле 14 px, скруглення 30 px, тло .9 з тонкою рамкою.
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(LikhtarColors.Surface.copy(alpha = 0.9f))
                    .border(0.75.dp, LikhtarColors.Faint, RoundedCornerShape(15.dp))
                    .padding(7.dp),
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
            color = LikhtarColors.TextTertiary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = LikhtarDimens.ScreenPadding, bottom = 24.dp)
                .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                .background(LikhtarColors.Glass)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )

        message?.let {
            Text(
                text = it,
                style = LikhtarType.Body2,
                color = LikhtarColors.TextSecondary,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
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
        // .act: поле 14×24 px, скруглення 20 px; вибраний — тепла біла.
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) LikhtarColors.Paper else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
            color = when {
                selected && danger -> DangerOnLight
                selected -> LikhtarColors.Background
                danger -> Danger
                else -> LikhtarColors.TextPrimary
            },
        )
        Text(
            text = subtitle,
            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 10.sp),
            color = if (selected) LikhtarColors.Background.copy(alpha = 0.62f) else LikhtarColors.TextDim,
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
                Text(
                    text = gameName(game.title),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 15.sp),
                    color = Color.White,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart),
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
private fun CloudTile(cloud: CloudGame, installed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        Box(
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
    com.likhtar.home.core.data.RetroSystem.GB -> Color(0xFF5E7A1F)
    com.likhtar.home.core.data.RetroSystem.GBC -> Color(0xFF6B3FA0)
    com.likhtar.home.core.data.RetroSystem.GBA -> Color(0xFF3F3FA8)
    com.likhtar.home.core.data.RetroSystem.NES, com.likhtar.home.core.data.RetroSystem.N64 -> Color(0xFFB3282D)
    com.likhtar.home.core.data.RetroSystem.SNES -> Color(0xFF5B4FA6)
    com.likhtar.home.core.data.RetroSystem.MEGA_DRIVE, com.likhtar.home.core.data.RetroSystem.MASTER_SYSTEM,
    com.likhtar.home.core.data.RetroSystem.GAME_GEAR -> Color(0xFF1F4FA8)
    com.likhtar.home.core.data.RetroSystem.PC_ENGINE -> Color(0xFFC0602A)
    com.likhtar.home.core.data.RetroSystem.PLAYSTATION, com.likhtar.home.core.data.RetroSystem.PSP -> Color(0xFF3A4A66)
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
                style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp),
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
                style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 10.sp),
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
                style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp),
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
                style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 10.sp),
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
