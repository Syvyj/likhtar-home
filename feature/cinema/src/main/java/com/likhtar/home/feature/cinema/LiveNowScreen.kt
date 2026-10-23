package com.likhtar.home.feature.cinema

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarScreenScaffold
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.ProtoButton
import com.likhtar.home.core.model.LikhtarSection
import kotlinx.coroutines.delay

/**
 * ТВ Ефір, сцена 1 — варіант A прототипу (site/livetv/variants; 1920×1080 px, 1 dp = 2 px).
 * Банер — картка під фокусом: знак каналу, передача, «зараз на X · ще N хв», кадр праворуч.
 * Ряди з 600 px; у кінці кожного — «Усі канали», сцена 2 ([LiveGuide]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveNowScreen(
    onSelectSection: (LikhtarSection) -> Unit,
    viewModel: LiveNowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val menuFocus = remember { FocusRequester() }
    val rowsFocus = remember { FocusRequester() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    var rowsHaveFocus by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        if (state.loading) return@LaunchedEffect
        repeat(FocusAttempts) {
            withFrameNanos { }
            if (rowsHaveFocus) return@LaunchedEffect
            runCatching { rowsFocus.requestFocus() }
        }
    }
    BackHandler(enabled = rowsHaveFocus) { runCatching { menuFocus.requestFocus() } }
    // Сцена 2 (список і прев'ю): відкрита — група; null — сцена 1, шапка й «Мої канали».
    var guideGroup by remember { mutableStateOf<String?>(null) }
    var guideWasOpen by remember { mutableStateOf(false) }
    val mine = state.mine.takeIf { it.isNotEmpty() }
    val railState = remember { androidx.compose.foundation.lazy.LazyListState() }
    // Без своїх каналів сцени 1 немає — одразу сцена 2.
    LaunchedEffect(state.loading, mine == null) {
        if (!state.loading && mine == null && state.channels.isNotEmpty() && !guideWasOpen) guideGroup = GuideAll
    }
    // Банер — картка під фокусом (варіант A прототипу site/livetv/variants).
    var focusedCard by remember { mutableStateOf<LiveCard?>(null) }
    // Довге OK: меню каналу (обрані / приховати); refocus — повернути фокус у ряд, коли
    // приховану картку прибрано з-під нього.
    var menuCard by remember { mutableStateOf<LiveCard?>(null) }
    var refocus by remember { mutableIntStateOf(0) }
    // Після меню — назад на ту саму картку: вікно меню закривається не одразу, і фокус
    // головного вікна падав на «Головну». Перші кадри просимо фокус без перевірки:
    // rowsHaveFocus ще показує стан до меню.
    LaunchedEffect(refocus) {
        if (refocus == 0 || guideGroup != null) return@LaunchedEffect
        repeat(FocusAttempts) { attempt ->
            withFrameNanos { }
            if (attempt >= 3 && rowsHaveFocus) return@LaunchedEffect
            val at = state.mine.indexOfFirst { it.key == focusedCard?.key }
            if (at >= 0 && railState.firstVisibleItemIndex > at) railState.scrollToItem(at)
            runCatching { (if (state.mine.isNotEmpty()) rowsFocus else menuFocus).requestFocus() }
        }
    }
    val shown = focusedCard?.takeIf { c -> mine?.any { it.key == c.key } == true } ?: mine?.firstOrNull()
    // Із сцени 2 — назад на ту саму картку: ряд, з якого пішов фокус, LikhtarRail уже прокрутив
    // на початок, тож спершу докручуємо до неї.
    LaunchedEffect(guideGroup) {
        if (guideGroup != null) { guideWasOpen = true; return@LaunchedEffect }
        if (!guideWasOpen) return@LaunchedEffect
        val at = mine?.indexOfFirst { it.key == shown?.key } ?: -1
        repeat(FocusAttempts) {
            withFrameNanos { }
            if (rowsHaveFocus) return@LaunchedEffect
            if (at >= 0) railState.scrollToItem(at)
            runCatching { (if (mine != null) rowsFocus else menuFocus).requestFocus() }
        }
    }
    // .still і .veil — 700 px; ряд вище, ніж .rows прототипу (600 px), на 66 px: під ним
    // визирає сцена 2.
    val stillHeight = 350.dp
    val rowsTop = 262.dp

    Box(Modifier.fillMaxSize().background(LikhtarColors.Background)) {
        // .still: праворуч 1340×700 px, center 30% / cover; перехрестя .45s.
        AnimatedContent(
            targetState = shown?.let(::cardImage),
            transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(450)) },
            modifier = Modifier.align(Alignment.TopEnd).width(670.dp).height(stillHeight),
            label = "still",
        ) { image ->
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -0.4f),
                modifier = Modifier.fillMaxSize(),
            )
        }
        // .veil: графіт до 560 px, .78 на 760 px, .1 на 1300 px; знизу — у тло за 34 %; згори — .7 за 22 %.
        Box(
            Modifier
                .fillMaxWidth()
                .height(stillHeight)
                .background(
                    Brush.horizontalGradient(
                        0f to LikhtarColors.Background, 560f / 1920 to LikhtarColors.Background, 760f / 1920 to LikhtarColors.Background.copy(alpha = 0.78f), 1300f / 1920 to LikhtarColors.Background.copy(alpha = 0.1f),
                    ),
                )
                .background(Brush.verticalGradient(0.66f to LikhtarColors.Background.copy(alpha = 0f), 1f to LikhtarColors.Background))
                .background(Brush.verticalGradient(0f to LikhtarColors.BackgroundDeep.copy(alpha = 0.7f), 0.22f to LikhtarColors.BackgroundDeep.copy(alpha = 0f))),
        )

        if (!state.loading) {
            shown?.let { card ->
                // .hero: з 96 px зліва й 150 px згори, 1100 px завширшки.
                LiveHero(card, now, Modifier.offset(x = ProtoPad, y = 75.dp).width(550.dp))
            }

            // Жодного джерела каналів: не порожній екран, а що можна зробити — свій плейлист
            // або законні безкоштовні застосунки (список дає сервер).
            if (state.noSources) {
                Column(
                    Modifier
                        .offset(x = ProtoPad, y = 75.dp)
                        .width(760.dp)
                        .onFocusChanged { rowsHaveFocus = it.hasFocus },
                ) {
                    Text(
                        stringResource(R.string.cinema_live_empty_title),
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 31.sp, lineHeight = 31.6.sp, letterSpacing = (-0.62).sp),
                        color = Color.White,
                    )
                    Text(
                        stringResource(if (state.free.isEmpty()) R.string.cinema_live_empty_hint_playlist else R.string.cinema_live_empty_hint),
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 12.5.sp),
                        color = LikhtarColors.TextPrimary,
                        modifier = Modifier.padding(top = 7.dp, bottom = 20.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        state.free.forEachIndexed { i, f ->
                            ProtoButton(
                                label = if (f.installed) stringResource(R.string.cinema_live_free_open, f.name) else stringResource(R.string.cinema_live_free_install, f.name),
                                onClick = { viewModel.openFree(f) },
                                icon = null,
                                modifier = if (i == 0) Modifier.focusRequester(rowsFocus) else Modifier,
                            )
                        }
                    }
                    ProtoButton(
                        label = stringResource(R.string.cinema_live_add_playlist),
                        onClick = {
                            com.likhtar.home.core.model.SettingsTarget.section = "TV"
                            onSelectSection(LikhtarSection.SETTINGS)
                        },
                        icon = null,
                        modifier = if (state.free.isEmpty()) Modifier.focusRequester(rowsFocus) else Modifier,
                    )
                }
            }

            Column(
                Modifier
                    .padding(start = ProtoPad, top = rowsTop + 5.dp)
                    .onFocusChanged { rowsHaveFocus = it.hasFocus },
            ) {
                state.message?.let { message ->
                    // Та сама панель, що в Кінозалі й Реаліті, коли сервер не відповів:
                    // порожній Ефір не відрізнявся від «телепрограма ще не приїхала».
                    ServerDownPanel(
                        onRetry = viewModel::retry,
                        title = message,
                        hint = stringResource(R.string.cinema_server_down_hint),
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                }
                mine?.let { row ->
                    // .row-title 30 px + small 18 px моно, baseline, gap 16 px; margin-bottom 4 px.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 2.dp)) {
                        Text(stringResource(R.string.cinema_live_row_mine), style = RowTitleStyle, color = Color.White, modifier = Modifier.alignByBaseline())
                        Text("${row.size}", style = MonoSmall.copy(fontSize = 9.sp), color = Faint, modifier = Modifier.alignByBaseline())
                    }
                    // ↓ із ряду — сцена 2: усі канали, групи ліворуч.
                    Box(
                        Modifier.onPreviewKeyEvent { e ->
                            when {
                                e.type != KeyEventType.KeyDown -> false
                                e.key == Key.DirectionDown && state.channels.isNotEmpty() -> { guideGroup = GuideAll; true }
                                // ↑ — на вкладку цього розділу, як і Back; сам пошук фокуса вів
                                // на найближчу за геометрією, тобто «Головну».
                                e.key == Key.DirectionUp -> { runCatching { menuFocus.requestFocus() }; true }
                                else -> false
                            }
                        },
                    ) {
                        LikhtarRail(gap = 16.dp, state = railState) {
                            itemsIndexed(row, key = { _, card -> card.key }) { i, card ->
                                val anchor = card.key == shown?.key || (shown == null && i == 0)
                                LiveChannelCard(
                                    card = card,
                                    now = now,
                                    onFocused = { focusedCard = card },
                                    onClick = { viewModel.open(card) },
                                    onLongClick = { menuCard = card },
                                    modifier = Modifier
                                        .padding(top = 6.dp, bottom = 9.dp)
                                        .then(if (anchor) Modifier.focusRequester(rowsFocus) else Modifier),
                                )
                            }
                            // Остання картка у фокусі не впирається в край екрана — той самий відступ, що зліва.
                            item(key = "end") { Spacer(Modifier.width(ProtoPad)) }
                        }
                    }
                }
            }
            if (mine != null && state.channels.isNotEmpty()) {
                GuidePeek(state.groups, Modifier.padding(start = ProtoPad, top = rowsTop + 228.dp).fillMaxWidth())
            }
        }

        LikhtarScreenScaffold(
            section = LikhtarSection.LIVE_TV,
            onSelectSection = onSelectSection,
            menuFocusRequester = menuFocus,
            contentFocusRequester = rowsFocus,
            loading = state.loading,
        ) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LanternLoader(title = stringResource(R.string.cinema_live_loader_title), subtitle = stringResource(R.string.cinema_live_loader_subtitle))
                }
            }
        }

        guideGroup?.let { group ->
            LiveGuide(
                channels = state.channels,
                groups = state.groups,
                schedules = state.schedules,
                now = now,
                group = group,
                onGroup = { guideGroup = it },
                onOpen = viewModel::open,
                onLong = { menuCard = it },
                refocus = refocus,
                // Спершу фокус на «Усі канали» під сценою 2, потім закриваємо: інакше, поки
                // сцена 2 зникає, фокус падав у верхнє меню.
                // Без своїх каналів фокус далі йде в меню (див. повернення фокуса вище).
                onClose = { guideGroup = null },
            )
        }

        if (state.needPlayer) {
            NeedPlayerDialog(
                onInstall = { viewModel.openFree(FreeSource(PlayerOnPlay.first, PlayerOnPlay.second, installed = false)) },
                onDismiss = viewModel::dismissNeedPlayer,
            )
        }

        menuCard?.let { card ->
            LiveChannelMenu(
                card = card,
                pinned = card.link.url in state.pinned,
                onFavourite = { viewModel.toggleFavourite(card) },
                onHide = {
                    viewModel.hide(card)
                    if (focusedCard?.key == card.key) focusedCard = null
                },
                onDismiss = {
                    menuCard = null
                    refocus++
                },
            )
        }
    }
}

/** .hero варіанта A: знак 132×76 px, h1 62 px / 1.02 на 10 px нижче, .meta 25 px за 14 px. */
@Composable
private fun LiveHero(card: LiveCard, now: Long, modifier: Modifier = Modifier) {
    val res = LocalContext.current.resources
    // .swap: opacity і translateY 6 px за .2s.
    AnimatedContent(
        targetState = card,
        transitionSpec = { (fadeIn(tween(200)) + slideInVertically(tween(200)) { 3 }) togetherWith fadeOut(tween(200)) },
        contentKey = { it.key },
        modifier = modifier,
        label = "heroText",
    ) { c ->
        Column {
            ChannelMark(c.link, 66.dp, 38.dp)
            Text(
                programmeTitle(res, c),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 31.sp, lineHeight = 31.6.sp, letterSpacing = (-0.62).sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
            Text(
                heroMeta(res, c, now),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 12.5.sp),
                color = LikhtarColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
    }
}

/** «ще 1 год 33 хв» — коротке «лишилось» прототипу (shortLeft). */
internal fun shortLeft(res: android.content.res.Resources, endMillis: Long, now: Long): String {
    val minutes = ((endMillis - now) / 60_000L).toInt()
    if (minutes <= 0) return res.getString(R.string.cinema_live_ending)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> res.getString(R.string.cinema_live_more_m, m)
        m == 0 -> res.getString(R.string.cinema_live_more_h, h)
        else -> res.getString(R.string.cinema_live_more_hm, h, m)
    }
}

/** clean() прототипу: без «Х/ф», лапок і крапки наприкінці; «Сезон 3» — з назвою каналу. */
internal fun cleanProgramme(link: com.likhtar.home.core.tv.ChannelLink, raw: String): String =
    programmeName(link, raw.replace(ProgrammePrefix, "").replace(QuotedLead, "$1").trim().removeSuffix(".").trim())

private val ProgrammePrefix = Regex("""^(Х/ф|Т/с|М/с|М/ф|Д/с|Д/ф)\s*""")
private val QuotedLead = Regex("""^"([^"]+)"[.,]?""")

private val ProtoPad = 48.dp

private val Faint = LikhtarColors.TextPrimary.copy(alpha = 0.42f)
private val MonoSmall = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 8.sp)
private val RowTitleStyle = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

