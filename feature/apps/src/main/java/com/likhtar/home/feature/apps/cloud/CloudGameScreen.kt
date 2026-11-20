package com.likhtar.home.feature.apps.cloud


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import com.likhtar.home.core.designsystem.requestWithRetry
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.data.cloud.CloudGame
import com.likhtar.home.core.data.cloud.CloudLaunch
import com.likhtar.home.core.data.cloud.CloudService
import com.likhtar.home.core.data.cloud.mainLaunch
import com.likhtar.home.core.data.cloud.mainService
import com.likhtar.home.core.data.cloud.services
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarIcons
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.requestAfterFrame
import com.likhtar.home.feature.apps.R

private val Bg = LikhtarColors.FloorLift

/**
 * Картка гри (макет: #game): кадр на тлі, жанр і рік, назва, плашки, розробник і геймпад, опис;
 * кнопки — як на сторінці фільму в Кінозалі: «Грати» з ліхтарем у головному сервісі, далі решта
 * варіантів, «↗ У браузері». Нижче — скриншоти (OK — на весь екран) і «Ще з жанру».
 * ↑ з кнопок — трейлер в офіційному YouTube.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CloudGameScreen(
    gameKey: String,
    onOpenGame: (String) -> Unit,
    viewModel: CloudGamesViewModel = hiltViewModel(),
) {
    val catalog by viewModel.catalog.collectAsState()
    val library by viewModel.library.collectAsState()
    val all by viewModel.games.collectAsState()
    val game = remember(all, gameKey) { all.firstOrNull { it.key == gameKey } } ?: run {
        Box(Modifier.fillMaxSize().background(Bg))
        return
    }
    val more = remember(all, game) {
        val genre = game.genres.firstOrNull()
        if (genre == null) emptyList() else all.filter { it !== game && it.genres.firstOrNull() == genre }.sortedByDescending { it.popularity }.take(10)
    }
    var shot by remember { mutableStateOf<Int?>(null) }
    // Закрили скриншот — фокус на його мініатюру (на ту, до якої догортали), а не на «Грати».
    var shotBack by remember { mutableStateOf<Int?>(null) }
    val shotFocus = remember { FocusRequester() }
    LaunchedEffect(shotBack) { if (shotBack != null) shotFocus.requestWithRetry() }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(game.key) { runCatching { playFocus.requestAfterFrame() } }
    val focusManager = LocalFocusManager.current
    // Прокрутка зонами, як у макеті: кнопки — шапка цілком угорі; скриншоти — під'їжджають знизу;
    // «Ще з жанру» — до кінця. Без цього колонка сама доганяла фокус і ховала назву гри за край.
    val scroll = rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var shotsBottom by remember { mutableStateOf(0) }
    fun scrollTo(y: Int) { scope.launch { scroll.animateScrollTo(y.coerceIn(0, scroll.maxValue)) } }

    Box(Modifier.fillMaxSize().background(Bg)) {
        FallbackImage(listOfNotNull(game.hero, game.cover, game.cover2).distinct(), null, Modifier.fillMaxSize(), Alignment.TopCenter)
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Bg, 0.38f to Bg.copy(alpha = 0.92f), 0.7f to Bg.copy(alpha = 0.35f), 1f to Bg.copy(alpha = 0.6f)),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Bg.copy(alpha = 0f), 0.92f to Bg)))
        if (game.trailer.isNotEmpty()) {
            Text(
                stringResource(R.string.cloud_trailer),
                style = TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.5.sp),
                color = LikhtarColors.TextPrimary.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 17.dp),
            )
        }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides com.likhtar.home.core.designsystem.NoBringIntoView) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = LikhtarDimens.ScreenPadding),
        ) {
            Spacer(Modifier.height(46.dp))
            Column(Modifier.widthIn(max = 540.dp)) {
                val genreLine = (game.genres.map { genreLabel(it) } + listOfNotNull(game.year.take(4).ifEmpty { null })).joinToString(" · ")
                Text(genreLine.ifEmpty { stringResource(R.string.cloud_game) }.uppercase(), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp, letterSpacing = 1.5.sp), color = LikhtarColors.WarmGold)
                GameTitle(
                    game.title,
                    LikhtarType.HeroTitle.copy(fontSize = if (game.title.length > 36) 26.sp else 32.sp, lineHeight = if (game.title.length > 36) 27.sp else 33.sp),
                    maxLines = 3,
                    modifier = Modifier.padding(top = 7.dp, bottom = 11.dp),
                )
                CloudChips(game, services = library.services)
                val pad = when (game.pad) {
                    "full" -> stringResource(R.string.cloud_pad_full)
                    "part" -> stringResource(R.string.cloud_pad_part)
                    "none" -> stringResource(R.string.cloud_pad_none)
                    else -> null
                }
                val meta = listOfNotNull(game.dev.ifEmpty { null }, game.pub.takeIf { it.isNotEmpty() && it != game.dev })
                Row(Modifier.padding(top = 8.dp, bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = LikhtarColors.WarmSand)
                    pad?.let { Text(it, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = if (game.pad == "none") LikhtarColors.WarmGold else LikhtarColors.WarmSand) }
                }
                val desc = game.desc.ifEmpty { game.rom?.let { stringResource(R.string.cloud_retro_desc, it.system) }.orEmpty() }
                if (desc.isNotEmpty()) {
                    Text(desc, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 12.5.sp, lineHeight = 18.sp), color = LikhtarColors.WarmInkBright, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 490.dp).padding(bottom = 17.dp))
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // HeroActions / .gBtns.wide: у два ряди колонки не влазить — кнопки на всю ширину.
                modifier = Modifier.widthIn(max = if (buttonCount(game, library.services) > 6) 864.dp else 500.dp).onFocusChanged { if (it.hasFocus) scrollTo(0) }.onPreviewKeyEvent { e ->
                    // Вище кнопок нічого — трейлер, як на сторінці фільму в Кінозалі.
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp && game.trailer.isNotEmpty() && !focusManager.moveFocus(FocusDirection.Up)) {
                        viewModel.openTrailer(game)
                        true
                    } else {
                        false
                    }
                },
            ) {
                val rom = game.rom
                if (rom != null) {
                    // Ретро: одна кнопка — RetroArch з потрібним ядром.
                    LikhtarButton(
                        height = 38.dp,
                        label = stringResource(R.string.cloud_play),
                        onClick = { viewModel.play(game) },
                        enabled = !rom.missing,
                        modifier = Modifier.focusRequester(playFocus),
                        leading = { _, onLight -> LikhtarIcons.Lantern(size = 13.dp, onLight = onLight) },
                        trailing = {
                            Text(if (rom.missing) stringResource(R.string.cloud_no_file) else "RetroArch · ${rom.system}", style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = LikhtarColors.WarmSubtle, maxLines = 1)
                        },
                    )
                } else {
                    PlayButtons(game, playFocus, library.services, onPlay = { s, l -> viewModel.play(game, s, l) })
                }
                // Списки й «У мене є» — кнопками, нічого не ховаємо в меню, поки є місце (макет: .gb ⊕/✓).
                ListButton(stringResource(R.string.cloud_now), game.key in library.playing) { viewModel.updateLibrary { it.togglePlaying(game.key) } }
                ListButton(stringResource(R.string.cloud_favorites), game.key in library.favorites) { viewModel.updateLibrary { it.toggleFavorite(game.key) } }
                if (!game.free && game.rom == null) ListButton(stringResource(R.string.cloud_owned), game.key in library.owned) { viewModel.updateLibrary { it.toggleOwned(game.key) } }
                if (game.rom == null) LikhtarButton(
                    height = 38.dp,
                    label = stringResource(R.string.cloud_browser),
                    onClick = { viewModel.openWeb(game) },
                    leading = { color, _ -> Text("↗", style = TextStyle(fontSize = 11.5.sp), color = color) },
                )
            }
            if (game.shots.isNotEmpty()) {
                LikhtarRail(
                    modifier = Modifier.padding(top = 22.dp)
                        .onGloballyPositioned { shotsBottom = (it.positionInParent().y + it.size.height).toInt() }
                        .onFocusChanged { if (it.hasFocus) scrollTo(shotsBottom - scroll.viewportSize + 40) },
                    gap = 12.dp,
                ) {
                    itemsIndexed(game.shots, key = { i, _ -> i }) { i, url ->
                        FocusableSurface(
                            onClick = { shot = i },
                            modifier = Modifier.size(210.dp, 118.dp).then(if (i == shotBack) Modifier.focusRequester(shotFocus) else Modifier),
                            shape = RoundedCornerShape(7.dp),
                            background = LikhtarColors.Surface,
                            focusedBackground = LikhtarColors.Surface,
                            borderColor = Color.Transparent,
                            focusedScale = 1.06f,
                        ) { _ -> AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
            if (more.isNotEmpty()) {
                Text(
                    stringResource(R.string.cloud_more_genre, genreLabel(game.genres.first())),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 15.sp),
                    color = LikhtarColors.WarmInkBright,
                    modifier = Modifier.padding(top = 30.dp, bottom = 2.dp),
                )
                LikhtarRail(modifier = Modifier.onFocusChanged { if (it.hasFocus) scrollTo(scroll.maxValue) }, gap = 15.dp) {
                    itemsIndexed(more, key = { _, g -> g.key }) { _, g -> CloudCard(g, CardShape.POSTER, { onOpenGame(g.key) }) }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
        }
        shot?.let { i -> ShotViewer(game.shots, i, onIndex = { shot = it }, onClose = { shotBack = i; shot = null }) }
    }
}

/** «Грати» (головний сервіс, зі знаком ліхтаря) і решта варіантів: назва сервісу в кольорі марки + мітки. */
@Composable
private fun ListButton(label: String, on: Boolean, onClick: () -> Unit) {
    LikhtarButton(
        height = 38.dp,
        label = label,
        onClick = onClick,
        leading = { color, _ -> Text(if (on) "✓" else "⊕", style = TextStyle(fontSize = 11.5.sp), color = color) },
    )
}

