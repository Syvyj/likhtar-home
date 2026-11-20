package com.likhtar.home.feature.apps.cloud


import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.data.cloud.CloudGame
import com.likhtar.home.core.data.cloud.CloudLibrary
import com.likhtar.home.core.data.cloud.CloudService
import com.likhtar.home.core.data.cloud.mainService
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.requestAfterFrame
import com.likhtar.home.feature.apps.R

/* Головна «Ігор» за макетом (#home у docs/prototypes/cloud-games-mock.html; 1 dp = 2 px). */

internal val MockBg = LikhtarColors.FloorLift
internal val MockFocus = LikhtarColors.WarmCream
internal val MockInk = LikhtarColors.OnGlassActive
internal val MockDim = LikhtarColors.WarmMuted
internal val MockAmber = LikhtarColors.WarmGold

/** Що в фокусі на головній «Ігор» — від цього герой і підказка. */
sealed interface CloudHomeFocus {
    /** [play] — плитка образу в ряду приставки: OK там одразу запускає гру, а не відкриває картку. */
    data class Game(val game: CloudGame, val play: Boolean = false) : CloudHomeFocus
    data class List(val name: String, val count: Int, val openAll: Boolean) : CloudHomeFocus
    data object Actions : CloudHomeFocus
}

/** #bg: кадр гри на 760 px від верху екрана, тане вбік і донизу. Без гри — нічого. */
@Composable
fun CloudHomeBackdrop(game: CloudGame?) {
    if (game == null) return
    Box(Modifier.fillMaxWidth().height(380.dp)) {
        FallbackImage(listOfNotNull(game.hero, game.cover, game.cover2).distinct(), null, Modifier.fillMaxSize(), androidx.compose.ui.BiasAlignment(0f, -0.6f))
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to MockBg, 0.35f to MockBg.copy(alpha = 0.88f), 0.7f to MockBg.copy(alpha = 0.25f), 1f to MockBg.copy(alpha = 0.55f)),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to MockBg.copy(alpha = 0f), 0.98f to MockBg)))
    }
}

/**
 * Герой (#hero: 140 px від верху) і дії (#actions: 520 px) над рядами (#rows: 610 px). [contentTop] —
 * де під меню починається вміст розділу: координати макета рахуються від верху екрана.
 */
@Composable
fun CloudHomeHeader(
    focus: CloudHomeFocus,
    total: Int,
    contentTop: Dp,
    actionsFocus: FocusRequester,
    onSearch: () -> Unit,
    onAll: () -> Unit,
    onServices: () -> Unit,
    onDownFromActions: () -> Boolean,
    library: CloudLibrary,
) {
    Box(Modifier.fillMaxWidth().height((305.dp - contentTop).coerceAtLeast(120.dp))) {
        Column(Modifier.offset(y = (70.dp - contentTop).coerceAtLeast(0.dp)).padding(start = LikhtarDimens.ScreenPadding).width(525.dp)) {
            val game = (focus as? CloudHomeFocus.Game)?.game
            Text(
                (game?.genres?.firstOrNull()?.let { genreLabel(it) } ?: stringResource(R.string.cloud_row)).uppercase(),
                style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp, letterSpacing = 1.5.sp),
                color = MockAmber,
                modifier = Modifier.heightIn(min = 14.dp),
            )
            GameTitle(
                game?.title ?: (focus as? CloudHomeFocus.List)?.name ?: stringResource(R.string.cloud_library),
                LikhtarType.HeroTitle,
                modifier = Modifier.padding(top = 7.dp, bottom = 11.dp),
            )
            if (game != null) CloudChips(game, Modifier.padding(bottom = 8.dp), library.services)
            val hint = when (focus) {
                is CloudHomeFocus.Game -> stringResource(if (focus.play) R.string.cloud_hint_play else R.string.cloud_hint_game)
                is CloudHomeFocus.List -> if (focus.openAll) stringResource(R.string.cloud_hint_all, focus.count) else stringResource(R.string.cloud_hint_add)
                CloudHomeFocus.Actions -> null
            }
            hint?.let { Text(it, style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp), color = MockDim) }
        }
        Row(
            Modifier.offset(y = (260.dp - contentTop).coerceAtLeast(0.dp)).padding(start = LikhtarDimens.ScreenPadding)
                .onDown(onDownFromActions),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            ActionPill("⌕", stringResource(R.string.cloud_search), null, onSearch, Modifier.focusRequester(actionsFocus))
            ActionPill("▦", stringResource(R.string.cloud_all), total, onAll)
            ActionPill("⚙", stringResource(R.string.cloud_my_services), null, onServices)
        }
    }
}

