package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import com.likhtar.home.core.designsystem.PreparingScrim
import com.likhtar.home.core.model.SearchLaunch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.CompositionLocalProvider
import com.likhtar.home.core.designsystem.LocalFocusBlocked
import com.likhtar.home.core.designsystem.cardSlot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.animation.AnimatedVisibility
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
    // Перехід превʼю ↔ колонки робимо руками: пошук фокуса між ними то тікав у меню,
    // то не бачив колонок за межами вікна.
    val columnsEntry = remember { FocusRequester() }

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
    var advisorOpen by remember { mutableStateOf(false) }
    var candidatesOpen by remember { mutableStateOf(false) }
    var candidateIndex by remember { mutableStateOf(0) }
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
    BackHandler(enabled = candidatesOpen) {
        candidatesOpen = false
        backToPage()
    }
    BackHandler(enabled = !candidatesOpen && advisorOpen) {
        advisorOpen = false
        backToPage()
    }
    SectionBackHandler(
        inRails = inRails,
        contentHasFocus = contentHasFocus,
        menuFocus = menuFocusRequester,
        onCollapseRails = backToPage,
        enabled = !candidatesOpen && !advisorOpen,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // У рядах тло рівне, як на сторінках всесвітів (подача Apple TV): кадр на весь екран, що мінявся на кожен рух пульта, забирав половину
        // екрана під шапку й перемальовувався щокроку — на приставці це найдорожче.
        if (inRails) {
            Box(modifier = Modifier.fillMaxSize().background(LikhtarColors.Background))
        } else {
            SlideBackdrop(state.advisorPick?.item ?: state.slide, state.slideForward)
        }
        // Поки відкрито оверлей, сторінка позаду фокус не бере: інакше ↓ із панелі
        // провалювався крізь неї й починав гуляти рядами під затемненням.
        CompositionLocalProvider(LocalFocusBlocked provides (advisorOpen || candidatesOpen)) {
        LikhtarScreenScaffold(
            contentModifier = Modifier.onFocusChanged { contentHasFocus = it.hasFocus },
            section = LikhtarSection.CINEMA,
            onSelectSection = onSelectSection,
            menuFocusRequester = menuFocusRequester,
            contentFocusRequester = contentFocusRequester,
            loading = state.loading,
        ) {
            // Якір ↓ з меню — кнопка «Дивитись» у превʼю: перше, що бачить око, і перше,
            // куди має стати фокус. На контейнері зі focusGroup якір не працює — фокус
            // просто лишався в меню, і наступне → перемикало розділи.
            //
            // Якщо цей елемент не скомпонований (список повернувся прокрученим), запит
            // фокуса впаде — і його спіймає focusDown() у меню. Раніше на цьому місці
            // застосунок падав із FocusRequester is not initialized.
            // Верхній блок не прокручується: він і є сторінкою, а рядки під ним — додаток
            // до неї. Заголовка сторінки немає навмисно: назва розділу вже стоїть у меню.
            if (!inRails) {
                Column(
                    modifier = Modifier.padding(
                        start = LikhtarDimens.ScreenPadding,
                        end = LikhtarDimens.ScreenPadding,
                        top = 6.dp,
                        // Відступ до горизонтальних рядів: без нього верх наїжджав на них.
                        bottom = 18.dp,
                    ),
                ) {
                    // Висота блока = висота колонки: превʼю тягнеться рівно по низ кнопки
                    // «більше», а не по вигаданій константі.
                    // Саме слайд або підбір радника — і нічого більше. `state.hero` сюди не
                    // годиться: воно йде ще й за сфокусованою карткою ряду, тому нагорі лишався
                    // матеріал, якого в пулі немає, а разом із ним зникали крапки.
                    val heroItem = state.advisorPick?.item ?: state.slide
                    val openFranchise = stringResource(R.string.cinema_open_franchise)
                    val watchText = stringResource(R.string.cinema_watch)
                    if (heroItem != null) CinemaHero(
                        item = heroItem,
                        pool = state.trendingPool,
                        uaAudio = heroItem.tmdbId in state.uaAudio,
                        watchFocus = contentFocusRequester,
                        // У слайдері бувають і франшизи (сервер, heroDoors) — вони ведуть на свою
                        // сторінку, як широка секція в рядах.
                        onWatch = { item -> item.opensListing?.let { (kind, key) -> onOpenListing(kind, key) } ?: onOpenTitle(item) },
                        watchLabel = { item -> if (item.opensListing != null) openFranchise else watchText },
                        onNext = viewModel::nextSlide,
                        onPrev = viewModel::prevSlide,
                        menuFocus = menuFocusRequester,
                    )
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
                            scope.item(key = "wide-${row.title}", contentType = "wide") {
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
                            scope.item(key = "row-${row.title}", contentType = "rail") {
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
                        // Якір ↓ з меню в режимі рядів — саме ця картка.
                        //
                        // Навіщо: `contentFocusRequester` причеплений до кнопки «Дивитись»
                        // у превʼю, а в режимі рядів превʼю не малюється взагалі — у шапці
                        // стоїть `RailHeroBlock` без кнопок. Тобто якоря в цьому стані не
                        // існувало, запит фокуса гинув, далі працював геометричний пошук —
                        // і ПЕРШЕ ↓ з меню витрачалось даремно. На екрані це виглядало як
                        // «вниз не працює»: список зʼїжджав на початок, підсвітки не було,
                        // і лише друге ↓ ставало на картку.
                        //
                        // Саме радник, а не перша картка першого ряду: там уже живе
                        // `restoreFocus`, і два requester'и на одному вузлі — це дефект,
                        // закритий у `9ed1490`. Радник у цьому режимі перший і є завжди.
                        //
                        // Один requester на вузол, а не два: гілки `if (inRails)` взаємно
                        // виключні, тож `contentFocusRequester` завжди причеплений рівно
                        // до одного вузла — або до кнопки превʼю, або до цієї картки.
                        val firstResume = if (inRails) contentFocusRequester else ownResume
                        // Вхід на першу картку й відмотку ряду робить сам LikhtarRail.
                        Column {
                            SectionTitle(text = stringResource(R.string.cinema_continue_watching))
                            LikhtarRail {
                                // Радник стоїть тут завжди й першим — саме тому, що працює
                                // він тоді, коли продовжувати нема чого.
                                item(key = "advisor") {
                                    ShortcutCard(
                                        title = stringResource(R.string.cinema_what_to_watch),
                                        subtitle = stringResource(R.string.cinema_advisor_subtitle),
                                        icon = { LikhtarIcons.Lantern(size = 23.dp) },
                                        focus = firstResume,
                                        onClick = { advisorOpen = true },
                                        onFocused = {
                                            railIndex = index
                                            viewModel.onRowFocus()
                                        },
                                        onExitUp = exitUpIfFirst(isFirst),
                                    )
                                }
                                // «Закладки» — ярлик на ряд закладок Ліхтарика: окремого ряду в
                                // Кінозалі більше немає.
                                item(key = "bookmarks") {
                                    ShortcutCard(
                                        title = stringResource(R.string.cinema_bookmarks),
                                        subtitle = state.watchlist.size.takeIf { it > 0 }?.let { stringResource(R.string.cinema_in_list_count, it) } ?: stringResource(R.string.cinema_my_list),
                                        icon = { focused ->
                                            androidx.tv.material3.Icon(
                                                androidx.compose.ui.res.painterResource(com.likhtar.home.core.designsystem.R.drawable.ic_solar_bookmark),
                                                contentDescription = null,
                                                tint = if (focused) LikhtarColors.AmberOnPaper else LikhtarColors.Glow,
                                                modifier = Modifier.size(21.dp),
                                            )
                                        },
                                        onClick = {
                                            SearchLaunch.focusBookmarks = true
                                            onSelectSection(LikhtarSection.SEARCH)
                                        },
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
                                            // йшов без жодного індикатора, і поки торент
                                            // думав, натискання виглядало так, ніби картка
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

        // Панель радника — поверх сторінки, а не замість неї: за нею видно Кінозал, тому
        // зрозуміло, куди повертає BACK.
        AnimatedVisibility(
            visible = advisorOpen,
            enter = slideInHorizontally(tween(PanelSlideMs, easing = FastOutSlowInEasing)) { it },
            exit = slideOutHorizontally(tween(PanelSlideMs, easing = FastOutSlowInEasing)) { it },
        ) {
            AdvisorPanel(
                state = state,
                onTime = viewModel::selectTime,
                onMood = viewModel::selectMood,
                onShow = {
                    viewModel.showAdvisorPick()
                    candidateIndex = 0
                    candidatesOpen = true
                },
                onClose = { advisorOpen = false },
            )
        }

        if (candidatesOpen) {
            CandidatesOverlay(
                candidates = state.candidates,
                index = candidateIndex,
                onIndex = { candidateIndex = it },
                onOpen = { item ->
                    candidatesOpen = false
                    onOpenTitle(item)
                },
                onEmptyBack = {
                    candidatesOpen = false
                    backToPage()
                },
            )
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
        RowMarkPanel(viewModel.rowMarks)
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
@Composable
private fun ShortcutCard(
    title: String,
    subtitle: String,
    icon: @Composable (focused: Boolean) -> Unit,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    onExitUp: (() -> Unit)?,
    focus: FocusRequester? = null,
) {
    val exitUp = if (onExitUp == null) {
        Modifier
    } else {
        Modifier.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                onExitUp()
                true
            } else {
                false
            }
        }
    }
    // .short прототипу: 272×225 px, поле 22 px, тло --deep з тонкою рамкою; знак угорі, назва
    // 28 px і підпис моно 18 px унизу. Фокус — тепла біла заливка, 1.07 і тепла тінь.
    Column(modifier = Modifier.width(ShortcutWidth).cardSlot()) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(CatalogBannerWidth * 9f / 16f)
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .then(exitUp)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .focusGlow(com.likhtar.home.core.designsystem.ProtoCardCorner),
            shape = RoundedCornerShape(com.likhtar.home.core.designsystem.ProtoCardCorner),
            background = LikhtarColors.BackgroundDeep,
            focusedBackground = LikhtarColors.Paper,
            borderColor = LikhtarColors.Faint,
            focusedScale = LikhtarDimens.CardFocusScale,
            instantFill = true,
        ) { focused ->
            Column(modifier = Modifier.fillMaxSize().padding(11.dp)) {
                icon(focused)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = title,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp),
                    color = if (focused) ShortcutInk else androidx.compose.ui.graphics.Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Mono, fontSize = 9.sp),
                    color = if (focused) ShortcutInk else LikhtarColors.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

private val ShortcutInk = com.likhtar.home.core.designsystem.LikhtarColors.OnGlassActive

/** Ширина картки-ярлика в «Продовжити перегляд»: радник і закладки. */
private val ShortcutWidth = 136.dp

/** Ключ ряду «З медіатеки»: без «Відкрити всі». */
private const val LOCAL_ROW = "local-media"