@Composable
private fun PlayButtons(game: CloudGame, playFocus: FocusRequester, services: Set<CloudService>, onPlay: (CloudService, CloudLaunch) -> Unit) {
    val main = game.mainService(services)
    val mainLaunch = main?.let(game::mainLaunch)
    val installs = stringResource(R.string.cloud_installs)
    fun badges(l: CloudLaunch) = listOfNotNull(l.store.ifEmpty { null }, installs.takeIf { l.install })
    if (main != null && mainLaunch != null) {
        LikhtarButton(
        height = 38.dp,
            label = stringResource(R.string.cloud_play),
            onClick = { onPlay(main, mainLaunch) },
            // Як на сторінці фільму в Кінозалі: світла лише кнопка у фокусі; головну видно з ліхтаря.
            modifier = Modifier.focusRequester(playFocus),
            leading = { _, onLight -> LikhtarIcons.Lantern(size = 13.dp, onLight = onLight) },
            trailing = {
                Text(
                    (listOf(serviceName(main)) + badges(mainLaunch)).joinToString(" · "),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp),
                    color = LikhtarColors.WarmSubtle,
                    maxLines = 1,
                )
            },
        )
    }
    game.services(services).forEach { s ->
        game.sources[s].orEmpty().forEach { l ->
            if (s == main && l == mainLaunch) return@forEach
            LikhtarButton(
        height = 38.dp,
                label = serviceName(s),
                onClick = { onPlay(s, l) },
                brandColor = brandOnDark(s),
                leading = { color, _ -> Text("▷", style = TextStyle(fontSize = 11.5.sp), color = color) },
                trailing = {
                    badges(l).forEach { b ->
                        Text(
                            b,
                            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 8.sp),
                            color = LikhtarColors.WarmInkBright,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(LikhtarColors.SurfaceElevated).padding(horizontal = 4.5.dp, vertical = 1.5.dp),
                        )
                    }
                },
            )
        }
    }
}