/** Назва на картці: твір, а для серіалу ще й серія, якщо телепрограма її назвала. */
internal fun programmeTitle(res: android.content.res.Resources, card: LiveCard): String {
    // Ефір YouTube — його власна назва; потік плейлиста без програми — «Прямий ефір».
    if (card.bare) return cleanProgramme(card.link, card.raw).ifBlank { res.getString(R.string.cinema_live_bare) }
    val m = card.match
    val episode = m?.takeIf { card.item.kind == TitleKind.TV }?.let {
        listOfNotNull(
            it.season.takeIf { s -> s > 0 }?.let { s -> res.getString(R.string.cinema_live_season_short, s) },
            it.episode.takeIf { e -> e > 0 }?.let { e -> res.getString(R.string.cinema_live_episode_n, e) },
        ).joinToString(" ")
    }.orEmpty()
    return listOf(card.item.title, episode).filter(String::isNotBlank).joinToString(" · ")
}

/**
 * Кадр картки: впізнаний твір — кадр TMDB (у телепрограми нерідко замість кадру логотип
 * каналу на заливці), інакше кадр передачі, полегшений до розміру картки.
 */
internal fun cardImage(card: LiveCard): String? =
    TmdbImages.still(card.work?.backdropPath)
        ?: card.image?.replace(Regex("/pt/r\\d+x\\d+$"), "/pt/r600x340")

/** .meta: «зараз на X · <b>ще 40 хв</b>»; майбутнє — «на X · о 21:00». */
private fun heroMeta(res: android.content.res.Resources, card: LiveCard, now: Long): AnnotatedString = buildAnnotatedString {
    val channel = channelLabel(card.link.name)
    if (card.startMillis > now) {
        append(res.getString(R.string.cinema_live_on, channel))
        append(" · ")
        append(res.getString(R.string.cinema_live_at, com.likhtar.home.core.designsystem.clockTime(card.startMillis)))
    } else {
        append(res.getString(R.string.cinema_live_now_on, channel))
        if (card.bare) return@buildAnnotatedString
        append(" · ")
        withStyle(SpanStyle(color = LikhtarColors.Glow, fontWeight = FontWeight.SemiBold)) { append(shortLeft(res, card.endMillis, now)) }
    }
}

private const val FocusAttempts = 20


/** Службові ряди — перекладом; тематичні (жанри каталогу) — як прийшли. */
