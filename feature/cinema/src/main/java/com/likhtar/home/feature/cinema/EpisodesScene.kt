package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.designsystem.VoiceBadge
import com.likhtar.home.core.designsystem.ChipKind
import com.likhtar.home.core.designsystem.MetaChip
import com.likhtar.home.core.designsystem.CatalogPosterCard
import com.likhtar.home.core.catalog.TitleVideo
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.Episode
import com.likhtar.home.core.designsystem.LikhtarRail
import androidx.compose.foundation.layout.aspectRatio
import com.likhtar.home.core.catalog.TitleDetails
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.requestAfterFrame
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalFocusManager
import com.likhtar.home.core.designsystem.FocusRing
import com.likhtar.home.core.designsystem.ProtoCardCorner
import com.likhtar.home.core.designsystem.focusGlow

/**
 * Друга сцена сторінки серіалу.
 *
 * Її не кладемо нижче hero у спільний список: на TV користувач сприймає ↓ як перехід
 * на інший екран. Тому тут немає ані назви, ані опису з першої сцени — лише сезон і серії.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun EpisodesScene(
    details: TitleDetails,
    state: TitleUiState,
    firstSeasonFocus: FocusRequester,
    resumeEpisodeFocus: FocusRequester,
    episodeRail: LazyListState,
    onExit: () -> Unit,
    onSeason: (Int) -> Unit,
    onEpisode: (Episode) -> Unit,
    onVideo: (TitleVideo) -> Unit,
    onOpenTitle: (CatalogItem) -> Unit,
    onFranchiseParts: (Int) -> Unit,
    /** «Що було в N−1 сезоні» першим рядком серій; `null` — рядка немає (шоу Телепорталу). */
    onRecap: ((Int) -> Unit)? = null,
    /** Вкладка, з якої почати; `null` — перша. */
    startTab: TitleTab? = null,
    /** Підпис сезону. У серіалі TMDB це номер, у шоу Телепорталу — власна назва
     *  («Сезон 2026», «Сила таланту»), і вигадувати замість неї номер означало б брехати. */
    seasonLabel: ((Int) -> String)? = null,
    /**
     * Як підписати рядок випуску. У серіалі номер серії несе зміст і стоїть спереду, а в
     * шоу Телепорталу назва вже містить «Випуск 3», і номер давав би «1. Випуск 3».
     * Дату там натомість має кожен випуск — і саме за нею шоу й дивляться.
     */
    episodeNumbers: Boolean = true,
    episodeDates: Boolean = false,
    /**
     * Скільки місця дано назві вгорі. Для логотипа цього досить, а назва текстом у ту саму
     * третину екрана не влазить: «Кохання на виживання» ставало «Кохання на вижива…».
     */
    titleWidth: Float = 0.34f,
    /**
     * Стеля опису в «Деталях». У Кінозалі синопсис TMDB короткий, а опис Телепорталу — це
     * SEO-текст на дві тисячі знаків, який заливав увесь екран і йшов за нижній край.
     */
    overviewMaxLines: Int = Int.MAX_VALUE,
    /** Чим підписати склад. «У ролях» про ведучих шоу — неправда, якої видно з дивана. */
    castLabel: String = stringResource(R.string.cinema_cast),
    /** Канал, на якому це виходить. Порожньо — поля в «Деталях» просто не буде. */
    channel: String = "",
) {
    val tabs = titleTabs(details)
    val firstTab = startTab?.takeIf { it in tabs } ?: tabs.first()
    var tab by remember(details.item.tmdbId, firstTab) { mutableStateOf(firstTab) }
    // ↑ з вмісту — на АКТИВНУ вкладку.
    val tabFocus = remember(tabs, firstTab) {
        tabs.associateWith { if (it == firstTab) firstSeasonFocus else FocusRequester() }
    }
    // Курсор списку серій живе тут, а не у вкладці: на нього веде ↓ з вкладок (прототип:
    // «з вкладок на Серіях — одразу на наступну серію») і вхід у вкладку після гортання.
    val cursorFocus = remember(state.season) { FocusRequester() }
    val paneFocus = remember { FocusRequester() }
    var paneAsk by remember { mutableStateOf(0) }
    LaunchedEffect(paneAsk) { if (paneAsk > 0) paneFocus.requestAfterFrame() }
    val focusManager = LocalFocusManager.current
    val switchTab: (Int) -> Boolean = { dir ->
        val next = tabs.getOrNull(tabs.indexOf(tab) + dir)
        if (next != null) {
            tab = next
            paneAsk += 1
            true
        } else {
            false
        }
    }
    // .pane.page: вкладка в'їжджає з боку руху (.26s) — лише прозорість, без зсуву шару над фокусом.
    val pageAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(tab) {
        pageAlpha.snapTo(0f)
        pageAlpha.animateTo(1f, androidx.compose.animation.core.tween(260))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // .mini: знак тайтла 104 px заввишки, 56 px від верху, по центру.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp)
                .height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            TitleLogoArea(
                logoPath = details.logoPath,
                title = details.item.title,
                height = 52.dp,
                widthFraction = titleWidth,
                alignment = Alignment.Center,
            )
        }
        // .tabs: 200 px від верху на сцені серій; ліворуч −22 px, щоб текст стояв по полю.
        Column(modifier = Modifier.padding(start = LikhtarDimens.ScreenPadding, top = 20.dp)) {
            ProtoTabsRow(
                tabs = tabs,
                active = tab,
                modifier = Modifier
                    .offset(x = (-11).dp)
                    .focusProperties { enter = { tabFocus[tab] ?: FocusRequester.Default } }
                    .focusGroup(),
                tabModifier = { entry ->
                    Modifier
                        .focusRequester(tabFocus.getValue(entry))
                        .focusProperties { if (entry == TitleTab.EPISODES && tab == TitleTab.EPISODES) down = cursorFocus }
                },
                onClick = { tab = it },
                onExitUp = onExit,
            )
            // .rule: 1 px, .2, до правого поля.
            Box(
                Modifier
                    .padding(end = LikhtarDimens.ScreenPadding)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LikhtarColors.Paper.copy(alpha = 0.2f)),
            )
        }

        // .panel: margin-top 24 px; ← → за краєм ряду — сусідня вкладка, фокус у її вміст.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp)
                .graphicsLayer { alpha = pageAlpha.value }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val dir = when (event.key) {
                        Key.DirectionRight -> 1
                        Key.DirectionLeft -> -1
                        else -> return@onKeyEvent false
                    }
                    if (focusManager.moveFocus(if (dir > 0) FocusDirection.Right else FocusDirection.Left)) true else switchTab(dir)
                }
                .focusRequester(paneFocus)
                .focusProperties { enter = { if (tab == TitleTab.EPISODES) cursorFocus else FocusRequester.Default } }
                .focusGroup(),
        ) {
            when (tab) {
                TitleTab.EPISODES -> {
                    val episodeScroll = rememberCoroutineScope()
                    // Ряд сезонів — нульовий у списку (.seasons усередині .list), далі
                    // «Що було в N−1 сезоні», далі серії.
                    val recapBefore = if (onRecap != null && state.episodes.isNotEmpty()) state.recapBefore else 0
                    val lead = 1 + if (recapBefore > 0) 1 else 0
                    val rows = lead + state.episodes.size
                    // Курсор стартує з серії, з якої продовжувати (вона ж «наступна»).
                    val resumeRow = state.episodes.indexOfFirst { it.number == state.resumeEpisode }
                        .takeIf { it >= 0 && state.season == state.resumeSeason }?.plus(lead)
                        ?: if (state.episodes.isNotEmpty()) lead else 0
                    var cursor by remember(state.season, rows, resumeRow) { mutableStateOf(resumeRow) }
                    val anchorPx = with(androidx.compose.ui.platform.LocalDensity.current) { 30.dp.roundToPx() }
                    LaunchedEffect(cursor, state.episodes.size, state.recap == null) {
                        if (state.recap != null) return@LaunchedEffect
                        // [data-scroll="60"]: рядок у фокусі — на 60 px від верху панелі, вище
                        // лишається попередня серія. Сезони — коли курсор на них — угорі.
                        val offset = if (cursor == 0) 0 else -anchorPx
                        val row = episodeRail.layoutInfo.visibleItemsInfo.firstOrNull { it.index == cursor }
                        if (row == null) {
                            episodeRail.scrollToItem(cursor, offset)
                            cursorFocus.requestAfterFrame()
                        } else {
                            cursorFocus.requestAfterFrame()
                            if (row.offset != -offset) episodeRail.animateScrollToItem(cursor, offset)
                        }
                    }
                    val move: (Int) -> Boolean = { step ->
                        val next = cursor + step
                        if (next in 0 until rows) {
                            cursor = next
                            true
                        } else {
                            false
                        }
                    }
                    LazyColumn(
                        state = episodeRail,
                        contentPadding = PaddingValues(start = LikhtarDimens.ScreenPadding, end = LikhtarDimens.ScreenPadding, bottom = 300.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "seasons") {
                            // .seasons: пігулки сезонів, gap 12 px, padding 4 / 22 px.
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(top = 2.dp, bottom = 11.dp),
                                modifier = Modifier.onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) move(+1) else false
                                },
                            ) {
                                items(details.seasonNumbers, key = { it }) { season ->
                                    SeasonPill(
                                        label = seasonLabel?.invoke(season) ?: stringResource(R.string.cinema_season_n, season),
                                        count = if (state.season == season) state.episodes.size else 0,
                                        active = state.season == season,
                                        onClick = { onSeason(season) },
                                        modifier = if (state.season == season && cursor == 0) Modifier.focusRequester(cursorFocus) else Modifier,
                                        onFocused = { cursor = 0 },
                                    )
                                }
                            }
                        }
                        if (recapBefore > 0) item(key = "recap") {
                            val video = state.recapVideos[recapBefore]
                            RecapRow(
                                season = recapBefore,
                                video = video != null,
                                onClick = { if (video != null) onVideo(video) else onRecap?.invoke(recapBefore) },
                                cursorFocus = cursorFocus.takeIf { cursor == 1 },
                                onMove = move,
                            )
                        }
                        itemsIndexed(state.episodes, key = { _, episode -> episode.number }) { i, episode ->
                            val index = i + lead
                            val watch = episode.watchState(
                                displayedSeason = state.season,
                                resumeSeason = state.resumeSeason,
                                resumeEpisode = state.resumeEpisode,
                            )
                            EpisodeRow(
                                episode = episode,
                                showNumber = episodeNumbers,
                                showDate = episodeDates,
                                translated = episode.number in state.translated,
                                focus = resumeEpisodeFocus.takeIf {
                                    episode.number == state.resumeEpisode && state.season == state.resumeSeason
                                },
                                watchState = watch,
                                next = watch == EpisodeWatchState.IN_PROGRESS,
                                // Серія, що ще не вийшла, не запускається (Темна матерія S02E07).
                                onClick = { if (!episode.upcoming) onEpisode(episode) },
                                cursorFocus = cursorFocus.takeIf { index == cursor },
                                onMove = move,
                            )
                        }
                    }
                }

                TitleTab.WORTH -> Box(Modifier.padding(start = LikhtarDimens.ScreenPadding)) { WorthTab(card = details.card) }

                TitleTab.DETAILS -> Box(Modifier.padding(start = LikhtarDimens.ScreenPadding)) {
                    DetailsTab(
                        details = details,
                        overviewMaxLines = overviewMaxLines,
                        castLabel = castLabel,
                        channel = channel,
                    )
                }

                TitleTab.VIDEOS -> LikhtarRail(modifier = Modifier.padding(start = LikhtarDimens.ScreenPadding), gap = 14.dp) {
                    items(details.videos, key = { it.key }) { video ->
                        VideoCard(video = video, onClick = { onVideo(video) })
                    }
                }

                TitleTab.SIMILAR -> LikhtarRail(modifier = Modifier.padding(start = LikhtarDimens.ScreenPadding, top = 4.dp), gap = 14.dp) {
                    items(details.similar, key = { it.id }) { similar ->
                        CatalogPosterCard(
                            title = similar.title,
                            year = similar.year,
                            posterUrl = TmdbImages.poster(similar.posterPath),
                            rating = similar.rating,
                            onClick = { onOpenTitle(similar) },
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }

                TitleTab.FRANCHISE -> details.franchise?.let { franchise ->
                    LaunchedEffect(franchise.id) { onFranchiseParts(franchise.id) }
                    LikhtarRail(modifier = Modifier.padding(start = LikhtarDimens.ScreenPadding, top = 4.dp), gap = 14.dp) {
                        items(state.franchise, key = { it.id }) { part ->
                            CatalogPosterCard(
                                title = part.title,
                                year = part.year,
                                posterUrl = TmdbImages.poster(part.posterPath),
                                rating = part.rating,
                                onClick = { onOpenTitle(part) },
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Набір табів матеріалу — один на сцену серій і на її визирання з першої сцени. Досі
 * визирання збирало свій список у іншому порядку («Подібне» перед «Деталями»), і
 * вкладки мінялись місцями, щойно ↓ відкривав сцену.
 */
internal fun titleTabs(details: TitleDetails): List<TitleTab> = buildList {
    if (details.seasonNumbers.isNotEmpty()) add(TitleTab.EPISODES)
    if (details.card.hasVerdict) add(TitleTab.WORTH)
    add(TitleTab.DETAILS)
    if (details.videos.isNotEmpty()) add(TitleTab.VIDEOS)
    if (details.similar.isNotEmpty()) add(TitleTab.SIMILAR)
    if (details.franchise != null) add(TitleTab.FRANCHISE)
}

/** Таби сцени матеріалу. Порядок оголошення — порядок на екрані. */
internal enum class TitleTab(@androidx.annotation.StringRes val title: Int) {
    EPISODES(R.string.cinema_tab_episodes),
    WORTH(R.string.cinema_tab_worth),
    DETAILS(R.string.cinema_tab_details),
    VIDEOS(R.string.cinema_tab_videos),
    SIMILAR(R.string.cinema_tab_similar),
    FRANCHISE(R.string.cinema_tab_franchise),
}

/**
 * Ряд вкладок (.tabs прототипу): пігулки 26 px / 600 з полями 12/22/16 px; активна —
 * біла з рискою 3 px знизу, у фокусі — тепла біла заливка; «Чи варто?» — з вогником.
 * Без [onClick] — лише вид (визирання з першої сцени).
 */
@Composable
internal fun ProtoTabsRow(
    tabs: List<TitleTab>,
    active: TitleTab?,
    modifier: Modifier = Modifier,
    tabModifier: (TitleTab) -> Modifier = { Modifier },
    onClick: ((TitleTab) -> Unit)? = null,
    onExitUp: () -> Unit = {},
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        tabs.forEach { entry ->
            var focused by remember { mutableStateOf(false) }
            val on = entry == active
            Box(
                modifier = tabModifier(entry)
                    .then(
                        if (onClick == null) Modifier else Modifier
                            // Вкладка міняється фокусом, як на Apple TV: по OK вміст не встигав за
                            // курсором, і було незрозуміло, що відкрито.
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused && entry != active) onClick(entry)
                            }
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (event.key) {
                                    Key.DirectionUp -> { onExitUp(); true }
                                    Key.DirectionCenter, Key.Enter -> { onClick(entry); true }
                                    else -> false
                                }
                            },
                    )
                    .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                    .background(if (focused) LikhtarColors.Paper else Color.Transparent)
                    .padding(start = 11.dp, end = 11.dp, top = 6.dp, bottom = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry == TitleTab.WORTH) {
                        // .lamp: 9 px бурштину з теплим ореолом.
                        Box(
                            Modifier
                                .padding(end = 5.dp, top = 1.dp)
                                .size(4.5.dp)
                                .drawBehind {
                                    // box-shadow 0 0 12px 3px rgba(255,210,122,.55): м'який ореол.
                                    val r = 7.5.dp.toPx()
                                    drawCircle(
                                        androidx.compose.ui.graphics.Brush.radialGradient(
                                            0f to LikhtarColors.Glow.copy(alpha = 0.55f), 0.3f to LikhtarColors.Glow.copy(alpha = 0.55f), 1f to Color.Transparent,
                                            center = center, radius = r,
                                        ),
                                        radius = r,
                                    )
                                    drawCircle(LikhtarColors.Glow)
                                },
                        )
                    }
                    Text(
                        text = stringResource(entry.title),
                        style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                        color = when {
                            focused -> LikhtarColors.Background
                            on -> Color.White
                            else -> LikhtarColors.TextDim
                        },
                    )
                }
                if (on) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .padding(start = 0.dp, bottom = 0.dp)
                            .drawBehind {
                                val inset = 11.dp.toPx()
                                val h = 1.5.dp.toPx()
                                val y = size.height + 8.dp.toPx() - 2.dp.toPx() - h
                                drawRoundRect(
                                    if (focused) LikhtarColors.Background else LikhtarColors.Paper,
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                                    size = androidx.compose.ui.geometry.Size(size.width, h),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h),
                                )
                                inset.hashCode()
                            },
                    )
                }
            }
        }
    }
}

