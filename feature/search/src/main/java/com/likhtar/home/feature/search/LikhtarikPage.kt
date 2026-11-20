package com.likhtar.home.feature.search

import androidx.annotation.DrawableRes
import com.likhtar.home.core.designsystem.LikhtarColors
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.likhtar.home.core.designsystem.tryFocus
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.Candidate
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.ResumeCard
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.catalog.WatchMood
import com.likhtar.home.core.catalog.WatchFormat
import com.likhtar.home.core.designsystem.CatalogPosterCard
import com.likhtar.home.core.designsystem.PosterHeight
import com.likhtar.home.core.designsystem.PosterWidth
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarIcons
import com.likhtar.home.core.designsystem.LikhtarVoice
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.NoBringIntoView
import com.likhtar.home.core.designsystem.ProtoButton
import com.likhtar.home.core.designsystem.R as DsR
import com.likhtar.home.core.designsystem.ResumeTile
import com.likhtar.home.core.designsystem.SectionTitle
import com.likhtar.home.core.designsystem.cardSlot
import com.likhtar.home.core.designsystem.focusDirections
import kotlinx.coroutines.launch

/**
 * Ліхтарик за прототипом (site/search): пошук і радник «Що подивитись» на одному
 * екрані. Згори — запит і кнопки, під ними недавні пошуки або два питання радника; ліворуч —
 * голос Ліхтарика (думка, що друкується, і знак, що розгоряється, поки він думає); праворуч —
 * банер найкращого збігу чи поради у фокусі; знизу — ряд порад із поясненням «чому».
 * Нижче згину — історія, закладки й добірки за смаком; сторінка їде вгору цілою.
 * Розміри — з прототипу (1920×1080 px, 1 dp = 2 px), відраховані від верху екрана.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LikhtarikPage(
    state: SearchUiState,
    advisor: AdvisorUiState,
    shelves: Shelves,
    voiceOnTv: Boolean,
    canAsk: Boolean,
    queryFocus: FocusRequester,
    menuFocus: FocusRequester,
    voiceFocus: FocusRequester,
    phoneFocus: FocusRequester,
    /** Куди стати на вході: у раднику — вибраний настрій, у пошуку — найкращий збіг. */
    startFocus: FocusRequester,
    bookmarksFocus: FocusRequester,
    onType: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    onPhone: () -> Unit,
    onAdvisor: () -> Unit,
    onPickRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onFormat: (WatchFormat) -> Unit,
    onMood: (WatchMood) -> Unit,
    open: (CatalogItem) -> Unit,
    playCard: (ResumeCard) -> Unit,
    onForget: (ResumeCard) -> Unit,
    onBookmark: (CatalogItem) -> Unit,
    shows: List<com.likhtar.home.core.catalog.TrackedShow> = emptyList(),
    week: List<com.likhtar.home.core.catalog.CalendarEvent> = emptyList(),
    today: String = "",
    hero: HeroPick? = null,
    onHeroPlay: (HeroPick) -> Unit = {},
    onShow: (com.likhtar.home.core.catalog.TrackedShow) -> Unit = {},
    onShowLong: (com.likhtar.home.core.catalog.TrackedShow) -> Unit = {},
    onEvent: (com.likhtar.home.core.catalog.CalendarEvent) -> Unit = {},
    onCalendar: () -> Unit = {},
    /** Знак назви для героя, якого немає в самому тайтлі (розклад TMDB, порада). */
    logoOf: suspend (CatalogItem) -> String? = { null },
    game: com.likhtar.home.feature.cinema.TriviaUi = com.likhtar.home.feature.cinema.TriviaUi(),
    onGameAnswer: (Boolean) -> Unit = {},
    onGameNext: () -> Unit = {},
) {
    val searching = state.query.isNotBlank()
    // Радник — бічною панеллю (макет «Новий Ліхтарик», екран 4), а не пігулками на пів сторінки.
    var advisorOpen by remember { mutableStateOf(false) }
    if (advisorOpen) AdvisorPanel(advisor, onFormat = onFormat, onMood = onMood, onDismiss = { advisorOpen = false })
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val anchor = with(density) { 75.dp.toPx() }
    var down by remember { mutableStateOf(false) }
    // Картка ряду порад у фокусі: у раднику банер іде за нею, «чому» — під заголовком ряду.
    var focusedPick by remember { mutableStateOf<CatalogItem?>(null) }
    val picks = advisor.picks
    // Банер — перша порада, ряд — решта: та сама картка двічі поруч не стоїть.
    val spotCandidate = if (!searching) picks.firstOrNull() else null
    // Без запиту й без поради радника — герой «нове для вас» (екран 1); інакше — банер і ряд, як були.
    val heroMode = !searching && picks.isEmpty() && !advisor.loading
    val heroPick = hero.takeIf { heroMode }
    // Герой доїжджає пізніше за кнопки (трекер і полиці вантажаться) — курсор уже стояв на
    // «Що подивитись». Поки людина нічого не натиснула, переводимо його на головну дію героя.
    var touched by remember { mutableStateOf(false) }
    LaunchedEffect(heroPick != null) {
        if (heroPick == null || touched) return@LaunchedEffect
        repeat(com.likhtar.home.core.designsystem.FOCUS_FRAMES) {
            androidx.compose.runtime.withFrameNanos { }
            if (!touched && startFocus.tryFocus()) return@LaunchedEffect
        }
    }
    val thinking = if (searching) state.aiPending || state.status == SearchStatus.LOADING else advisor.loading
    // Ряд порад у фокусі стає під верхнє меню, як ряди під згином: постери з низом видно цілком
    //. 398 — верх ряду, 75 — край меню (anchor).
    val railLift = with(density) { (420 - 75).dp.toPx() }.toInt()
    val railFocus: Modifier = Modifier.onFocusChanged {
        if (it.hasFocus && scroll.value != railLift) {
            // Тінь під меню — як для рядів під згином: банер не визирає з-під меню.
            down = true
            scope.launch { scroll.animateScrollTo(railLift, tween(350)) }
        }
    }
    val topFocus: Modifier = Modifier.onFocusChanged {
        if (it.hasFocus && scroll.value != 0) {
            down = false
            scope.launch { scroll.animateScrollTo(0, tween(350)) }
        }
    }

    // Шпалери під Ліхтариком немає (LikhtarApp) — тло своє, графіт, як у Кінозалі.
    Box(
        Modifier
            .fillMaxSize()
            .background(LikhtarColors.Background)
            .background(
                Brush.radialGradient(
                    colors = listOf(LikhtarColors.HeroGlowWarm, Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(1280f, 430f),
                    radius = 740f,
                ),
            )
            .onPreviewKeyEvent { touched = true; false },
    ) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoView) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().height(if (heroMode) 405.dp else 700.dp)) {
                    // Кадр героя — від верху екрана, під меню й пошуком.
                    if (heroPick != null) {
                        LikhtarikHero(
                            pick = heroPick,
                            onPlay = { onHeroPlay(heroPick) },
                            onOpen = {
                                open(
                                    when (heroPick) {
                                        is HeroPick.NewEpisode -> heroPick.show.item
                                        is HeroPick.Continue -> heroPick.item.asCatalogItem()
                                        is HeroPick.Advice -> heroPick.item
                                    },
                                )
                            },
                            playFocus = startFocus,
                            logoOf = logoOf,
                            contentTop = 118.dp,
                            modifier = Modifier.fillMaxWidth().height(405.dp).then(topFocus),
                        )
                    }
                    // Найкращий збіг пошуку й порада радника — тією самою сценою на весь верх, а не
                    // банером у рамці поруч із порожньою колонкою.
                    val spotItem = if (searching) state.best else spotCandidate?.item
                    if (!heroMode && spotItem != null) {
                        val dim by animateFloatAsState(if (thinking) 0.45f else 1f, tween(500), label = "spotDim")
                        HeroScene(
                            item = spotItem,
                            kicker = when {
                                !searching -> stringResource(R.string.search_likhtarik_pick)
                                spotItem in state.ai && state.descriptive -> stringResource(R.string.search_likhtarik_pick)
                                else -> stringResource(R.string.search_section_best)
                            },
                            meta = spotMeta(spotItem, spotCandidate),
                            overview = if (searching) spotItem.overview else spotCandidate?.worth?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.search_worth, it) }.orEmpty(),
                            // Рядок Ліхтарика — поки він думає; коли збіг знайдено, кікер уже каже те саме.
                            line = if (searching) (if (thinking) state.aiRefined.ifBlank { searchStatus(state) } else "") else spotCandidate?.why.orEmpty(),
                            playLabel = stringResource(R.string.search_open),
                            onPlay = { open(spotItem) },
                            onLongPlay = { onBookmark(spotItem) },
                            playFocus = startFocus,
                            logoOf = logoOf,
                            contentTop = 160.dp,
                            modifier = Modifier.fillMaxWidth().height(405.dp).graphicsLayer { alpha = dim }.then(topFocus),
                        )
                    }
                    // .ask: з 140 px (під новою шапкою 56 dp), поле й три кнопки.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .offset(y = 70.dp)
                            .padding(horizontal = LikhtarDimens.ScreenPadding)
                            .fillMaxWidth()
                            // Ряд порад піднімає сторінку — поле зникає, а не лізе під верхнє меню.
                            .graphicsLayer { alpha = (1f - scroll.value / railLift.toFloat()).coerceIn(0f, 1f) }
                            .then(topFocus),
                    ) {
                        QueryField(
                            query = state.draft,
                            placeholder = stringResource(if (canAsk) R.string.search_query_placeholder else R.string.search_query_placeholder_plain),
                            onQueryChange = onType,
                            onSubmit = onSubmit,
                            onClear = {},
                            queryFocusRequester = queryFocus,
                            menuFocusRequester = menuFocus,
                            contentFocusRequester = null,
                            modifier = Modifier.weight(1f),
                        )
                        val up = Modifier.focusDirections { this.up = menuFocus }
                        if (voiceOnTv) ProtoButton(
                            label = stringResource(R.string.search_voice),
                            onClick = onVoice,
                            icon = DsR.drawable.ic_solar_mic,
                            height = 42.dp,
                            modifier = up.focusRequester(voiceFocus),
                        )
                        ProtoButton(
                            label = stringResource(R.string.search_from_phone),
                            onClick = onPhone,
                            icon = DsR.drawable.ic_solar_phone,
                            height = 42.dp,
                            modifier = up.focusRequester(phoneFocus),
                        )
                        // «Що подивитись» увімкнене — бурштинова рамка (.btn.on).
                        Box(
                            Modifier.then(
                                if (!searching) Modifier.border(1.dp, LikhtarColors.Glow.copy(alpha = 0.55f), RoundedCornerShape(LikhtarDimens.CornerPill)) else Modifier,
                            ),
                        ) {
                            ProtoButton(
                                label = stringResource(R.string.search_what_to_watch),
                                onClick = {
                                    onAdvisor()
                                    advisorOpen = true
                                },
                                icon = DsR.drawable.ic_solar_magic,
                                height = 42.dp,
                                // Ні героя, ні поради — вхід сюди: це єдине, що на сторінці робить щось.
                                modifier = if (heroMode && heroPick == null) up.focusRequester(startFocus) else up,
                            )
                        }
                    }

                    // .line2: з 256 px, 64 px заввишки.
                    val line2 = Modifier
                        .offset(y = 122.dp)
                        .padding(horizontal = LikhtarDimens.ScreenPadding)
                        .height(32.dp)
                        // Ряд у фокусі піднімає сторінку — пігулки гаснуть разом із полем, а банер
                        // займає їхню смугу.
                        .graphicsLayer { alpha = (1f - scroll.value / railLift.toFloat()).coerceIn(0f, 1f) }
                        .then(topFocus)
                    if (searching) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = line2,
                        ) {
                            if (state.recent.isNotEmpty()) {
                                Label(stringResource(R.string.search_recent_title))
                                state.recent.take(4).forEach { q ->
                                    PillChip(q, on = q == state.query, icon = DsR.drawable.ic_solar_history_line, onClick = { onPickRecent(q) })
                                }
                                PillChip(stringResource(R.string.search_recent_clear), muted = true, onClick = onClearRecent)
                            } else {
                                Label(stringResource(if (canAsk) R.string.search_hint else R.string.search_hint_plain))
                            }
                        }
                    }

                    // .voice: з 352 px, 500×340 px.
                    if (heroPick == null && (heroMode || spotItem == null)) LikhtarVoice(
                        name = stringResource(R.string.search_likhtarik),
                        status = if (searching) searchStatus(state) else advisorStatus(advisor),
                        text = when {
                            searching -> state.aiRefined
                            heroMode -> stringResource(R.string.search_hero_empty)
                            else -> advisorThought(advisor)
                        },
                        thinking = thinking,
                        modifier = Modifier.offset(x = LikhtarDimens.ScreenPadding, y = if (heroMode) 122.dp else 162.dp).width(if (heroMode) 420.dp else 232.dp),
                    )

                    // .rows: з 734 px — ряд порад із поясненням під заголовком.
                    val groups: List<Pair<String?, List<CatalogItem>>> = if (searching) {
                        listOf(
                            null to (if (state.descriptive) state.aiRest else state.aiRest),
                            stringResource(R.string.search_section_shows) to state.showItems,
                            stringResource(R.string.search_section_movies) to state.movies,
                            stringResource(R.string.search_section_series) to state.series,
                        ).filter { it.second.isNotEmpty() }
                    } else {
                        listOf(null to picks.drop(1).map { it.item }).filter { it.second.isNotEmpty() }
                    }
                    if (groups.isNotEmpty()) {
                        val why: Map<String, String> = if (searching) state.aiWhy else picks.associate { it.item.id to it.why }
                        PicksRail(
                            title = if (searching) {
                                if (state.aiRest.isNotEmpty()) stringResource(R.string.search_section_ai_for_query) else stringResource(R.string.search_section_best)
                            } else {
                                stringResource(R.string.search_advisor_rail)
                            },
                            // Не українцю порадник пояснень не дає (картки лише українською) — і обіцяти
                            // «Ліхтарик скаже, чому» тоді нема чого.
                            hint = if (!searching && picks.none { it.why.isNotBlank() }) "" else
                                stringResource(if (searching) R.string.search_why_hint_search else R.string.search_why_hint_advisor),
                            groups = groups,
                            why = why,
                            focused = focusedPick,
                            onFocused = { focusedPick = it },
                            open = open,
                            onLongClick = onBookmark,
                            modifier = Modifier
                                .offset(y = 420.dp)
                                .padding(start = LikhtarDimens.ScreenPadding)
                                .then(railFocus),
                        )
                    }
                }

                // Нижче згину: ряд у фокусі стає під верхнє меню (150 px).
                var historyTop by remember { mutableFloatStateOf(0f) }
                var bookmarksTop by remember { mutableFloatStateOf(0f) }
                val below: @Composable (Modifier, () -> Float) -> Modifier = { m, top ->
                    m.onFocusChanged {
                        if (it.hasFocus) {
                            down = true
                            scope.launch { scroll.animateScrollTo((top() - anchor).toInt().coerceAtLeast(0), tween(350)) }
                        }
                    }
                }
                if (!searching && shows.any { it.state != com.likhtar.home.core.catalog.ShowState.DROPPED }) {
                    var top by remember { mutableFloatStateOf(0f) }
                    FoldSection(
                        title = stringResource(com.likhtar.home.feature.cinema.R.string.tracker_my_shows),
                        hint = stringResource(com.likhtar.home.feature.cinema.R.string.tracker_my_shows_hint),
                        modifier = below(Modifier.onGloballyPositioned { top = it.positionInParent().y }) { top },
                    ) {
                        com.likhtar.home.feature.cinema.MyShowsRail(shows, onOpen = onShow, onLongClick = onShowLong)
                    }
                }
                // «Кинуті» — згорнуто: є куди подивитись і звідки повернути, але не заважає.
                val dropped = shows.count { it.state == com.likhtar.home.core.catalog.ShowState.DROPPED }
                // Повернули останнього кинутого — розділ зник разом із фокусом; не в меню, а на героя.
                var hadDropped by remember { mutableStateOf(false) }
                LaunchedEffect(dropped) {
                    if (hadDropped && dropped == 0) {
                        androidx.compose.runtime.withFrameNanos { }
                        startFocus.tryFocus()
                    }
                    hadDropped = dropped > 0
                }
                if (!searching && dropped > 0) {
                    var droppedOpen by remember { mutableStateOf(false) }
                    var top by remember { mutableFloatStateOf(0f) }
                    FoldSection(
                        title = stringResource(com.likhtar.home.feature.cinema.R.string.tracker_dropped_section) + " · $dropped",
                        hint = stringResource(com.likhtar.home.feature.cinema.R.string.tracker_dropped_hint),
                        modifier = below(Modifier.onGloballyPositioned { top = it.positionInParent().y }) { top },
                    ) {
                        Column {
                            ProtoButton(
                                label = stringResource(if (droppedOpen) com.likhtar.home.feature.cinema.R.string.tracker_dropped_hide else com.likhtar.home.feature.cinema.R.string.tracker_dropped_show),
                                onClick = { droppedOpen = !droppedOpen },
                                icon = null,
                                height = 32.dp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            if (droppedOpen) com.likhtar.home.feature.cinema.MyShowsRail(shows, onOpen = onShow, onLongClick = onShowLong, onlyDropped = true)
                        }
                    }
                }
                if (!searching && (week.isNotEmpty() || shows.isNotEmpty())) {
                    var top by remember { mutableFloatStateOf(0f) }
                    FoldSection(
                        title = stringResource(com.likhtar.home.feature.cinema.R.string.tracker_week),
                        hint = stringResource(com.likhtar.home.feature.cinema.R.string.tracker_week_hint),
                        modifier = below(Modifier.onGloballyPositioned { top = it.positionInParent().y }) { top },
                    ) {
                        com.likhtar.home.feature.cinema.WeekStrip(
                            today = today,
                            events = week,
                            onOpen = onEvent,
                            onCalendar = onCalendar,
                            modifier = Modifier.padding(top = 8.dp, end = LikhtarDimens.ScreenPadding),
                        )
                    }
                }
                game.shown?.takeIf { !searching }?.let { statement ->
                    var top by remember { mutableFloatStateOf(0f) }
                    FoldSection(
                        title = stringResource(com.likhtar.home.feature.cinema.R.string.trivia_kicker),
                        hint = stringResource(com.likhtar.home.feature.cinema.R.string.trivia_hint),
                        modifier = below(Modifier.onGloballyPositioned { top = it.positionInParent().y }) { top },
                    ) {
                        GameCard(game, statement, onAnswer = onGameAnswer, onNext = onGameNext, onOpen = { open(statement.item) })
                    }
                }
                if (shelves.bookmarks.isNotEmpty()) {
                    FoldSection(
                        title = stringResource(R.string.search_section_bookmarks),
                        hint = stringResource(R.string.search_bookmarks_hint),
                        modifier = below(Modifier.onGloballyPositioned { bookmarksTop = it.positionInParent().y }) { bookmarksTop },
                    ) {
                        PosterRow(shelves.bookmarks, open = open, onLongClick = onBookmark, firstFocus = bookmarksFocus)
                    }
                }
                shelves.personal.forEach { shelf ->
                    var top by remember(shelf.title) { mutableFloatStateOf(0f) }
                    FoldSection(
                        title = shelf.title,
                        hint = stringResource(R.string.search_liked_hint),
                        modifier = below(Modifier.onGloballyPositioned { top = it.positionInParent().y }) { top },
                    ) {
                        PosterRow(shelf.items, open = open, onLongClick = onBookmark)
                    }
                }
                shelves.advice?.takeIf { it.items.isNotEmpty() }?.let { advice ->
                    var top by remember { mutableFloatStateOf(0f) }
                    FoldSection(
                        title = stringResource(R.string.search_section_advice),
                        hint = advice.thought.ifBlank { stringResource(R.string.search_liked_hint) },
                        modifier = below(Modifier.onGloballyPositioned { top = it.positionInParent().y }) { top },
                    ) {
                        PosterRow(advice.items, open = open, onLongClick = onBookmark)
                    }
                }
                state.starters.forEach { shelf ->
                    var top by remember(shelf.title) { mutableFloatStateOf(0f) }
                    FoldSection(
                        title = shelf.title,
                        hint = "",
                        modifier = below(Modifier.onGloballyPositioned { top = it.positionInParent().y }) { top },
                    ) {
                        PosterRow(shelf.items, open = open, onLongClick = onBookmark)
                    }
                }
                if (shelves.history.isNotEmpty()) {
                    FoldSection(
                        title = stringResource(R.string.search_history_title),
                        hint = stringResource(R.string.search_history_hint),
                        modifier = below(Modifier.onGloballyPositioned { historyTop = it.positionInParent().y }) { historyTop },
                    ) {
                        LikhtarRail {
                            itemsIndexed(shelves.history, key = { _, card -> card.id }) { _, card ->
                                ResumeTile(
                                    title = card.title,
                                    subtitle = cardNote(card, androidx.compose.ui.platform.LocalResources.current),
                                    imageUrl = card.imageUrl,
                                    logoUrl = card.logoUrl,
                                    progress = card.continueItem?.takeIf { !it.finished }?.progress,
                                    onClick = { playCard(card) },
                                    onLongClick = card.continueItem?.let { { onForget(card) } },
                                    modifier = Modifier.cardSlot().width(242.dp),
                                )
                            }
                        }
                    }
                }
                // Лише поле внизу: останній ряд стає до низу екрана, а не під меню з порожнечею під ним
                // (560 dp запасу були під постери 80×120).
                Spacer(Modifier.height(24.dp))
            }
        }
        // .topshade: під меню — тло, щоб ряди під ним не читались крізь нього.
        // Щойно сторінка зрушила — тінь: інакше постери проїжджали під пігулками меню (меню тепер вище, 72 dp).
        val shade by animateFloatAsState(if (down || scroll.value > 0) 1f else 0f, tween(300), label = "shade")
        Box(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .graphicsLayer { alpha = shade }
                .background(Brush.verticalGradient(0.82f to LikhtarColors.Background, 1f to LikhtarColors.Background.copy(alpha = 0f))),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp),
        color = LikhtarColors.TextDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = 3.dp),
    )
}

