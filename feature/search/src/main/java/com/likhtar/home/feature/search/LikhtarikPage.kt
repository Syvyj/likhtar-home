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
) {
    val searching = state.query.isNotBlank()
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
    val thinking = if (searching) state.aiPending || state.status == SearchStatus.LOADING else advisor.loading
    // Ряд порад у фокусі стає під верхнє меню, як ряди під згином: постери з низом видно цілком
    // 398 — верх ряду, 75 — край меню (anchor).
    val railLift = with(density) { (398 - 75).dp.toPx() }.toInt()
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
    Box(Modifier.fillMaxSize().background(LikhtarColors.Background)) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoView) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().height(678.dp)) {
                    // .ask: з 148 px, поле й три кнопки по 84 px, gap 16 px.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .offset(y = 74.dp)
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
                                onClick = onAdvisor,
                                icon = DsR.drawable.ic_solar_magic,
                                height = 42.dp,
                                modifier = up,
                            )
                        }
                    }

                    // .line2: з 256 px, 64 px заввишки.
                    val line2 = Modifier
                        .offset(y = 128.dp)
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
                    } else {
                        // Сім настроїв у ширину екрана не влазять — ряд прокручується за фокусом.
                        val moodFocus = advisor.mood ?: WatchMood.entries.first()
                        LikhtarRail(gap = 6.dp, modifier = line2) {
                            itemsIndexed(WatchFormat.entries) { i, t ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (i == 0) Label(stringResource(R.string.search_format_question))
                                    PillChip(formatLabel(t), on = t == advisor.format, onClick = { onFormat(t) })
                                }
                            }
                            itemsIndexed(WatchMood.entries) { i, m ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (i == 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Label(stringResource(R.string.search_mood_question))
                                    }
                                    PillChip(
                                        moodLabel(m),
                                        on = m == advisor.mood,
                                        onClick = { onMood(m) },
                                        modifier = if (m == moodFocus) Modifier.focusRequester(startFocus) else Modifier,
                                    )
                                }
                            }
                        }
                    }

                    // .voice: з 352 px, 500×340 px.
                    LikhtarVoice(
                        name = stringResource(R.string.search_likhtarik),
                        status = if (searching) searchStatus(state) else advisorStatus(advisor),
                        text = if (searching) state.aiRefined else advisorThought(advisor),
                        thinking = thinking,
                        modifier = Modifier.offset(x = LikhtarDimens.ScreenPadding, y = 176.dp).width(232.dp),
                    )

                    // .spot: з 640 px до правого поля, 356 px заввишки.
                    val spotItem = if (searching) state.best else spotCandidate?.item
                    if (spotItem != null) {
                        val dim by animateFloatAsState(if (thinking) 0.45f else 1f, tween(500), label = "spotDim")
                        Spot(
                            item = spotItem,
                            kicker = when {
                                !searching -> stringResource(R.string.search_likhtarik_pick)
                                spotItem in state.ai && state.descriptive -> stringResource(R.string.search_likhtarik_pick)
                                else -> stringResource(R.string.search_section_best)
                            },
                            meta = spotMeta(spotItem, spotCandidate),
                            why = if (!searching) spotCandidate?.why.orEmpty() else "",
                            overview = if (searching) spotItem.overview else spotCandidate?.worth?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.search_worth, it) }.orEmpty(),
                            onOpen = { open(spotItem) },
                            onLongClick = { onBookmark(spotItem) },
                            openFocus = if (searching) startFocus else null,
                            modifier = Modifier
                                .offset(y = 176.dp)
                                .padding(start = 296.dp, end = LikhtarDimens.ScreenPadding)
                                .fillMaxWidth()
                                .height(210.dp)
                                .graphicsLayer { alpha = dim }
                                .then(topFocus),
                        )
                    }

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
                                .offset(y = 398.dp)
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
                                    subtitle = cardNote(card, LocalContext.current.resources),
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
                // Лише поле внизу: останній ряд стає до низу екрана, а не під меню з порожнечею під ним
                // (560 dp запасу були під постери 80×120).
                Spacer(Modifier.height(24.dp))
            }
        }
        // .topshade: під меню — тло, щоб ряди під ним не читались крізь нього.
        val shade by animateFloatAsState(if (down) 1f else 0f, tween(300), label = "shade")
        Box(
            Modifier
                .fillMaxWidth()
                .height(75.dp)
                .graphicsLayer { alpha = shade }
                .background(Brush.verticalGradient(0.8f to LikhtarColors.Background, 1f to LikhtarColors.Background.copy(alpha = 0f))),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.sp),
        color = LikhtarColors.TextDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = 3.dp),
    )
}

