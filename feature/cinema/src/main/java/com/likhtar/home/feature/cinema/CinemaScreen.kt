package com.likhtar.home.feature.cinema

import com.likhtar.home.core.designsystem.RailRestore
import com.likhtar.home.core.designsystem.rememberRailRestore

import androidx.compose.ui.res.stringResource
import com.likhtar.home.core.designsystem.PreparingScrim
import com.likhtar.home.core.model.SearchLaunch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.CompositionLocalProvider
import com.likhtar.home.core.designsystem.LocalFocusBlocked
import com.likhtar.home.core.designsystem.cardSlot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import com.likhtar.home.core.designsystem.LikhtarIcons
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.likhtar.home.core.catalog.arranged
import com.likhtar.home.core.catalog.opensListing
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import com.likhtar.home.core.designsystem.focusGlow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.CatalogListingKind
import com.likhtar.home.core.catalog.SectionRow
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.rememberLoaderHold
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarScreenScaffold
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.ResumeTile
import com.likhtar.home.core.designsystem.SectionTitle
import com.likhtar.home.core.designsystem.requestAfterFrame
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.catalog.open

/**
 * Кінозал — режим «ВИБІР», а не полиця.
 *
 * Сторінка відповідає на одне питання: **що подивитись сьогодні ввечері**. Два питання —
 * скільки часу й який настрій — і п'ять кандидатів із чесною причиною для кожного. Двадцять
 * рядків постерів це вітрина: людина йде по ній хвилинами й вимикає телевізор без вибору.
 *
 * Каталог нікуди не подівся: під кандидатами лежать типові добірки, і в кінці кожного ряду
 * «Відкрити всі» веде у звичну сітку.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun CinemaScreen(
    onSelectSection: (LikhtarSection) -> Unit,
    onOpenTitle: (CatalogItem) -> Unit,
    onOpenListing: (CatalogListingKind, String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStreamings: () -> Unit,
    /** Сторінка шоу порталу — для творів українського каталогу без TMDB. */
    onOpenShow: (String) -> Unit = {},
    /** Повна сторінка «Реаліті» з кнопки «Відкрити всі» у ряді Реаліті. */
    onOpenReality: () -> Unit = {},
    viewModel: CinemaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Повернення в розділ — привід зазирнути, чи не застоялось. Сам поріг тримає
    // ViewModel: захід і вихід мережі не коштують.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshIfStale()
        onPauseOrDispose {}
    }
    val menuFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    // Перше, на чому має стояти фокус при відкритті розділу, — кнопка превʼю. Інакше
    // після приходу даних він осідав у колонці радника, і сторінка відкривалась
    // прокрученою вбік.
    // Не `Unit`, а поява слайда: кнопки героя до приходу даних просто немає, і запит
    // фокуса на неіснуючий вузол мовчки падав — фокус лишався у верхньому меню, і розділ
    // відкривався так, ніби він не активний.
    // Кадр очікування обов'язковий, і саме він лікував «фокус зник після Back».
    //
    // При першому відкритті розділу дані приходять пізніше за композицію, тож на момент
    // запиту кнопка героя вже розмічена. А при поверненні зі сторінки тайтла `slide` уже
    // не null: ефект спрацьовував першим же кадром, вузла ще не було, і запит тихо гинув
    // у `runCatching` — розділ відкривався без фокуса взагалі. Другий Back потрапляв уже
    // у власний обробник сторінки, і саме він повертав підсвітку.
    // Куди стати після повернення зі сторінки тайтла: на ту саму картку того самого
    // ряду. `railItem` живе у ViewModel і перехід переживає, тож картку є за чим знайти;
    // без цього фокус ішов на кнопку превʼю, а на рядах його не було взагалі — і меню
    // перехоплювало ↓, через що каталог здавався замкненим.
    val restoreFocus = remember { FocusRequester() }
    LaunchedEffect(state.slide != null) {
        if (state.slide != null && !(state.inRows && viewModel.railItem != null)) {
            contentFocusRequester.requestAfterFrame()
        }
    }

    val inRails = state.inRows

    // BACK крокує назад по сторінці, як у ТВ Ефірі: перший тап згортає ряди й повертає
    // на початок, другий веде у верхнє меню, і лише третій (уже з меню) виходить із
    // розділу — його обробляє навігація застосунку.
    var contentHasFocus by remember { mutableStateOf(false) }
    // Закриваючи оверлей, фокус треба комусь віддати: сторінка позаду під час його
    // існування фокус не брала, тож після зникнення він лишався б у нікуди — і ↓ починало
    // гуляти рядами без видимої підсвітки.
    // Фокус на кнопку слайдера — ПІСЛЯ кадру, а не відразу за resetPreview: у тому ж кадрі
    // `contentFocusRequester` ще причеплений до картки «Продовжити» в рядах, запит ставав
    // на неї, а за мить вузол зникав — і Кінозал лишався зовсім без фокуса.
    var sliderFocusAsk by remember { mutableStateOf(0) }
    LaunchedEffect(sliderFocusAsk) { if (sliderFocusAsk > 0) contentFocusRequester.requestAfterFrame() }
    val backToPage = {
        viewModel.resetPreview()
        sliderFocusAsk += 1
    }
    SectionBackHandler(
        inRails = inRails,
        contentHasFocus = contentHasFocus,
        menuFocus = menuFocusRequester,
        onCollapseRails = backToPage,
        enabled = true,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // У рядах тло рівне; при переході Hero ↔ Ряди кадр плавно згасає за 320 мс.
        Box(modifier = Modifier.fillMaxSize().background(LikhtarColors.Background))
        AnimatedVisibility(
            visible = !inRails,
            enter = fadeIn(tween(320, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(280, easing = FastOutSlowInEasing)),
        ) {
            SlideBackdrop(state.slide, state.slideForward)
        }
        val inactiveHeroFocus = remember { FocusRequester() }
        // Поки відкрито оверлей, сторінка позаду фокус не бере: інакше ↓ із панелі
        // провалювався крізь неї й починав гуляти рядами під затемненням.
        CompositionLocalProvider(LocalFocusBlocked provides false) {
        LikhtarScreenScaffold(
            contentModifier = Modifier.onFocusChanged { contentHasFocus = it.hasFocus },
            section = LikhtarSection.CINEMA,
            onSelectSection = onSelectSection,
            menuFocusRequester = menuFocusRequester,
            contentFocusRequester = contentFocusRequester,
            loading = state.loading,
            showTopBar = !inRails,
        ) {
            // Плавне згортання/розгортання Hero при переході в ряди (як у tvOS / likhtar-cinema-modern.html).
            AnimatedVisibility(
                visible = !inRails,
                enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) + expandVertically(tween(320, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) + shrinkVertically(tween(320, easing = FastOutSlowInEasing)),
            ) {
                val heroFocus = if (!inRails) contentFocusRequester else inactiveHeroFocus
                Column(
                    modifier = Modifier.padding(
                        start = LikhtarDimens.ScreenPadding,
                        end = LikhtarDimens.ScreenPadding,
                        top = 6.dp,
                        // Відступ до горизонтальних рядів: без нього верх наїжджав на них.
                        bottom = 18.dp,
                    ),
                ) {
                    val heroItem = state.slide
                    val openFranchise = stringResource(R.string.cinema_open_franchise)
                    val watchText = stringResource(R.string.cinema_watch)
                    val trivia by viewModel.game.state.collectAsStateWithLifecycle()
                    val question = trivia.shown
                    if (heroItem != null && state.triviaOn && question != null) {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(HeroFraction)) {
                            TriviaPanel(
                                statement = question,
                                answered = trivia.answered,
                                kicker = stringResource(R.string.trivia_slide_kicker),
                                onAnswer = { viewModel.game.answer(it) },
                                nextLabel = stringResource(R.string.trivia_more),
                                onNext = { viewModel.game.next() },
                                onOpen = { onOpenTitle(question.item) },
                                firstFocus = heroFocus,
                                menuFocus = menuFocusRequester,
                                onEdgeLeft = { viewModel.prevSlide(); sliderFocusAsk += 1 },
                                onEdgeRight = { viewModel.nextSlide(); sliderFocusAsk += 1 },
                                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 32.dp),
                                width = 520.dp,
                            )
                        }
                    } else if (heroItem != null) CinemaHero(
                        item = heroItem,
                        pool = state.trendingPool,
                        uaAudio = heroItem.tmdbId in state.uaAudio,
                        watchFocus = heroFocus,
                        // У слайдері бувають і франшизи (сервер, heroDoors) — вони ведуть на свою
                        // сторінку, як широка секція в рядах.
                        onWatch = { item -> item.opensListing?.let { (kind, key) -> onOpenListing(kind, key) } ?: onOpenTitle(item) },
                        watchLabel = { item -> if (item.opensListing != null) openFranchise else watchText },
                        // На слайд питання кнопки інші — фокус треба поставити заново.
                        onNext = { viewModel.nextSlide(); sliderFocusAsk += 1 },
                        onPrev = { viewModel.prevSlide(); sliderFocusAsk += 1 },
                        menuFocus = menuFocusRequester,
                        moreHint = stringResource(R.string.cinema_more_tip),
                    ) else if (state.loading && state.rows.isEmpty()) {
                        com.likhtar.home.core.designsystem.CatalogSkeleton()
                    }
                }
            }

            // Ряди завжди починаються з першого: список зберігає прокрутку між заходами,
            // і заголовок «Новинки» лишався за верхньою межею вікна.
            // Сфокусований ряд стає першим у вікні: інакше попередній лишався висіти
            // половинками карток над ним.
            // rememberSaveable, а не remember: перехід на сторінку тайтла знищує
            // композицію Кінозалу, і зі звичайним remember ряд скидався на нульовий —
            // повернення викидало на початок каталогу.
            // Ряд, лічильник пришпилювання, плитка й прапорці повернення — див. [RailRestore].
            // Плитка — стрімінг чи жанр, з якого пішли на його сторінку: `railItem` її не
            // пам'ятає, і після Back фокус падав на картку за межами екрана.
            val studioState = rememberSaveable { mutableStateOf<String?>(null) }
            val restore = rememberRailRestore(studioState, restoreFocus) { tile ->
                state.inRows && (viewModel.railItem != null || tile != null)
            }
            var railIndex by restore::index
            var railPin by restore::pin
            val restorePending by restore::pending
            val localTitle = stringResource(R.string.cinema_local_row)
            val localOtherTitle = stringResource(R.string.cinema_local_other_row)
            RailsColumn(
                restore = restore,
                inRails = inRails,
                // Повернення зі сторінки тайтла; якір — картка «Продовжити», тож її ряд у вікно.
                ready = state.slide != null,
                onRestoreFallback = { rails ->
                    rails.scrollToItem(0)
                    contentFocusRequester.requestAfterFrame()
                },
                dataKey = Triple(state.rows, state.brands, state.rowOrder to state.hiddenRows),
            ) {
                var first = true
                var railCount = 0
                val scope = this
                // Ключ ряду — з назви, і два ряди сервера з однаковою (перекладеною) назвою клали
                // Кінозал «Key … was already used». Повтор дістає номер.
                val usedKeys = HashMap<String, Int>()
                val uniqueKey = { k: String -> (usedKeys.merge(k, 1, Int::plus) ?: 1).let { n -> if (n == 1) k else "$k#$n" } }

                // ↑ з першого ряду робить те саме, що BACK: згортає ряди й повертає нагору.
                val exitUpIfFirst: (Boolean) -> (() -> Unit)? = { isFirst ->
                    if (isFirst) {
                        { backToPage() }
                    } else {
                        null
                    }
                }

                val emitRow: (SectionRow) -> Unit = { row ->
                    if (row.items.isNotEmpty()) {
                        val visible = if (state.hideWatched) {
                            row.items.filterNot { it.id in state.watched }
                        } else {
                            row.items
                        }
                        val wideItem = visible.firstOrNull()?.takeIf { row.wide != null }
                        if (wideItem != null) {
                            val index = railCount
                            railCount += 1
                            first = false
                            scope.item(key = uniqueKey("wide-${row.title}"), contentType = "wide") {
                                // Широка секція: один тайтл або франшиза на всю ширину.
                                TitleSpotlight(
                                    item = wideItem,
                                    ua = wideItem.tmdbId in state.uaAudio,
                                    kicker = if (row.wide == "franchise") stringResource(R.string.cinema_tab_franchise) else null,
                                    buttonLabel = stringResource(if (row.wide == "franchise") R.string.cinema_open_franchise else R.string.cinema_open_play),
                                    onClick = {
                                        wideItem.opensListing?.let { (kind, key) -> onOpenListing(kind, key) }
                                            ?: onOpenTitle(wideItem)
                                    },
                                    onLongClick = viewModel.rowMarks::open.takeIf { wideItem.markable() }?.let { f -> { f(wideItem) } },
                                    buttonModifier = Modifier.onFocusChanged {
                                        if (it.isFocused) {
                                            railIndex = index
                                            railPin += 1
                                        }
                                    },
                                )
                            }
                        } else if (visible.isNotEmpty()) {
                            val isFirst = first
                            val index = railCount
                            railCount += 1
                            first = false
                            scope.item(key = uniqueKey("row-${row.title}"), contentType = "rail") {
                                CollectionRail(
                                    // Мікс форм, як в Apple TV: де прогрес чи підпис серії
                                    // («Нові серії», «Незабаром») і де немає постерів
                                    // («Суто українське» з порталами) — горизонтальні
                                    // картки з лого; решта — вертикальні постери.
                                    posters = row.notes.isEmpty() && !row.tail && row.openBrand != "ukrainian_feed",
                                    title = row.title,
                                    items = visible,
                                    watched = state.watched,
                                    uaConfirmed = state.uaAudio,
                                    // «S01E02 · ще 2» у «Нових серіях»: яка серія наступна.
                                    note = row.notes.takeIf { it.isNotEmpty() }?.let { n -> { item -> n[item.tmdbId] } },
                                    // Анонси — великими картками; на сторінці «Анонси» вже звичайні.
                                    tall = row.openBrand == "upcoming",
                                    onOpen = { item ->
                                        when {
                                            viewModel.openTrailer(item) -> Unit
                                            item.showId.isNotBlank() -> onOpenShow(item.showId)
                                            else -> onOpenTitle(item)
                                        }
                                    },
                                    onLongOpen = viewModel.rowMarks::open,
                                    restoreId = viewModel.railItem?.id,
                                    restore = restore,
                                    rowIndex = index,
                                    onFocused = { focusedItem ->
                                        railIndex = index
                                        viewModel.onRailFocus(focusedItem)
                                    },
                                    onExitUp = exitUpIfFirst(isFirst),
                                    // Ряд стрімінга веде на його сторінку з усіма
                                    // підбірками — це ті самі двері, що й плитка внизу.
                                    // Ряд без сторінки відкривається звичною сіткою.
                                    // Своя медіатека — уся в ряду, сторінки для неї немає.
                                    onOpenAll = {
                                        val brand = row.openBrand
                                        if (brand != null) {
                                            onOpenListing(CatalogListingKind.STUDIO, brand)
                                        } else {
                                            onOpenListing(CatalogListingKind.COLLECTION, row.key)
                                        }
                                    }.takeIf { row.key != LOCAL_ROW },
                                    onAnyFocus = {
                                        railIndex = index
                                        railPin += 1
                                    },
                                )
                            }
                        }
                    }
                }

                // Сервер не віддав рядів — кажемо прямо, а не лишаємо порожнечу. Героя тоді
                // теж немає (його пул — із рядів), тож фокус з меню стає на цю кнопку.
                if (!state.loading && state.rows.isEmpty()) {
                    scope.item(key = "server-down") {
                        ServerDownPanel(
                            onRetry = viewModel::retry,
                            focus = contentFocusRequester.takeIf { state.slide == null && !inRails },
                        )
                    }
                }

                // Порядок рядів заданий, а не успадкований від порядку enum: спершу те, що
                // людина вже почала, далі те, що зʼявилось, потім її власні закладки — і аж
                // тоді решта каталогу.
                run {
                    val isFirst = first
                    val index = railCount
                    railCount += 1
                    first = false
                    scope.item(key = "resume") {
                        val ownResume = remember { FocusRequester() }
                        val firstResume = if (inRails) contentFocusRequester else ownResume
                        // Вхід на першу картку й відмотку ряду робить сам LikhtarRail.
                        Column {
                            SectionTitle(text = stringResource(R.string.cinema_continue_watching))
                            LikhtarRail {
                                // Радник стоїть тут завжди й першим — саме тому, що працює
                                // він тоді, коли продовжувати нема чого.
                                // Одна плитка «Ліхтарик» замість «Що подивитись» і «Закладок»:
                                // головна фішка, тож розміром як кадри поруч і з тим, що нового саме для мене.
                                item(key = "likhtarik") {
                                    LikhtarikTile(
                                        bookmarks = state.watchlist.size,
                                        focus = firstResume,
                                        onClick = { onSelectSection(LikhtarSection.SEARCH) },
                                        onFocused = {
                                            railIndex = index
                                            viewModel.onRowFocus()
                                        },
                                        onExitUp = exitUpIfFirst(isFirst),
                                    )
                                }
                                items(state.resume.take(RAIL_ITEMS), key = { it.id }) { card ->
                                    ResumeTile(
                                        title = card.title,
                                        subtitle = card.subtitle,
                                        imageUrl = card.imageUrl,
                                        logoUrl = card.logoUrl,
                                        progress = card.progress,
                                        onClick = {
                                            // Три дороги, і саме в цьому порядку: наша
                                            // позиція відкриває сторінку тайтла, позиція
                                            // чужого сервісу вмикається в його застосунку,
                                            // решта веде в розділ.
                                            //
                                            // Сторінка, а не одразу плеєр: запуск звідси
                                            // йшов без жодного індикатора, і поки джерело
                                            // готувалось, натискання виглядало так, ніби картка
                                            // не відкривається. На сторінці перша кнопка —
                                            // «Продовжити SxxEyy», тож це один OK зверху.
                                            card.open(
                                                onShow = { onSelectSection(LikhtarSection.REALITY) },
                                                onTitle = onOpenTitle,
                                                onLaunch = viewModel::openInApp,
                                                onSection = onSelectSection,
                                            )
                                        },
                                        onFocused = {
                                            railIndex = index
                                            viewModel.onPreviewApp(card)
                                        },
                                        modifier = Modifier
                                            .width(CatalogBannerWidth)
                                            .cardSlot(),
                                    )
                                }
                                // Решта історії живе за останньою карткою — як у будь-якій
                                // добірці Кінозалу.
                                if (state.resume.isNotEmpty()) {
                                    item(key = "resume-all") {
                                        OpenAllCard(
                                            label = stringResource(R.string.cinema_all_history),
                                            onClick = onOpenHistory,
                                            modifier = Modifier.onFocusChanged {
                                                if (it.isFocused) {
                                                    railIndex = index
                                                    viewModel.onRowFocus()
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Своя медіатека — одразу після «Продовжити»: це те, що вже лежить удома.
                if (state.local.isNotEmpty()) {
                    emitRow(SectionRow(localTitle, state.local, emptySet(), emptySet(), emptyMap(), emptyMap(), key = LOCAL_ROW))
                }
                if (state.localOther.isNotEmpty()) {
                    val index = railCount
                    railCount += 1
                    first = false
                    scope.item(key = "local-other") {
                        LocalFilesRail(
                            title = localOtherTitle,
                            files = state.localOther,
                            onPlay = viewModel::playLocalFile,
                            onFocused = {
                                railIndex = index
                                railPin += 1
                            },
                        )
                    }
                }

                // Ряди сервера в його ж порядку: новинки, українська стрічка, далі
                // стрімінги. «Закладки» — ярликом у «Продовжити перегляд».
                // Свій порядок людини — поверх серверного (налаштування з телефона).
                val shownRows = state.rows
                    .filterNot { it.tail || it.items.isEmpty() || (!state.showAnime && it.openBrand == "anime") }
                    .arranged(state.rowOrder, state.hiddenRows)
                // «Жанри» — де сказав сервер (після «Бо вам сподобалось»);
                // старий сервер мітки не шле — тоді після третього ряду, як було.
                val genresAt = shownRows.indexOfFirst { it.genresAfter }.takeIf { it >= 0 } ?: 2
                // «Топ-10» — після першої широкої секції за жанрами, як у прототипі.
                var topPlaced = false
                val emitTop = {
                    val top = state.top
                    if (!topPlaced && top != null) {
                        topPlaced = true
                        val index = railCount
                        railCount += 1
                        first = false
                        scope.item(key = "top10") {
                            TopTenRail(
                                title = top.title,
                                items = top.items,
                                uaConfirmed = state.uaAudio,
                                onOpen = onOpenTitle,
                                onFocused = { focusedItem ->
                                    railIndex = index
                                    viewModel.onRailFocus(focusedItem)
                                },
                                onLongOpen = viewModel.rowMarks::open,
                            )
                        }
                    }
                }
                shownRows.forEachIndexed { rowIndex, row ->
                    emitRow(row)
                    if (rowIndex > genresAt && row.wide != null) emitTop()
                    if (rowIndex == genresAt && state.genres.isNotEmpty()) {
                        val index = railCount
                        railCount += 1
                        first = false
                        scope.item(key = "genres") {
                            GenreRail(
                                genres = state.genres,
                                onOpen = { genre -> onOpenListing(CatalogListingKind.STUDIO, genre.key) },
                                onFocused = { viewModel.onRowFocus() },
                                restore = restore,
                                rowIndex = index,
                            )
                        }
                    }
                }

                emitTop()
                if (state.realityItems.isNotEmpty()) {
                    val isFirst = first
                    val index = railCount
                    railCount += 1
                    first = false
                    scope.item(key = "reality-row", contentType = "rail") {
                        CollectionRail(
                            posters = false,
                            title = stringResource(LikhtarSection.REALITY.titleRes),
                            items = state.realityItems,
                            watched = state.watched,
                            uaConfirmed = state.uaAudio,
                            onOpen = { item -> viewModel.openReality(item, onOpenShow, onOpenTitle) },
                            onLongOpen = viewModel.rowMarks::open,
                            restoreId = viewModel.railItem?.id,
                            restore = restore,
                            rowIndex = index,
                            onFocused = { focusedItem ->
                                railIndex = index
                                viewModel.onRailFocus(focusedItem)
                            },
                            onExitUp = exitUpIfFirst(isFirst),
                            onOpenAll = onOpenReality,
                            onAnyFocus = {
                                railIndex = index
                                railPin += 1
                            },
                        )
                    }
                }
                if (state.brands.isNotEmpty()) {
                    val isFirst = first
                    val index = railCount
                    railCount += 1
                    first = false
                    item(key = "studios") {
                        // Решта сервісів — останньою плиткою, сіткою на окремій сторінці:
                        // другим рядом вони не влазили (каналів більше, ніж стрімінгів).
                        TileRail(
                            title = stringResource(R.string.cinema_streamings_and_channels),
                            tiles = state.brands.map { BrandTile(it.key, it.title) } +
                                BrandTile(STREAMINGS_ALL, stringResource(R.string.cinema_all_streamings), subtitle = stringResource(R.string.cinema_and_tv_channels), brand = null),
                            restore = restore,
                            rowIndex = index,
                            onRowFocus = viewModel::onRowFocus,
                            onOpen = { tile ->
                                if (tile.key == STREAMINGS_ALL) onOpenStreamings()
                                else onOpenListing(CatalogListingKind.STUDIO, tile.key)
                            },
                        )
                    }
                }

                // Довідка про майбутнє — після плиток: ввімкнути з неї нічого.
                state.rows.filter { it.tail }.forEach(emitRow)

                state.message?.let { text ->
                    item(key = "message") {
                        Text(
                            text = text,
                            style = LikhtarType.MonoSmall,
                            color = LikhtarColors.TextMuted,
                        )
                    }
                }
            }
        }

        }


        // Поки добірки та локальні стрічки збираються, не показуємо напівготову геометрію
        // сторінки: на телевізорі її послідовне "доїжджання" виглядає як ривок. Оверлей
        // зникає одним кадром, коли є стабільний перший складений стан.
        // Тримаємо лоадер щонайменше дві секунди: сторінка встигає скластися повністю, і
        // перехід читається як перехід, а не як миготіння напівготової геометрії.
        if (rememberLoaderHold(state.loading)) {
            CinemaLoadingScreen()
        }

        // Довге OK на картці — та сама «Позначити», що на сторінці тайтла, без уточнень, яких
        // з ряду не видно (сезон, де зупинились, жанри): їх дає сторінка.
        RowMarkPanel(viewModel.rowMarks, onOpen = onOpenTitle)
        PreparingScrim(viewModel.preparing.collectAsStateWithLifecycle().value)
    }
}

@Composable
private fun CinemaLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LikhtarColors.BackgroundDeep),
        contentAlignment = Alignment.Center,
    ) {
        LanternLoader(title = stringResource(R.string.cinema_loader_title), subtitle = stringResource(R.string.cinema_loader_subtitle))
    }
}

/** Скільки заїжджає панель. */
private const val PanelSlideMs = 260

/** Ключ відновлення для плитки «Всі стрімінги». */
private const val STREAMINGS_ALL = "__all__"

/**
 * Радник у ряду «Продовжити» — картка, за якою не фільм, а питання.
 *
 * Постера в неї немає навмисно, як і в решти плиток-переходів: випадковий кадр обіцяв би
 * конкретний фільм, а тут його ще нема — його треба підібрати.
 */

/** Ключ ряду «З медіатеки»: без «Відкрити всі». */
private const val LOCAL_ROW = "local-media"