/** .chip прототипу: 58 px, поле 26 px, 23 px / 600; вибрана — світліша з бурштиновою цяткою. */
@Composable
internal fun PillChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    muted: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(29.dp),
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = when {
            muted -> Color.Transparent
            on -> LikhtarColors.Paper.copy(alpha = 0.16f)
            else -> LikhtarColors.Surface.copy(alpha = 0.85f)
        },
        focusedBackground = LikhtarColors.Paper,
        borderColor = Color.Transparent,
        focusedScale = 1.05f,
        instantFill = true,
    ) { focused ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 13.dp),
        ) {
            if (on) {
                Box(
                    Modifier
                        .size(4.5.dp)
                        .drawBehind {
                            if (!focused) drawCircle(LikhtarColors.Amber.copy(alpha = 0.4f), radius = size.minDimension)
                            drawCircle(if (focused) LikhtarColors.AmberOnPaper else LikhtarColors.Glow)
                        },
                )
            }
            if (icon != null) Icon(painterResource(icon), null, tint = if (focused) LikhtarColors.InkOnPaper else LikhtarColors.TextDim, modifier = Modifier.size(12.dp))
            Text(
                text,
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp),
                color = when {
                    focused -> LikhtarColors.Background
                    muted -> LikhtarColors.TextDim
                    else -> LikhtarColors.TextPrimary
                },
                maxLines = 1,
            )
        }
    }
}

