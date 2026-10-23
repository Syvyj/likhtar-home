package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.likhtar.home.core.designsystem.PreparingScrim
import com.likhtar.home.core.catalog.open
import com.likhtar.home.core.designsystem.NoBringIntoView
import com.likhtar.home.core.designsystem.cardSlot
import com.likhtar.home.core.designsystem.centerItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.CompositionLocalProvider
import com.likhtar.home.core.designsystem.LikhtarColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarRail
import com.likhtar.home.core.designsystem.LikhtarScreenScaffold
import com.likhtar.home.core.designsystem.ResumeTile
import com.likhtar.home.core.designsystem.SectionTitle
import com.likhtar.home.core.designsystem.requestAfterFrame
import com.likhtar.home.core.model.LikhtarSection

/**
 * «Реаліті» — Кінозал з іншими даними.
 *
 * Власного вигляду розділ не має й не проєктувався: слайдер — той самий `CinemaHero`, тло
 * рядів — той самий `RailBackdrop`, шапка — той самий `RailHeroBlock`, ряди — той самий
 * `CollectionRail` із тими самими картками й тим самим «Відкрити всі». Тому екран і живе в
 * модулі Кінозалу: спільні компоненти тут внутрішні, і копіювати їх у сусідній модуль
 * означало б завести другу копію того, що вже є.
 *
 * Різниця з Кінозалом рівно одна — звідки беруться позиції. Порадника й настроїв тут немає:
 * вони про вибір фільму на вечір, а не про шоу.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RealityScreen(
    onSelectSection: (LikhtarSection) -> Unit,
    /** Наша позиція: id, а не картка — сторінці шоу потрібні канал і слаг, яких у картці немає. */
    onOpenShow: (String) -> Unit,
    /** Позиція з TMDB: у неї є справжній id, тож відкривається сторінкою матеріалу Кінозалу. */
    onOpenTitle: (CatalogItem) -> Unit,
    onOpenAll: (String) -> Unit,
    /** Продовжити: відкриваємо сторінку проєкту за його ext-ключем. */
    onResume: (String) -> Unit,
    viewModel: RealityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Повернення в розділ — привід зазирнути, чи не застоялось. Сам поріг тримає
    // ViewModel: захід і вихід мережі не коштують.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshIfStale()
        onPauseOrDispose {}
    }
    val menuFocusRequester = remember { FocusRequester() }
    // ДВА якорі, а не один спільний.
    //
    // Спільний requester на кнопку слайдера й на першу картку ряду давав таку поведінку: на вхід у розділ фокус осідав на картці, зсунутій за нижній край
    // екрана (заміряно дампом: `bounds=[64,1046][498,1080]`), і далі ↑/↓ ходили між двома
    // невидимими картками. Причина в тому, що вузлів два, а requester один: хто останній
    // причепився, той і забирає фокус.
    val heroFocusRequester = remember { FocusRequester() }
    val railsFocusRequester = remember { FocusRequester() }
    val inRails = state.inRows
    // Меню мусить віддавати фокус туди, де людина зараз: у слайдері це кнопка, у рядах —
    // перший ряд. Один requester на два стани, але щоразу причеплений рівно до одного вузла.
    val contentFocusRequester = if (inRails) railsFocusRequester else heroFocusRequester
    // Дві дороги з однієї картки: наші позиції відкриваються власною сторінкою шоу, TMDB —
    // сторінкою матеріалу Кінозалу з торентами й сервісами. Ряд при цьому спільний.
    // ТРИ дороги з однієї картки, і вибирає їх сервер, а не знак id: позиція порталу
    // відкривається власною сторінкою шоу, позиція TMDB — сторінкою матеріалу Кінозалу, а
    // ютуб-плейлист і позиція сервісу вмикаються там, де лежать: своєї сторінки для них
    // немає й не передбачено.
    val openShow: (CatalogItem) -> Unit = { viewModel.open(it, onOpenShow, onOpenTitle) }
    // Повернення з рядів у слайдер. Фокус поверне ефект нижче: він спрацьовує вже після
    // зміни [inRails], коли кнопка «Дивитись» знову існує в композиції.
    val backToSlider = viewModel::resetPreview

    // Фокус просимо після приходу даних: кнопки «Дивитись» до них просто немає, і запит
    // на неіснуючий вузол гине мовчки — розділ відкривався так, ніби він не активний.
    // Плитка, з якої пішли на сторінку сервісу чи студії. Плитка — не картка, `railItem` її
    // не пам'ятає, і після Back фокус падав на першу картку згори, поки список стояв унизу:
    // сторінка виглядала завислою (той самий випадок, що в Кінозалі).
    val tileState = rememberSaveable { mutableStateOf<String?>(null) }
    var tileRestore by tileState
    LaunchedEffect(state.slide != null, inRails) {
        if (state.slide == null) return@LaunchedEffect
        // Повернення на плитку веде ефект відновлення нижче — тут не заважаємо.
        if (inRails && tileRestore != null) return@LaunchedEffect
        // У рядах кнопки слайдера немає — там якір інший, і просити її означало б
        // відправити фокус у неіснуючий вузол.
        if (inRails) railsFocusRequester.requestAfterFrame() else heroFocusRequester.requestAfterFrame()
    }
    // Кадр іде тим самим ланцюжком, що й шапка нижче: картка ряду, інакше поточний слайд.
    // Якщо для цієї ж картки вже приїхала велика обкладинка проєкту — беремо її: шапка на
    // весь екран, і каталожний банер 1440×550 на ній помітно м'якший.
    // Логотип, добраний на фокусі, підставляємо й у шапку: він уже завантажений, і окремого
    // запиту це не коштує. Без цього в шапці стояв текстовий заголовок, хоч на картці того
    // самого шоу в ряду під нею логотип уже був — це впадало в очі.
    val withLogo: (CatalogItem) -> CatalogItem = { card ->
        state.logos[card.tmdbId]?.let { card.copy(logoPath = it) } ?: card
    }
    val head = state.slide?.let(withLogo)
    // Те саме правило, що в Кінозалі: немає кадру — беремо власний постер позиції.
    // Порожнє тло тут не «нейтральне», а просто порожнє: у розділі, де половина карток
    // портальні, це була більшість заходів.
    val railBackdrop = state.heroArt?.takeIf { it.first == head }?.second
        ?: head?.let { TmdbImages.backdrop(it.backdropPath) ?: TmdbImages.poster(it.posterPath) }

    var contentHasFocus by remember { mutableStateOf(false) }
    // BACK крокує назад по сторінці, як у Кінозалі: перший тап згортає ряди, другий веде у
    // верхнє меню, і лише третій виходить із розділу.
    SectionBackHandler(
        inRails = inRails,
        contentHasFocus = contentHasFocus,
        menuFocus = menuFocusRequester,
        onCollapseRails = backToSlider,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // У рядах тло рівне, як у Кінозалі (як для Кінозалу, так і
        // для Реаліті): кадр на весь екран, що мінявся з кожним рухом пульта, забирав
        // пів екрана під шапку й перемальовувався щокроку.
        Box(modifier = Modifier.fillMaxSize().background(LikhtarColors.Background))
        // Прототип: кадр шапки — лише ліворуч, праворуч графіт під «Нові випуски».
        if (!inRails) RealityFrame(railBackdrop)

        LikhtarScreenScaffold(
            contentModifier = Modifier.onFocusChanged { contentHasFocus = it.hasFocus },
            section = LikhtarSection.REALITY,
            onSelectSection = onSelectSection,
            menuFocusRequester = menuFocusRequester,
            contentFocusRequester = contentFocusRequester,
            loading = state.loading,
        ) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LanternLoader(title = stringResource(R.string.cinema_reality_loader_title), subtitle = stringResource(R.string.cinema_reality_loader_subtitle))
                }
                return@LikhtarScreenScaffold
            }

            val slide = state.slide?.let(withLogo)
            if (inRails) {
                // Шапки в рядах немає, як у Кінозалі: ряди починаються під меню.
            } else if (slide != null) {
                // Шапка — програма нових випусків (прототип): розміри відраховано від
                // верху екрана, тож шапка знає, де вона починається.
                val density = androidx.compose.ui.platform.LocalDensity.current
                var heroTop by remember { mutableStateOf(56.dp) }
                val confirmed = remember(state.rows) { state.rows.flatMap { it.confirmed }.toSet() }
                RealityGuideHero(
                    pool = state.pool.map(withLogo),
                    current = slide,
                    top = heroTop,
                    focus = heroFocusRequester,
                    menuFocus = menuFocusRequester,
                    onShow = viewModel::showSlide,
                    onOpen = openShow,
                    ua = { it.tmdbId in confirmed },
                    modifier = Modifier.onGloballyPositioned {
                        heroTop = with(density) { it.positionInRoot().y.toDp() }
                    },
                )
            }

            // Сфокусований ряд стає першим у вікні: інакше попередній лишався висіти
            // половинками карток над ним.
            //
            // rememberSaveable, а не remember: перехід на сторінку шоу знищує композицію
            // розділу, і зі звичайним remember ряд скидався на нульовий — повернення
            // викидало нагору, а не в той ряд, з якого пішли. Той самий урок, що в
            // Кінозалі, і лікується тим самим способом.
            // Ряд, лічильник пришпилювання й прапорці повернення — див. [RailRestore].
            val restore = rememberRailRestore(tileState) { tile -> inRails && (viewModel.railItem != null || tile != null) }
            var railIndex by restore::index
            var railPin by restore::pin
            var restoreLanded by restore::landed
            val restorePending by restore::pending
            val restoreFocus = restore.focus
            RailsColumn(
                restore = restore,
                inRails = inRails,
                ready = state.rows.isNotEmpty(),
                onRestoreFallback = { railsFocusRequester.requestAfterFrame() },
            ) {
                // «Продовжити перегляд» першим, як домовлено. Рядків тут стільки, скільки
                // проєктів, а не випусків: сервер тримає один на проєкт, і ряд лишається
                // «звідки продовжити», а не історією.
                // Те саме, що в Кінозалі: сервер не віддав рядів — кажемо прямо.
                if (!state.loading && state.rows.isEmpty()) {
                    item(key = "server-down") {
                        ServerDownPanel(
                            onRetry = viewModel::reload,
                            focus = heroFocusRequester.takeIf { state.slide == null && !inRails },
                        )
                    }
                }
                if (state.resume.isNotEmpty()) {
                    item(key = "reality-resume") {
                        Column {
                            SectionTitle(text = stringResource(R.string.cinema_continue_watching))
                            LikhtarRail {
                                itemsIndexed(state.resume, key = { _, card -> card.id }) { index, card ->
                                    ResumeTile(
                                        title = card.title,
                                        subtitle = card.subtitle,
                                        imageUrl = viewModel.artOf(card),
                                        progress = card.progress,
                                        logoUrl = card.logoUrl,
                                        onClick = {
                                            card.open(
                                                onShow = onResume,
                                                onTitle = onOpenTitle,
                                                onLaunch = viewModel::openInApp,
                                                onSection = onSelectSection,
                                            )
                                        },
                                        onFocused = {
                                            // Ряд «Продовжити» теж вводить у стан рядів:
                                            // інакше шапка лишалась слайдером, а фокус уже
                                            // був унизу — рівно та розсинхронізація, через
                                            // яку навігація й читалась як зламана.
                                            railIndex = 0
                                            viewModel.onRowFocus()
                                        },
                                        modifier = Modifier
                                            .width(CatalogBannerWidth)
                                            .cardSlot()
                                            // Якір ↓ з меню, поки «Продовжити» — перший ряд.
                                            .then(
                                                if (index == 0 && inRails) {
                                                    Modifier.focusRequester(railsFocusRequester)
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .onKeyEvent { event ->
                                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                                // ↑ з першого ряду — назад у слайдер, як і
                                                // з будь-якого першого ряду розділу.
                                                if (event.key == Key.DirectionUp) {
                                                    backToSlider()
                                                    true
                                                } else {
                                                    false
                                                }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }

                // Зсув на ряд «Продовжити»: він стоїть першим, тож нульовим рядом для
                // якоря й для «↑ виходить у слайдер» є вже він, а не перша добірка.
                val rowShift = if (state.resume.isEmpty()) 0 else 1
                // Ряд Реаліті: широка секція або рейка. Окремою лямбдою, бо рядів дві пачки —
                // до «Жанрів» і після, а вигляд і повернення фокуса в них однакові.
                val rowContent: @Composable (Int, RealitySection) -> Unit = { index, row ->
                    val wideItem = row.items.firstOrNull()?.takeIf { row.wide }
                    if (wideItem != null) {
                        // Широка секція — як у Кінозалі: одна позиція на всю ширину.
                        TitleSpotlight(
                            item = withLogo(wideItem),
                            ua = wideItem.tmdbId in row.confirmed,
                            // «Шоу», як у банері вгорі: ток-шоу «Зважся!» підписувалось «Серіал».
                            kindLabel = stringResource(R.string.cinema_kind_show).takeIf { wideItem.kind == TitleKind.TV },
                            reality = true,
                            onClick = { openShow(wideItem) },
                            onLongClick = viewModel.rowMarks::open.takeIf { wideItem.markable() }?.let { f -> { f(wideItem) } },
                            // Повернення зі сторінки шоу — на ту саму кнопку, як і в ряди.
                            buttonModifier = Modifier
                                .then(if (index == railIndex) Modifier.focusRequester(restoreFocus) else Modifier)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        if (restorePending) restoreLanded = true
                                        railIndex = index
                                        railPin += 1
                                        viewModel.onRailFocus(wideItem)
                                    }
                                },
                        )
                    } else CollectionRail(
                        // Мікс форм, як у Кінозалі: ряд сервісів (MEGOGO, Sweet.TV) — постери,
                        // ряд порталів і ютуба — горизонтальні картки з назвою: у порталів
                        // постерів немає, лише широкі кадри.
                        // Постерами — лише коли справжніх постерів більшість. Кадри 16:9 у
                        // вертикальній картці обрізали написи («ВА С / НЕЦЬКА ГРО») і стояли
                        // без назви: такий ряд — широкими картками з назвою.
                        // І через ряд, як у Кінозалі: відколи ряди з TMDB, постери
                        // були в усіх, і розділ став однією вертикальною стіною. Банери між
                        // рядами в лічбі не беруть участі.
                        posters = row.items.count(::hasRealPoster) * 2 >= row.items.size &&
                            state.rows.take(state.rows.indexOf(row)).count { !it.wide } % 2 == 1,
                        title = row.title,
                        // Логотип, добраний на фокусі, підставляємо в ту саму картку —
                        // інакше він приїхав би нікуди.
                        items = row.items.map(withLogo),
                        uaConfirmed = row.confirmed,
                        russianAudio = row.russian,
                        onOpen = openShow,
                        onLongOpen = viewModel.rowMarks::open,
                        // Якір ↓ з меню, поки ми в рядах: кнопки «Дивитись» у цьому стані
                        // немає, і без якоря меню просто не мало куди віддати фокус —
                        // розділ здавався замкненим. Ставимо його на першу картку першого
                        // ряду, тобто рівно туди, звідки в ряди й заходять.
                        // Ряд, з якого пішли на сторінку: саме в нього повертається фокус.
                        restoreId = viewModel.railItem?.id,
                        restore = restore,
                        rowIndex = index,
                        onFocused = { card ->
                            railIndex = index
                            viewModel.onRailFocus(card)
                        },
                        // ↑ з першого ряду робить те саме, що BACK: повертає у слайдер.
                        onExitUp = if (index == 0) backToSlider else null,
                        // Ряд зі своєю сторінкою («Для вас», теми) веде на неї, як у Кінозалі;
                        // решта, довша за десять карток, — у звичну сітку.
                        onOpenAll = row.openBrand?.let { brand -> { onOpenAll(RealityListViewModel.brandTitle(brand)) } }
                            ?: if (row.items.size > RAIL_ITEMS) ({ onOpenAll(row.title) }) else null,
                        onAnyFocus = {
                            railIndex = index
                            railPin += 1
                        },
                    )
                }
                // «Жанри», як в Apple TV, — після четвертого ряду.
                val cut = if (state.genres.isEmpty()) state.rows.size else minOf(4, state.rows.size)
                itemsIndexed(state.rows.take(cut), key = { _, row -> (if (row.wide) "wide-" else "row-") + row.title }, contentType = { _, row -> if (row.wide) "wide" else "rail" }) { position, row ->
                    rowContent(position + rowShift, row)
                }
                if (state.genres.isNotEmpty()) {
                    val genreIndex = cut + rowShift
                    item(key = "reality-genres") {
                        GenreRail(
                            genres = state.genres,
                            wide = true,
                            onOpen = { genre -> onOpenAll(RealityListViewModel.brandTitle(genre.key)) },
                            onFocused = { viewModel.onRowFocus() },
                            restore = restore,
                            rowIndex = genreIndex,
                        )
                    }
                }
                val afterGenres = if (state.genres.isEmpty()) 0 else 1
                itemsIndexed(state.rows.drop(cut), key = { _, row -> (if (row.wide) "wide-" else "row-") + row.title }, contentType = { _, row -> if (row.wide) "wide" else "rail" }) { position, row ->
                    rowContent(cut + afterGenres + position + rowShift, row)
                }

                // Плитки внизу — те саме, що «Стрімінги та канали» в Кінозалі, лише
                // вміст інший: чиє це неігрове насправді.
                //
                // Плиток «Discovery» чи «National Geographic» тут немає, і це замір, а не
                // пропуск: у придатному неігровому 969 позицій, мережа TMDB відома у 83, а
                // позицій MEGOGO й порталів TMDB не знає взагалі (пошук по 15 назвах дав
                // два збіги, один із них хибний). Плитка каже правду про джерело, а не
                // вдає знання про студію.
                if (state.brands.isNotEmpty()) {
                    val index = state.rows.size + rowShift + (if (state.genres.isEmpty()) 0 else 1)
                    item(key = "reality-brands") {
                        TileRail(
                            title = stringResource(R.string.cinema_reality_brands),
                            // Той самий вигляд, що «Стрімінги та канали» Кінозалу.
                            tiles = state.brands.map { BrandTile(it.key, it.title) },
                            restore = restore,
                            rowIndex = index,
                            onRowFocus = viewModel::onRowFocus,
                            onOpen = { tile -> onOpenAll(RealityListViewModel.brandTitle(tile.key)) },
                        )
                    }
                }
            }
        }
        RowMarkPanel(viewModel.rowMarks)
        PreparingScrim(viewModel.preparing.collectAsStateWithLifecycle().value)
    }
}