/** .chip прототипу: 58 px, поле 26 px, 23 px / 600; вибрана — світліша з бурштиновою цяткою. */
@Composable
private fun PillChip(
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
 * .spot: банер найкращого збігу чи поради — кадр праворуч (72 %, center 25 %), текст на
 * графіті: підпис, знак (чужомовний — з назвою Oswald), мета моно, «чому» з паличкою, опис;
 * «Відкрити» — у правому нижньому куті.
 */
@Composable
private fun Spot(
    item: CatalogItem,
    kicker: String,
    meta: String,
    why: String,
    overview: String,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    openFocus: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(LikhtarColors.BackgroundDeep)) {
        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn(tween(300, delayMillis = 160)) togetherWith fadeOut(tween(160)) },
            contentKey = { it.id },
            label = "spot",
            modifier = Modifier.fillMaxSize(),
        ) { shown ->
            Box(Modifier.fillMaxSize()) {
                // Кадр — на весь банер, текст лягає на градієнт.
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = TmdbImages.backdrop(shown.backdropPath) ?: TmdbImages.poster(shown.posterPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = BiasAlignment(0f, -0.5f),
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to LikhtarColors.BackgroundDeep.copy(alpha = 0.96f), 0.38f to LikhtarColors.BackgroundDeep.copy(alpha = 0.72f), 0.72f to LikhtarColors.BackgroundDeep.copy(alpha = 0f))))
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 16.dp).width(470.dp),
                ) {
                    Text(kicker, style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp), color = LikhtarColors.TextDim)
                    val logo = shown.logoPath?.let { TmdbImages.logo(it) ?: it }
                    if (logo != null) {
                        AsyncImage(
                            model = logo,
                            contentDescription = shown.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier.widthIn(max = 280.dp).height(56.dp),
                        )
                    }
                    if (logo == null || TmdbImages.needsLocalTitle(shown.logoPath, shown.title)) {
                        Text(
                            shown.title,
                            style = TextStyle(fontFamily = LikhtarType.Poster, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(meta, style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.sp), color = LikhtarColors.TextDim, maxLines = 1)
                    if (why.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(DsR.drawable.ic_solar_magic), null, tint = LikhtarColors.Glow, modifier = Modifier.size(14.dp))
                            Text(
                                why,
                                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (overview.isNotBlank()) {
                        Text(
                            overview,
                            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 12.5.sp, lineHeight = 17.5.sp),
                            color = LikhtarColors.TextPrimary.copy(alpha = 0.86f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(440.dp),
                        )
                    }
                }
            }
        }
        ProtoButton(
            label = stringResource(R.string.search_open),
            onClick = onOpen,
            onLongClick = onLongClick,
            height = 33.dp,
            padding = 15.dp,
            fontSize = 12.sp,
            iconSize = 14.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 17.dp, bottom = 15.dp)
                .then(if (openFocus != null) Modifier.focusRequester(openFocus) else Modifier),
        )
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
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.sp),
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
                            if (i == 0 && label != null) Text(label, style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 10.5.sp), color = LikhtarColors.TextDim, maxLines = 1)
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
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 10.5.sp),
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
private fun formatLabel(f: WatchFormat): String = stringResource(
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
private fun moodLabel(m: WatchMood): String = stringResource(
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