/** ↓ з рядка дій — у перший ряд: стандартний пошук фокуса йшов уздовж ряду. */
private fun Modifier.onDown(action: () -> Boolean): Modifier = onPreviewKeyEvent { e ->
    e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && action()
}

/** .act: 22 px, поле 14×28, скло, у фокусі — світле й трохи більше. */
@Composable
private fun ActionPill(icon: String, label: String, count: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(99.dp),
        background = LikhtarColors.SurfaceAlt,
        focusedBackground = LikhtarColors.GlassActive,
        borderColor = LikhtarColors.PillBorder,
        focusedScale = 1.05f,
    ) { focused ->
        Row(Modifier.padding(horizontal = 15.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon, style = TextStyle(fontSize = 11.5.sp), color = if (focused) MockInk else LikhtarColors.GlassActive)
            Text(label, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp), color = if (focused) MockInk else LikhtarColors.GlassActive)
            count?.let { Text(it.toString(), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp), color = if (focused) LikhtarColors.BorderMuted else MockDim) }
        }
    }
}

/** Порожній список чи «+ Додати гру з каталогу» (макет: .card.empty) — тієї ж висоти, що постер. */
@Composable
fun AddToListCard(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(110.dp, 165.dp),
        shape = RoundedCornerShape(7.dp),
        background = Color.Transparent,
        focusedBackground = Color.Transparent,
        borderColor = Color.Transparent,
        focusedScale = 1.10f,
        focusLighten = 0f,
    ) { focused ->
        // .card.empty: пунктир 3 px #4a453e.
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                color = LikhtarColors.BorderStrong,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))),
            )
        }
        Text(
            text,
            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp, lineHeight = 14.sp),
            color = if (focused) LikhtarColors.TextPrimary else MockDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(13.dp),
        )
    }
}

/**
 * Меню гри на довге OK (макет: #menu): грати в кожному варіанті (непідключені — сірим), списки,
 * «У мене є», справжнє посилання в браузері.
 */