/** .pill прототипу: «Сезон 2 · 10 серій», 60 px заввишки; вибраний — з рамкою, фокус — тепла біла. */
@Composable
private fun SeasonPill(
    label: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .height(30.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = LikhtarColors.Surface.copy(alpha = 0.85f),
        focusedBackground = LikhtarColors.Paper,
        borderColor = if (active) LikhtarColors.Paper.copy(alpha = 0.5f) else Color.Transparent,
        focusedScale = 1.04f,
        instantFill = true,
    ) { focused ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 13.dp),
        ) {
            Text(
                label,
                style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp),
                color = if (focused) LikhtarColors.Background else LikhtarColors.TextPrimary,
                maxLines = 1,
            )
            if (count > 0) {
                Text(
                    pluralStringResource(R.plurals.cinema_episodes_count, count, count),
                    style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Mono, fontWeight = FontWeight.Medium, fontSize = 9.sp),
                    color = if (focused) LikhtarColors.AmberMuted else LikhtarColors.TextDim,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Серія рядком (.ep прототипу): кадр 352 px із кільцем у фокусі, «8. Назва» 30 px, чипи
 * «нова · дата» / «вийде дата» бурштином і «N хв» моно, опис 23 px у два рядки.
 * Наступна серія — під теплим відсвітом і з бурштиновою рамкою кадру; майбутня — блідий кадр.
 */
@Composable
private fun EpisodeRow(
    episode: Episode,
    showNumber: Boolean,
    showDate: Boolean,
    translated: Boolean,
    focus: FocusRequester?,
    watchState: EpisodeWatchState,
    next: Boolean,
    onClick: () -> Unit,
    cursorFocus: FocusRequester? = null,
    onMove: (Int) -> Boolean = { false },
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .drawBehind {
                if (!next) return@drawBehind
                // .ep.next::before: 900×420 px з −220/−60 px, radial closest-side .32 → .1 → 0.
                val w = 450.dp.toPx()
                val h = 210.dp.toPx()
                val left = -110.dp.toPx()
                val top = -30.dp.toPx()
                val c = androidx.compose.ui.geometry.Offset(left + w / 2, top + h / 2)
                scale(scaleX = w / h, scaleY = 1f, pivot = c) {
                    drawCircle(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            0f to LikhtarColors.Spill.copy(alpha = 0.32f), 0.55f to LikhtarColors.Amber.copy(alpha = 0.1f), 1f to Color.Transparent,
                            center = c, radius = h / 2,
                        ),
                        radius = h / 2,
                        center = c,
                    )
                }
            },
    ) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier
                .offset(x = (-6).dp)
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> onMove(+1)
                        Key.DirectionUp -> onMove(-1)
                        else -> false
                    }
                }
                .then(cursorFocus?.let { Modifier.focusRequester(it) } ?: Modifier)
                .then(focus?.let { Modifier.focusRequester(it) } ?: Modifier),
            shape = RoundedCornerShape(12.dp),
            background = Color.Transparent,
            focusedBackground = LikhtarColors.Faint,
            borderColor = Color.Transparent,
            focusedScale = 1f,
            focusLighten = 0f,
            instantFill = true,
        ) { _ ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(6.dp),
            ) {
                // .still: 352 px 16:9, скруглення 16 px; у фокусі — кільце 5 px і тепла тінь.
                Box(
                    modifier = Modifier
                        .width(EpisodeStillWidth)
                        .aspectRatio(16f / 9f)
                        .focusGlowWhen(focused, 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(LikhtarColors.Surface)
                        .then(if (next && !focused) Modifier.border(1.dp, LikhtarColors.Glow.copy(alpha = 0.7f), RoundedCornerShape(8.dp)) else Modifier),
                ) {
                    AsyncImage(
                        model = TmdbImages.still(episode.stillPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(if (episode.upcoming) 0.5f else 1f),
                    )
                    if (watchState == EpisodeWatchState.FINISHED) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(LikhtarColors.Amber),
                        )
                    }
                    if (focused) Box(Modifier.matchParentSize().border(2.5.dp, LikhtarColors.Paper, RoundedCornerShape(8.dp)))
                }
                Column(modifier = Modifier.weight(1f).padding(top = 3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(
                            // «9. Серія 9» не пишемо — номер лише перед власною назвою.
                            text = if (episode.hasOwnTitle && showNumber) "${episode.number}. ${episode.title}" else episode.title,
                            style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (translated) VoiceBadge()
                        val day = humanDate(episode.airDate).substringBeforeLast(' ')
                        when {
                            episode.upcoming -> ProtoChip(stringResource(R.string.cinema_episode_airs_on, day), amber = true, height = 19.dp, fontSize = 9.5.sp)
                            next && episode.airDate.isNotBlank() -> ProtoChip(stringResource(R.string.cinema_episode_new_on, day), amber = true, height = 19.dp, fontSize = 9.5.sp)
                            showDate && episode.airDate.isNotBlank() -> ProtoChip(humanDate(episode.airDate), mono = true, height = 19.dp, fontSize = 9.5.sp)
                        }
                        if (episode.runtimeMinutes > 0) {
                            ProtoChip(stringResource(R.string.cinema_minutes_short, episode.runtimeMinutes), mono = true, height = 19.dp, fontSize = 9.5.sp)
                        }
                    }
                    if (episode.overview.isNotBlank()) {
                        Text(
                            text = episode.overview,
                            style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp, lineHeight = 16.7.sp),
                            color = LikhtarColors.TextDim,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp).widthIn(max = 520.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Тепла тінь під кадром серії у фокусі — той самий focusGlow, але від стану рядка. */
@Composable
private fun Modifier.focusGlowWhen(focused: Boolean, corner: androidx.compose.ui.unit.Dp): Modifier {
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    return drawBehind {
        if (!focused || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return@drawBehind
        paint.color = android.graphics.Color.argb(140, 242, 169, 59)
        paint.maskFilter = android.graphics.BlurMaskFilter(30.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
        val inset = 8.dp.toPx()
        val dy = 13.dp.toPx()
        drawIntoCanvas {
            it.nativeCanvas.drawRoundRect(inset, inset + dy, size.width - inset, size.height - inset + dy, corner.toPx(), corner.toPx(), paint)
        }
    }
}

/**
 * «Що було в N сезоні» (.recap прототипу): плашка .6 з годинником бурштином, назва 27 px і
 * підпис 22 px; фокус — світліша плашка.
 */
@Composable
private fun RecapRow(
    season: Int,
    video: Boolean,
    onClick: () -> Unit,
    cursorFocus: FocusRequester?,
    onMove: (Int) -> Boolean,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .padding(bottom = 7.dp)
            .fillMaxWidth()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> onMove(+1)
                    Key.DirectionUp -> onMove(-1)
                    else -> false
                }
            }
            .then(cursorFocus?.let { Modifier.focusRequester(it) } ?: Modifier),
        shape = RoundedCornerShape(12.dp),
        background = LikhtarColors.Surface.copy(alpha = 0.6f),
        focusedBackground = LikhtarColors.Paper.copy(alpha = 0.14f),
        borderColor = Color.Transparent,
        focusedScale = 1f,
        focusLighten = 0f,
        instantFill = true,
    ) { _ ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            androidx.tv.material3.Icon(
                androidx.compose.ui.res.painterResource(com.likhtar.home.core.designsystem.R.drawable.ic_solar_history),
                contentDescription = null,
                tint = LikhtarColors.Glow,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.cinema_recap_what_happened, season),
                    style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
                    color = Color.White,
                )
                Text(
                    text = stringResource(if (video) R.string.cinema_recap_video else R.string.cinema_recap_text),
                    style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.sp),
                    color = LikhtarColors.TextDim,
                )
            }
        }
    }
}

