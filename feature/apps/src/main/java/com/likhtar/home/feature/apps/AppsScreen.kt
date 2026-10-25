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
 */
@Composable
fun AppsScreen(
    onSelectSection: (LikhtarSection) -> Unit,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val menuFocusRequester = rememberInitialFocusRequester(state.apps.isEmpty())
    val firstAppFocusRequester = rememberInitialFocusRequester(state.apps.isNotEmpty())

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
            section = LikhtarSection.APPS,
            onSelectSection = onSelectSection,
            menuFocusRequester = menuFocusRequester,
            contentFocusRequester = firstAppFocusRequester,
        ) {
            AppsPage(
                state = state,
                firstAppFocusRequester = firstAppFocusRequester,
                menuFocusRequester = menuFocusRequester,
                onFocused = viewModel::onAppFocused,
                onClick = viewModel::launch,
                onLongClick = { viewModel.openActions(it.app.id) },
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
    firstAppFocusRequester: FocusRequester,
    menuFocusRequester: FocusRequester,
    onFocused: (String) -> Unit,
    onClick: (LauncherAppWithState) -> Unit,
    onLongClick: (LauncherAppWithState) -> Unit,
) {
    // «Усі застосунки»: той самий екран, але без груп — усе встановлене за абеткою (і приховане,
    // притемнене), рядами по шість. BACK — назад до груп.
    var showAll by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showAll) { showAll = false }
    val allTitle = stringResource(R.string.apps_all)
    val rows = if (showAll) {
        state.apps.sortedBy { it.app.label.lowercase() }.chunked(ALL_PER_ROW).mapIndexed { i, chunk ->
            AppSection(if (i == 0) allTitle else "", chunk, count = state.apps.size)
        }
    } else buildList {
        if (state.recent.isNotEmpty()) add(AppSection(stringResource(R.string.apps_section_recent), state.recent))
        addAll(state.sections)
    }
    val lead = if (showAll) stringResource(R.string.apps_by_groups) to { showAll = false }
    else allTitle to { showAll = true }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
        state.selected?.let { AppsHero(entry = it, hero = state.hero) }
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
                                onFocused(id)
                                scope.launch { list.centerItem(rowIndex, topUntil = 0) }
                            },
                            onClick = onClick,
                            onLongClick = onLongClick,
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