/** Скриншот на весь екран: ← → гортати, Назад чи OK — закрити (макет: #shot). */
@Composable
private fun ShotViewer(shots: List<String>, index: Int, onIndex: (Int) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestAfterFrame() } }
    BackHandler(onBack = onClose)
    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (e.key) {
                Key.DirectionRight -> { onIndex((index + 1) % shots.size); true }
                Key.DirectionLeft -> { onIndex((index - 1 + shots.size) % shots.size); true }
                Key.DirectionCenter, Key.Enter -> { onClose(); true }
                // ↑↓ не випускають фокус під переглядач; решту (Back!) — далі: `true` тут ковтав
                // Back до BackHandler, і зі скриншота не було виходу.
                Key.DirectionUp, Key.DirectionDown -> true
                else -> false
            }
        },
    ) {
        AsyncImage(model = shots[index], contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Text(
            stringResource(R.string.cloud_shot_count, index + 1, shots.size),
            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp),
            color = LikhtarColors.WarmInkBright,
            modifier = Modifier.align(Alignment.BottomEnd).padding(30.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Скільки кнопок під описом: варіанти запуску, списки, «У мене є», «У браузері». */
private fun buttonCount(game: CloudGame, services: Set<CloudService>): Int =
    game.services(services).sumOf { game.sources[it].orEmpty().size } + 2 + (if (game.free || game.rom != null) 0 else 1) + (if (game.rom != null) 1 else 1)