/** Ролик (.card.wide.video прототипу): обкладинка YouTube, значок play по центру, назва й тип. */
@Composable
private fun VideoCard(video: TitleVideo, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 6.dp)
            .width(com.likhtar.home.feature.cinema.CatalogBannerWidth)
            .aspectRatio(16f / 9f)
            .focusGlow(ProtoCardCorner),
        shape = RoundedCornerShape(ProtoCardCorner),
        background = LikhtarColors.CardBlack,
        focusedBackground = LikhtarColors.CardBlack,
        focusedScale = LikhtarDimens.CardFocusScale,
    ) { focused ->
        AsyncImage(
            model = "https://i.ytimg.com/vi/${video.key}/hqdefault.jpg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(0.42f to Color.Transparent, 1f to LikhtarColors.Shade.copy(alpha = 0.85f))),
        )
        androidx.tv.material3.Icon(
            androidx.compose.ui.res.painterResource(com.likhtar.home.core.designsystem.R.drawable.ic_solar_play),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(BiasAlignment(0f, -0.16f)).size(32.dp),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 11.dp, end = 11.dp, bottom = 8.dp)) {
            Text(
                video.name,
                style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 13.sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                video.type,
                style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Mono, fontSize = 8.5.sp),
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        FocusRing(focused, ProtoCardCorner)
    }
}