/**
 * Ряд порад (.rows): заголовок 28 px, рядок «чому» з паличкою, постери 160 px з оцінкою;
 * групи («Серіали») — за тонкою рискою з підписом над першою карткою.
 */
@Composable
private fun PicksRail(
    title: String,
    hint: String,
    groups: List<Pair<String?, List<CatalogItem>>>,
    why: Map<String, String>,
    focused: CatalogItem?,
    onFocused: (CatalogItem) -> Unit,
    open: (CatalogItem) -> Unit,
    onLongClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(title, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = Color.White)
        val line = focused?.let { f -> why[f.id]?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.search_why_line, f.title, it) } }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(top = 1.dp).height(17.dp),
        ) {
            // Висота рядка лишається (17 dp) — ряд постерів не стрибає, коли пояснення немає.
            if ((line ?: hint).isNotBlank()) Icon(painterResource(DsR.drawable.ic_solar_magic), null, tint = LikhtarColors.Glow, modifier = Modifier.size(11.dp))
            Text(
                line ?: hint,
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp),
                color = if (line != null) LikhtarColors.TextPrimary else LikhtarColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LikhtarRail(gap = 13.dp) {
            groups.forEachIndexed { g, (label, items) ->
                if (g > 0) {
                    item(key = "gap-$g") {
                        Box(Modifier.padding(top = 17.dp, start = 7.dp, end = 7.dp).width(0.75.dp).height(PosterHeight).background(LikhtarColors.Faint))
                    }
                }
                itemsIndexed(items, key = { _, item -> "$g-${item.id}" }) { i, item ->
                    Column {
                        // .sublabel: над першою карткою групи, 21 px приглушено.
                        Box(Modifier.height(if (groups.any { it.first != null }) 17.dp else 0.dp)) {
                            if (i == 0 && label != null) Text(label, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = LikhtarColors.TextDim, maxLines = 1)
                        }
                        CatalogPosterCard(
                            title = item.title,
                            year = item.year,
                            posterUrl = TmdbImages.poster(item.posterPath),
                            rating = item.rating,
                            width = PosterWidth,
                            height = PosterHeight,
                            onClick = { open(item) },
                            onLongClick = { onLongClick(item) },
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).onFocusChanged { if (it.isFocused) onFocused(item) },
                        )
                    }
                }
            }
        }
    }
}