@Composable
fun CloudGameMenu(game: CloudGame, viewModel: CloudGamesViewModel, onDismiss: () -> Unit) {
    val library by viewModel.library.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestAfterFrame() } }
    LikhtarDialog(onDismiss = onDismiss, width = 350.dp, side = true) {
        Text(game.title, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 22.sp), color = LikhtarColors.TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
        CloudChips(game, Modifier.padding(bottom = 12.dp), library.services)
        val installs = stringResource(R.string.cloud_installs)
        var focused = false
        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            game.rom?.let { rom ->
                item(key = "rom") {
                    MenuItem(
                        text = stringResource(R.string.cloud_menu_play, "RetroArch · ${rom.system}"),
                        dot = RetroColor,
                        enabled = !rom.missing,
                        right = if (rom.missing) stringResource(R.string.cloud_no_file) else null,
                        onClick = { viewModel.play(game); onDismiss() },
                        modifier = Modifier.focusRequester(first).also { focused = true },
                    )
                }
            }
            CloudService.entries.forEach { s ->
                game.sources[s].orEmpty().forEachIndexed { i, l ->
                    item(key = "$s$i") {
                        val label = (listOf(serviceName(s)) + listOfNotNull(l.store.ifEmpty { null }, installs.takeIf { l.install })).joinToString(" · ")
                        val on = s in library.services
                        MenuItem(
                            text = if (on) stringResource(R.string.cloud_menu_play, label) else label,
                            dot = brand(s),
                            right = if (on) null else stringResource(R.string.cloud_not_connected),
                            enabled = on,
                            onClick = { viewModel.play(game, s, l); onDismiss() },
                            modifier = if (on && !focused) Modifier.focusRequester(first).also { focused = true } else Modifier,
                        )
                    }
                }
            }
            item(key = "now") {
                MenuItem(stringResource(R.string.cloud_now), right = if (game.key in library.playing) stringResource(R.string.cloud_in_list) else "+", top = 11.dp, onClick = { viewModel.updateLibrary { it.togglePlaying(game.key) } })
            }
            item(key = "fav") {
                MenuItem(stringResource(R.string.cloud_favorites), right = if (game.key in library.favorites) stringResource(R.string.cloud_in_list) else "+", onClick = { viewModel.updateLibrary { it.toggleFavorite(game.key) } })
            }
            if (!game.free && game.rom == null) item(key = "own") {
                MenuItem(stringResource(R.string.cloud_owned), right = if (game.key in library.owned) "✓" else "", onClick = { viewModel.updateLibrary { it.toggleOwned(game.key) } })
            }
            if (game.rom == null) item(key = "web") { MenuItem(stringResource(R.string.cloud_open_web), right = "↗", top = 11.dp, onClick = { viewModel.openWeb(game) }) }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    right: String? = null,
    enabled: Boolean = true,
    top: Dp = 0.dp,
) {
    FocusableSurface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.padding(top = top).fillMaxWidth().height(25.dp),
        shape = RoundedCornerShape(7.dp),
        background = Color.Transparent,
        focusedBackground = MockFocus,
        borderColor = Color.Transparent,
        focusedScale = 1f,
    ) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            dot?.let { Box(Modifier.size(7.dp).clip(CircleShape).background(it)) }
            Text(text, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = if (focused) MockInk else if (enabled) LikhtarColors.TextPrimary else LikhtarColors.WarmSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            right?.let { Text(it, style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 9.sp), color = if (focused) MockInk else MockDim) }
        }
    }
}

/**
 * «Які сервіси в тебе є?» (макет: #setup) — перший запуск і «⚙ Мої сервіси». Лаунчер ні в що не
 * логіниться; тут лише кажемо, де можна запускати ігри, і чи є Game Pass.
 */