/**
 * «Деталі» (.details прототипу): опис 26 px до 1180 px із гаслом бурштином, рядок фактів
 * (підпис 20 px, значення 24 px / 600) і ряд «Актори» — портрети 200 px з ім'ям і роллю.
 */
@Composable
private fun DetailsTab(
    details: TitleDetails,
    overviewMaxLines: Int = Int.MAX_VALUE,
    castLabel: String = stringResource(R.string.cinema_cast),
    channel: String = "",
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(end = LikhtarDimens.ScreenPadding)) {
        Text(
            text = details.item.overview.ifBlank { details.card.summary },
            style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.sp, lineHeight = 19.5.sp),
            color = Color.White,
            maxLines = overviewMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 590.dp),
        )
        if (details.tagline.isNotBlank()) {
            Text(
                details.tagline,
                style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.sp, lineHeight = 19.5.sp),
                color = LikhtarColors.Glow,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(top = 13.dp, bottom = 15.dp)) {
            val original = details.item.originalTitle.takeIf { it.isNotBlank() && it != details.item.title }
            original?.let { DetailFact(stringResource(R.string.cinema_detail_original), it) }
            if (details.creator.isNotBlank()) DetailFact(stringResource(R.string.cinema_detail_creator), details.creator)
            else if (details.director.isNotBlank()) DetailFact(stringResource(R.string.cinema_detail_director), details.director)
            (details.network.ifBlank { channel }).takeIf { it.isNotBlank() }?.let { DetailFact(stringResource(R.string.cinema_detail_channel), it) }
            if (details.seasonNumbers.isNotEmpty() && details.episodeCount > 0) {
                DetailFact(stringResource(R.string.cinema_detail_seasons_episodes), "${details.seasonNumbers.size} · ${details.episodeCount}")
            } else if (details.runtimeMinutes > 0) {
                DetailFact(stringResource(R.string.cinema_detail_runtime), stringResource(R.string.cinema_minutes_short, details.runtimeMinutes))
            }
            if (details.premiere.isNotBlank()) DetailFact(stringResource(R.string.cinema_detail_premiere), humanDate(details.premiere))
        }
        val people = details.people.ifEmpty { details.cast.map { com.likhtar.home.core.catalog.CastMember(it) } }
        if (people.isNotEmpty()) {
            com.likhtar.home.core.designsystem.SectionTitle(if (details.people.isNotEmpty()) stringResource(R.string.cinema_actors) else castLabel)
            LikhtarRail(gap = 14.dp) {
                items(people, key = { it.name }) { person -> PersonCard(person) }
            }
        }
    }
}