/** Секція під згином (.more section): заголовок 28 px, підказка 21 px, ряд. */
@Composable
private fun FoldSection(title: String, hint: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(start = LikhtarDimens.ScreenPadding, bottom = 18.dp)) {
        SectionTitle(title, modifier = Modifier.padding(bottom = 0.dp))
        if (hint.isNotBlank()) {
            Text(
                hint,
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp),
                color = LikhtarColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        content()
    }
}

@Composable
private fun PosterRow(
    items: List<CatalogItem>,
    open: (CatalogItem) -> Unit,
    onLongClick: (CatalogItem) -> Unit,
    firstFocus: FocusRequester? = null,
    ua: Boolean = false,
) {
    LikhtarRail(gap = 13.dp) {
        itemsIndexed(items, key = { _, item -> item.id }) { i, item ->
            CatalogPosterCard(
                title = item.title,
                year = item.year,
                posterUrl = TmdbImages.poster(item.posterPath),
                rating = item.rating,
                uaAudio = if (ua) true else null,
                width = PosterWidth,
                height = PosterHeight,
                onClick = { open(item) },
                onLongClick = { onLongClick(item) },
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .then(if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier),
            )
        }
    }
}

@Composable
internal fun formatLabel(f: WatchFormat): String = stringResource(
    when (f) {
        WatchFormat.MOVIE -> R.string.search_format_movie
        WatchFormat.TV -> R.string.search_format_tv
        WatchFormat.ANIME -> R.string.search_format_anime
        WatchFormat.CARTOON -> R.string.search_format_cartoon
        WatchFormat.DOC -> R.string.search_format_doc
        WatchFormat.REALITY -> R.string.search_format_reality
    },
)