@Composable
fun CloudSetupScreen(first: Boolean, onDone: () -> Unit, viewModel: CloudGamesViewModel = hiltViewModel()) {
    val library by viewModel.library.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestAfterFrame() } }
    fun count(s: CloudService) = catalog.games.count { !it.sources[s].isNullOrEmpty() }
    fun toggle(s: CloudService) = viewModel.updateLibrary { lib ->
        val services = if (s in lib.services) lib.services - s else lib.services + s
        lib.copy(services = services, gamePass = lib.gamePass && CloudService.XBOX in services)
    }
    Box(Modifier.fillMaxSize().background(MockBg), contentAlignment = Alignment.Center) {
        Column(Modifier.width(550.dp)) {
            Text(stringResource(if (first) R.string.cloud_setup_first else R.string.cloud_my_services), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp), color = MockDim)
            Text(stringResource(R.string.cloud_setup_q), style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 29.sp), color = LikhtarColors.TextPrimary, modifier = Modifier.padding(top = 5.dp, bottom = 7.dp))
            Text(stringResource(R.string.cloud_setup_p), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 12.sp, lineHeight = 17.5.sp), color = MockDim, modifier = Modifier.padding(bottom = 18.dp))
            SetupOption("Boosteroid", brand(CloudService.BOOSTEROID), CloudService.BOOSTEROID in library.services, androidx.compose.ui.res.pluralStringResource(R.plurals.cloud_setup_games, count(CloudService.BOOSTEROID), count(CloudService.BOOSTEROID)), { toggle(CloudService.BOOSTEROID) }, Modifier.focusRequester(firstFocus))
            SetupOption("GeForce NOW", brand(CloudService.GEFORCE_NOW), CloudService.GEFORCE_NOW in library.services, androidx.compose.ui.res.pluralStringResource(R.plurals.cloud_setup_games, count(CloudService.GEFORCE_NOW), count(CloudService.GEFORCE_NOW)), { toggle(CloudService.GEFORCE_NOW) })
            SetupOption(stringResource(R.string.cloud_xbox_full), brand(CloudService.XBOX), CloudService.XBOX in library.services, androidx.compose.ui.res.pluralStringResource(R.plurals.cloud_setup_xbox, count(CloudService.XBOX), count(CloudService.XBOX)), { toggle(CloudService.XBOX) })
            val xbox = CloudService.XBOX in library.services
            SetupOption(stringResource(R.string.cloud_gamepass), null, library.gamePass, stringResource(R.string.cloud_gamepass_note), { if (xbox) viewModel.updateLibrary { it.copy(gamePass = !it.gamePass) } }, enabled = xbox)
            // Ретро — з теки ROMs, як і досі; тут лише сказати, що воно в тих самих списках.
            val roms = viewModel.games.collectAsState().value.count { it.rom != null }
            val retroToast = stringResource(R.string.cloud_retro_toast)
            SetupOption(stringResource(R.string.cloud_retro_setup), RetroColor, true, androidx.compose.ui.res.pluralStringResource(R.plurals.cloud_retro_note, roms, roms), { Toast.makeText(context, retroToast, Toast.LENGTH_SHORT).show() })
            val need = stringResource(R.string.cloud_setup_need)
            FocusableSurface(
                onClick = {
                    if (library.services.isEmpty()) {
                        Toast.makeText(context, need, Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateLibrary { it.copy(setupDone = true) }
                        onDone()
                    }
                },
                modifier = Modifier.padding(top = 11.dp).height(32.dp),
                shape = RoundedCornerShape(8.dp),
                background = LikhtarColors.SurfaceElevated,
                focusedBackground = MockFocus,
                borderColor = Color.Transparent,
                focusedScale = 1.04f,
            ) { focused ->
                Text(stringResource(if (first) R.string.cloud_setup_done else R.string.cloud_setup_save), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.sp), color = if (focused) MockInk else LikhtarColors.TextPrimary, modifier = Modifier.align(Alignment.Center).padding(horizontal = 21.dp))
            }
        }
    }
}

/** .opt: прапорець, кольорова крапка сервісу, назва, примітка праворуч. */
@Composable
private fun SetupOption(title: String, dot: Color?, on: Boolean, note: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.padding(bottom = 7.dp).fillMaxWidth().height(36.dp),
        shape = RoundedCornerShape(9.dp),
        background = LikhtarColors.SurfaceSoft,
        focusedBackground = LikhtarColors.SurfaceSoft,
        borderColor = Color.Transparent,
        focusedScale = 1f,
        focusLighten = 0f,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().then(if (focused) Modifier.border(1.dp, MockFocus, RoundedCornerShape(9.dp)) else Modifier).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier.size(17.dp).clip(RoundedCornerShape(4.5.dp)).background(if (on) MockFocus else Color.Transparent)
                    .border(1.5.dp, if (on) MockFocus else LikhtarColors.WarmSubtle, RoundedCornerShape(4.5.dp)),
                contentAlignment = Alignment.Center,
            ) { if (on) Text("✓", style = TextStyle(fontSize = 11.5.sp), color = MockInk) }
            dot?.let { Box(Modifier.size(11.dp).clip(CircleShape).background(it)) }
            Text(title, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 14.sp), color = if (enabled) LikhtarColors.TextPrimary else LikhtarColors.TextPrimary.copy(alpha = 0.35f), modifier = Modifier.weight(1f))
            Text(note, style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 9.5.sp), color = MockDim.copy(alpha = if (enabled) 1f else 0.35f))
        }
    }
}