@Composable
private fun DetailFact(label: String, value: String) {
    Column {
        Text(label, style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontSize = 10.sp), color = LikhtarColors.TextDim, maxLines = 1)
        Text(
            value,
            style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
            color = LikhtarColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** .person: портрет 200 px 2:3, ім'я 21 px над роллю 16 px. Фокус — як у картки. */
@Composable
private fun PersonCard(person: com.likhtar.home.core.catalog.CastMember) {
    FocusableSurface(
        onClick = {},
        modifier = Modifier
            .padding(vertical = 6.dp)
            .width(100.dp)
            .aspectRatio(2f / 3f)
            .focusGlow(ProtoCardCorner),
        shape = RoundedCornerShape(ProtoCardCorner),
        background = LikhtarColors.Surface,
        focusedBackground = LikhtarColors.Surface,
        focusedScale = LikhtarDimens.CardFocusScale,
    ) { focused ->
        person.photoPath?.let {
            AsyncImage(model = TmdbImages.poster(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(0.42f to Color.Transparent, 1f to LikhtarColors.Shade.copy(alpha = 0.85f))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 7.dp)) {
            Text(
                person.name,
                style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, lineHeight = 11.5.sp),
                color = Color.White,
                maxLines = 2,
            )
            if (person.role.isNotBlank()) {
                Text(
                    person.role,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontSize = 8.sp),
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        FocusRing(focused, ProtoCardCorner)
    }
}

/**
 * Логотип на сцені серій: менший за той, що на першій сцені (82dp), але не втричі.
 *
 * Тридцять dp читались як підпис, а не як ідентифікація сторінки — при тому, що це
 * єдине, що каже, чиї це серії. Дві третини від героя — те, що в Apple ([apple-tv/07.webp]).
 */
private val EpisodeStillWidth = 176.dp



/** Каст займає праву третину: ширше — і рядок наїжджає на опис. */

/** API не отримує секунди із зовнішнього плеєра, тому прогрес тут лише чесний стан: почали/закінчили. */
private enum class EpisodeWatchState { NOT_STARTED, IN_PROGRESS, FINISHED }

private fun Episode.watchState(
    displayedSeason: Int,
    resumeSeason: Int,
    resumeEpisode: Int,
): EpisodeWatchState = when {
    displayedSeason != resumeSeason || resumeEpisode <= 0 -> EpisodeWatchState.NOT_STARTED
    number < resumeEpisode -> EpisodeWatchState.FINISHED
    number == resumeEpisode -> EpisodeWatchState.IN_PROGRESS
    else -> EpisodeWatchState.NOT_STARTED
}