@Composable
internal fun moodLabel(m: WatchMood): String = stringResource(
    when (m) {
        WatchMood.LIGHT -> R.string.search_mood_light
        WatchMood.TENSE -> R.string.search_mood_tense
        WatchMood.SMART -> R.string.search_mood_smart
        WatchMood.FUNNY -> R.string.search_mood_funny
        WatchMood.ROMANTIC -> R.string.search_mood_romantic
        WatchMood.MYSTERY -> R.string.search_mood_mystery
        WatchMood.ADVENTURE -> R.string.search_mood_adventure
    },
)

@Composable
private fun formatPhrase(f: WatchFormat?): String = f?.let { formatLabel(it) } ?: stringResource(R.string.search_format_any)

@Composable
private fun moodWord(m: WatchMood): String = stringResource(
    when (m) {
        WatchMood.LIGHT -> R.string.search_mood_word_light
        WatchMood.TENSE -> R.string.search_mood_word_tense
        WatchMood.SMART -> R.string.search_mood_word_smart
        WatchMood.FUNNY -> R.string.search_mood_word_funny
        WatchMood.ROMANTIC -> R.string.search_mood_word_romantic
        WatchMood.MYSTERY -> R.string.search_mood_word_mystery
        WatchMood.ADVENTURE -> R.string.search_mood_word_adventure
    },
)

@Composable
private fun advisorStatus(a: AdvisorUiState): String = when {
    a.loading -> stringResource(R.string.search_advisor_picking)
    a.format == null && a.mood == null -> stringResource(R.string.search_advisor_ask)
    a.mood == null -> formatPhrase(a.format)
    else -> stringResource(R.string.search_advisor_status, formatPhrase(a.format), moodWord(a.mood))
}

/** «На одну серію — те, що триматиме до кінця. Ось чотири поради, з першої я б і почав.» */
@Composable
private fun advisorThought(a: AdvisorUiState): String {
    if (a.loading) return ""
    if (a.format == null && a.mood == null) return stringResource(R.string.search_advisor_ask_text)
    val time = formatPhrase(a.format)
    val lead = stringResource(
        when (a.mood) {
            null -> R.string.search_lead_any
            WatchMood.LIGHT -> R.string.search_lead_light
            WatchMood.TENSE -> R.string.search_lead_tense
            WatchMood.SMART -> R.string.search_lead_smart
            WatchMood.FUNNY -> R.string.search_lead_funny
            WatchMood.ROMANTIC -> R.string.search_lead_romantic
            WatchMood.MYSTERY -> R.string.search_lead_mystery
            WatchMood.ADVENTURE -> R.string.search_lead_adventure
        },
    )
    if (a.picks.isEmpty() && a.offline) return stringResource(R.string.search_advisor_offline)
    if (a.picks.isEmpty()) return stringResource(R.string.search_advisor_none, time, lead)
    return stringResource(R.string.search_advisor_thought, time, lead, pluralStringResource(R.plurals.search_advice_count, a.picks.size, a.picks.size))
}

@Composable
private fun searchStatus(state: SearchUiState): String {
    val best = state.best?.takeIf { it in state.ai && state.descriptive } ?: state.best?.takeIf { !state.aiPending }
    return when {
        state.aiPending || state.status == SearchStatus.LOADING -> stringResource(R.string.search_thought_pending, state.query.trim()) + "…"
        best != null -> stringResource(R.string.search_thought_best, best.title)
        state.status == SearchStatus.EMPTY -> stringResource(R.string.search_empty, state.query.trim())
        else -> ""
    }
}

/** «Серіал · 2024 · ★ 7.8» — для поради радника береться його власний підпис. */
@Composable
private fun spotMeta(item: CatalogItem, candidate: Candidate?): String {
    val kind = stringResource(if (item.kind == TitleKind.TV) R.string.search_kind_series else R.string.search_kind_movie)
    val rating = item.rating.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "★ %.1f", it) }
    val head = candidate?.meta?.takeIf { it.isNotBlank() } ?: listOf(kind, item.year).filter(String::isNotBlank).joinToString(" · ")
    return listOfNotNull(head, rating).joinToString(" · ")
}



/** Під назвою секції гри: де ти в раунді дня або чим він скінчився. */
@Composable
private fun gameHint(game: com.likhtar.home.feature.cinema.TriviaUi): String {
    val d = game.day
    return when {
        game.inRound -> stringResource(com.likhtar.home.feature.cinema.R.string.trivia_round, (d.answers.size + if (game.answered != null) 0 else 1).coerceAtMost(d.round.size), d.round.size)
        d.done -> listOf(
            stringResource(com.likhtar.home.feature.cinema.R.string.trivia_done, d.score, d.round.size),
            stringResource(com.likhtar.home.feature.cinema.R.string.trivia_streak, d.streak),
            stringResource(com.likhtar.home.feature.cinema.R.string.trivia_done_line),
        ).joinToString(" · ")
        else -> ""
    }
}

/** Гра дня: твердження зліва, кадр тайтла справа — як герой, але карткою в стрічці. */
@Composable
private fun GameCard(
    game: com.likhtar.home.feature.cinema.TriviaUi,
    statement: com.likhtar.home.core.catalog.TriviaStatement,
    onAnswer: (Boolean) -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    Box(
        Modifier.padding(top = 10.dp, end = LikhtarDimens.ScreenPadding).fillMaxWidth().height(250.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LikhtarColors.Surface)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
    ) {
        coil3.compose.AsyncImage(
            model = com.likhtar.home.core.catalog.TmdbImages.hero(statement.item.backdropPath),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.62f),
        )
        Box(
            Modifier.matchParentSize().background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    // Кадр починається на 38 %: до нього тло суцільне, далі гасне — без шва на краю картинки.
                    0.4f to LikhtarColors.BackgroundDeep,
                    0.6f to LikhtarColors.BackgroundDeep.copy(alpha = 0.6f),
                    0.85f to LikhtarColors.BackgroundDeep.copy(alpha = 0f),
                ),
            ),
        )
        com.likhtar.home.feature.cinema.TriviaPanel(
            statement = statement,
            answered = game.answered,
            kicker = gameHint(game).ifBlank { stringResource(com.likhtar.home.feature.cinema.R.string.trivia_kicker) },
            onAnswer = onAnswer,
            nextLabel = stringResource(
                if (game.inRound && game.day.answers.size < game.day.round.size) com.likhtar.home.feature.cinema.R.string.trivia_next
                else com.likhtar.home.feature.cinema.R.string.trivia_more,
            ),
            onNext = onNext,
            onOpen = onOpen,
            firstFocus = focus,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 26.dp),
        )
    }
}
