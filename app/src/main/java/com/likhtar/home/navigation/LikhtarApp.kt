package com.likhtar.home.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import com.likhtar.home.feature.cinema.CatalogListRoute
import com.likhtar.home.feature.cinema.RealityListRoute
import com.likhtar.home.feature.cinema.RealityShowRoute
import com.likhtar.home.feature.cinema.StreamingsRoute
import com.likhtar.home.feature.cinema.TitleRoute
import com.likhtar.home.core.catalog.playerPlan
import com.likhtar.home.core.catalog.TitleKind
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.likhtar.home.core.catalog.cleanStreamLabel
import com.likhtar.home.core.catalog.playbackResult
import com.likhtar.home.core.catalog.putResumePosition
import com.likhtar.home.core.catalog.TmdbImages
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.feature.settings.HomeLauncherOnboarding
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.CompositionLocalProvider
import com.likhtar.home.core.designsystem.LocalAmbientIdle
import com.likhtar.home.core.designsystem.LocalVisibleSections
import com.likhtar.home.core.designsystem.WallpaperBackground
import com.likhtar.home.core.designsystem.WallpaperScrim
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.feature.smarthome.SmartHomeScreen
import com.likhtar.home.feature.cinema.RealityListScreen
import com.likhtar.home.feature.cinema.RealityListViewModel
import com.likhtar.home.feature.cinema.RealityScreen
import com.likhtar.home.feature.cinema.LiveNowScreen
import com.likhtar.home.feature.cinema.RealityTitleScreen
import com.likhtar.home.feature.cinema.RealityTitleViewModel
import com.likhtar.home.feature.apps.AppsScreen
import com.likhtar.home.core.catalog.CatalogListingKind
import com.likhtar.home.feature.cinema.CatalogListScreen
import com.likhtar.home.feature.cinema.StreamingsScreen
import com.likhtar.home.feature.cinema.CatalogListViewModel
import com.likhtar.home.feature.cinema.CinemaScreen
import com.likhtar.home.feature.cinema.CinemaViewModel
import com.likhtar.home.feature.cinema.TitleScreen
import com.likhtar.home.feature.home.HomeScreen
import com.likhtar.home.feature.home.WeatherScreen
import com.likhtar.home.feature.search.SearchScreen
import com.likhtar.home.feature.settings.SettingsScreen

/** Підсторінки Кінозалу — єдині маршрути поза плоским списком розділів. */
/** Сторінка погоди: без аргументів, але типобезпечна, як і решта підсторінок. */
@kotlinx.serialization.Serializable
private data object WeatherRoute

/**
 * Навігація лаунчера.
 *
 * Стек навмисно плаский: будь-який перехід між розділами згортається до Головної, тому
 * Back завжди веде на Головну за один крок, а на самій Головній не робить нічого —
 * домашній екран не має куди «виходити».
 */
@Composable
fun LikhtarApp(
    homeRequests: Flow<Unit> = emptyFlow(),
    openRequests: Flow<OpenRequest> = emptyFlow(),
    interactions: Flow<Long> = emptyFlow(),
    viewModel: LikhtarAppViewModel = hiltViewModel(),
) {
    val startSection by viewModel.startSection.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    // Усе, що не розділ, — підсторінка Кінозалу: там Back повертає в каталог, а не на Головну.
    val onDetailRoute = route != null && LikhtarSection.fromRoute(route) == null
    val currentSection = LikhtarSection.fromRoute(route) ?: LikhtarSection.HOME

    val visibleSections by viewModel.visibleSections.collectAsStateWithLifecycle()
    val showHomeOnboarding by viewModel.homeOnboarding.collectAsStateWithLifecycle()
    val showFirstRun by viewModel.firstRun.collectAsStateWithLifecycle()

    val navigateTo: (LikhtarSection) -> Unit = { section ->
        // `|| onDetailRoute` обов'язкове: для підсторінок (фільм, каталог) fromRoute віддає
        // null, і currentSection «схлопується» в HOME. Без цієї умови перехід на Головну
        // зі сторінки фільму вважався б переходом у самого себе й не робив нічого.
        if (section != currentSection || onDetailRoute) {
            navController.navigate(section.route) {
                // Стек тримаємо рівно з одного запису: попереднього розділу не існує, тому
                // Back завжди веде на Головну, а не назад по історії переходів.
                //
                // popUpTo саме на граф. На startDestination це не працює: перший же перехід
                // виносить його зі стека (inclusive), далі popUpTo не знаходить ціль і
                // МОВЧКИ нічого не робить — розділи починають накопичуватись, Back водить
                // по всьому, що відкривали, а кожен незакритий екран тримає свої дані в
                // пам'яті. Саме через це лаунчер «важчав» від ходіння по розділах.
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Home при живому процесі має повертати лаунчер туди, звідки він стартує за налаштуванням:
    // без цього кнопка Home нічого не робить, якщо LIKHTAR уже на екрані.
    val navigate by rememberUpdatedState(navigateTo)
    LaunchedEffect(homeRequests, startSection) {
        val start = startSection ?: return@LaunchedEffect
        homeRequests.collect { navigate(start) }
    }
    // Кнопка пульта з дією: розділ — як з меню; стрімінг — його сторінка поверх Кінозалу.
    var assistantOpen by remember { mutableStateOf(false) }
    var assistantQuestion by remember { mutableStateOf<String?>(null) }
    // Лише коли NavHost уже має граф (startSection прочитано): інакше холодний старт із кнопки
    // пульта падав на navigate «You must call setGraph()». Запити в Channel — дочекаються.
    LaunchedEffect(openRequests, startSection != null) {
        if (startSection == null) return@LaunchedEffect
        openRequests.collect { request ->
            when (request) {
                is OpenRequest.Assistant -> {
                    assistantQuestion = request.question
                    assistantOpen = true
                }
                is OpenRequest.Section -> navigate(request.section)
                is OpenRequest.Streaming -> {
                    navigate(LikhtarSection.CINEMA)
                    navController.navigate(CatalogListViewModel.route(CatalogListingKind.STUDIO, request.brand))
                }
            }
        }
    }
    if (assistantOpen) {
        val assistant: com.likhtar.home.assistant.AssistantViewModel = hiltViewModel()
        LaunchedEffect(assistant) {
            assistant.navigation.collect { nav ->
                assistantOpen = false
                when (nav) {
                    is com.likhtar.home.assistant.AssistantNav.Section -> navigate(nav.section)
                    is com.likhtar.home.assistant.AssistantNav.Streaming -> {
                        navigate(LikhtarSection.CINEMA)
                        navController.navigate(CatalogListViewModel.route(CatalogListingKind.STUDIO, nav.brand))
                    }
                    is com.likhtar.home.assistant.AssistantNav.Watch -> {
                        com.likhtar.home.core.model.SearchLaunch.query = nav.query
                        navigate(LikhtarSection.SEARCH)
                    }
                }
            }
        }
        com.likhtar.home.assistant.AssistantPanel(assistant, onClose = { assistantOpen = false }, question = assistantQuestion)
    }

    // Back перехоплюємо завжди: на Головній він має нічого не робити, інакше система
    // виводить із лаунчера в попередній застосунок — для домашнього екрана це неприпустимо.
    BackHandler(enabled = true) {
        when {
            onDetailRoute -> navController.popBackStack()
            currentSection != LikhtarSection.HOME -> navigateTo(LikhtarSection.HOME)
        }
    }

    // ── Запуск плеєра «на результат» ────────────────────────────────────────────
    // Позицію відтворення може віддати лише Activity, і саме тому запуск живе тут, а не
    // в репозиторії застосунків: той тримає ApplicationContext і результату не дочекається.
    val player = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val (position, duration) = result.data.playbackResult(result.resultCode)
        // Логуємо сирий результат, бо мовчазний плеєр і плеєр, який повернув нулі, — це
        // дві різні поломки: перша лікується задачею/лаунчмодом, друга — ключами extras.
        Log.i(
            "LikhtarPlayback",
            "плеєр закрився: code=${result.resultCode} " +
                "extras=${result.data?.extras?.keySet()?.joinToString()} pos=$position dur=$duration " +
                "data=${result.data?.dataString?.take(120)}",
        )
        // `data` результату — серія, на якій плеєр вийшов (Just+ кладе її адресу).
        viewModel.onPlayerClosed(position, duration, result.data?.dataString)
    }
    val context = LocalContext.current
    val openWith = androidx.compose.ui.res.stringResource(com.likhtar.home.R.string.app_open_with)
    LaunchedEffect(viewModel) {
        viewModel.playback.requests.collect { request ->
            // Плейлист сезону (m3u) беруть не всі: Just+ і Just Player його не claim'ять.
            // Таким віддаємо саму серію й чергу `video_list` — серії він перемикає сам.
            val takesM3u = request.packageName.isBlank() || runCatching {
                context.packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(request.uri), request.mime)
                        .setPackage(request.packageName),
                    0,
                ).isNotEmpty()
            }.getOrDefault(true)
            val companion = runCatching { viewModel.playback.companionExtras() }.getOrDefault(emptyMap())
            // Що саме віддати плеєру — чиста функція під тестом (PlayerPlan.kt, core:catalog).
            val plan = request.playerPlan(takesM3u, companion)
            val intent = plan.toIntent()
            val asQueue = plan.asQueue
            Log.i(
                "LikhtarPlayback",
                "запуск ${request.packageName.ifBlank { "(системний вибір)" }} " +
                    "черга=${if (asQueue) request.queue.size else 0} варіантів=${request.variants.size} " +
                    "resume=${request.resumeMs} title=${request.title?.item?.title}",
            )
            // Без NEW_TASK: інакше плеєр іде окремим завданням і результату не повертає.
            runCatching { player.launch(intent) }.onFailure {
                // Пакет не знайшовся або не бере ACTION_VIEW — віддаємо вибір системі.
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_VIEW).setDataAndType(
                                Uri.parse(request.uri),
                                request.mime,
                            ),
                            openWith,
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }

    val wallpaper by viewModel.wallpaper.collectAsStateWithLifecycle()
    val ambientTimeout by viewModel.ambientTimeoutSeconds.collectAsStateWithLifecycle()

    // Ambient вмикається лише на Головній: у розділах користувач щось шукає, і ховати
    // від нього інтерфейс через хвилину роздумів — знущання.
    var idle by remember { mutableStateOf(false) }
    // Відлік прив'язаний до RESUMED, а не до композиції: приставка йде в очікування з
    // живим процесом, тому таймер догоряв уже за темного екрана і після ввімкнення
    // Головна відкривалась у ambient — ряди з'являлись лише після натискання пульта.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(ambientTimeout, currentSection, interactions, lifecycle) {
        idle = false
        if (ambientTimeout <= 0 || currentSection != LikhtarSection.HOME) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            idle = false
            interactions.collectLatest {
                idle = false
                delay(ambientTimeout * 1000L)
                idle = true
            }
        }
    }

    var dropOpen by remember { mutableStateOf(false) }
    if (dropOpen) com.likhtar.home.drop.PhoneDropDialog(onDismiss = { dropOpen = false })
    // Файл із телефона прийшов, коли вікно вже закрили (чи його збила заставка) — показуємо знову.
    val drop: com.likhtar.home.drop.PhoneDropViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        // Сервер — разом із лаунчером: закладка на телефоні працює й без QR, а файл, що прийшов,
        // відкриває вікно сам.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { drop.drop.start() }
        drop.drop.arrivals.collect { dropOpen = true }
    }

    CompositionLocalProvider(
        LocalVisibleSections provides visibleSections,
        LocalAmbientIdle provides idle,
        com.likhtar.home.core.designsystem.LocalPhoneDropAction provides { dropOpen = true },
    ) {
    // Фон спільний для всіх розділів, КРІМ каталогів: ТВ Ефір і «Наше» малюють свій
    // повноекранний кадр, і шпалера під ним — невидимий зайвий прохід GPU плюс чужа
    // атрибуція поверх каталогу.
    //
    // Сторінка фільму й список каталогу — такі самі каталожні екрани: обидва малюють
    // `HeroBackdrop` кадром із TMDB на весь екран. Але `fromRoute` для підсторінок віддає
    // null, `currentSection` схлопується в HOME, і умова нижче їх не ловила — під ними
    // й далі вантажилась і малювалась шпалера, а в куті стояв підпис wallhaven до фото,
    // якого на екрані немає. Саме це видно, коли з Кінозалу провалитись у фільм.
    // `destination.route` — це шаблон, а не заповнений шлях, тому звіряємось із самими
    // константами. Сторінка погоди теж тут: вона малює власний суцільний фон,
    // і шпалери під ними — невидимий прохід GPU плюс чужа атрибуція. `home/resume` не входить: це
    // підсторінка Головної, і шпалери під нею мають лишатись.
    val destination = backStackEntry?.destination
    val cinemaDetailRoute = destination != null && (
        destination.hasRoute<TitleRoute>() || destination.hasRoute<CatalogListRoute>() ||
            destination.hasRoute<StreamingsRoute>() ||
        // Сторінка шоу й сітка «Реаліті» — той самий випадок, що сторінка фільму: власний
        // непрозорий фон із кадру. Без них під сторінкою шоу й далі вантажилась шпалера, і
        // в кутку стояв підпис bing до фото, якого на екрані немає.
            destination.hasRoute<RealityShowRoute>() || destination.hasRoute<RealityListRoute>() ||
            destination.hasRoute<WeatherRoute>()
        )
    val catalogSection = cinemaDetailRoute || (
        !onDetailRoute && currentSection in setOf(
            LikhtarSection.CINEMA,
            LikhtarSection.LIVE_TV,
            LikhtarSection.REALITY,
            // Ліхтарик — постери й банер, як у Кінозалі: шпалера з підписом під ним — шум.
            LikhtarSection.SEARCH,
        )
        )
    WallpaperBackground(
        imageUri = wallpaper.wallpaper?.imageUri.takeIf { !catalogSection },
        backdrop = wallpaper.wallpaper?.backdrop.takeIf { !catalogSection },
        // «Затемнення» зі Налаштувань — ручка Головної й тільки її: решта розділів мають
        // власні scrim'и під свій макет, і глобальне затемнення поверх них лише гасило фото.
        dimPercent = if (!onDetailRoute && currentSection == LikhtarSection.HOME) wallpaper.dimPercent else 0,
        scrim = when {
            catalogSection -> null
            (if (onDetailRoute) LikhtarSection.CINEMA else currentSection) == LikhtarSection.HOME ->
                // Світлі тематичні шпалери потребують лише локальних зон під годинник,
                // погоду й док — не суцільного затемнення кадру.
                WallpaperScrim.HOME
            (if (onDetailRoute) LikhtarSection.CINEMA else currentSection) == LikhtarSection.CINEMA ->
                WallpaperScrim.HERO
            currentSection == LikhtarSection.APPS -> WallpaperScrim.APPS
            else -> WallpaperScrim.CONTENT
        },
        // Налаштування мають власний суцільний фон, тому шпалери під ними не видно —
        // а підпис до невидимого фото читається як сміття в кутку екрана.
        attribution = wallpaper.wallpaper?.attribution
            ?.takeIf {
                wallpaper.showAttribution &&
                    !catalogSection &&
                    currentSection != LikhtarSection.SETTINGS
            },
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Поки налаштування читаються, показуємо лише тло: один кадр темного екрана
        // краще за екран, який відкрився не там, де просив користувач.
        val start = startSection ?: return@Box

        NavHost(
            navController = navController,
            startDestination = start.route,
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) },
        ) {
            composable(LikhtarSection.HOME.route) {
                HomeScreen(
                    onSelectSection = navigateTo,
                    onOpenWeather = { navController.navigate(WeatherRoute) },
                    onOpenHistory = { navigateTo(LikhtarSection.SEARCH) },
                    onOpenTitle = { item -> navController.navigate(CinemaViewModel.titleRoute(item)) },
                    onOpenShow = { key ->
                        RealityTitleViewModel.idOfExtKey(key)?.let { navController.navigate(RealityTitleViewModel.route(it)) }
                            ?: navigateTo(LikhtarSection.REALITY)
                    },
                )
            }
            composable<WeatherRoute>(
                enterTransition = { slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(220)) },
            ) {
                WeatherScreen()
            }
            composable(LikhtarSection.CINEMA.route) {
                CinemaScreen(
                    onSelectSection = navigateTo,
                    onOpenTitle = { item ->
                        // Єдині маршрути, які лягають поверх стека, а не замінюють його:
                        // Back зі сторінки фільму має повертати в каталог, а не на Головну.
                        navController.navigate(CinemaViewModel.titleRoute(item))
                    },
                    onOpenListing = { kind, key ->
                        navController.navigate(CatalogListViewModel.route(kind, key))
                    },
                    onOpenHistory = { navigateTo(LikhtarSection.SEARCH) },
                    onOpenStreamings = { navController.navigate(StreamingsRoute) },
                    onOpenShow = { id -> navController.navigate(RealityTitleViewModel.route(id)) },
                )
            }
            composable<StreamingsRoute>(
                enterTransition = { slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(180)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(220)) },
            ) {
                StreamingsScreen(
                    onOpenListing = { kind, key ->
                        navController.navigate(CatalogListViewModel.route(kind, key))
                    },
                )
            }
            composable<TitleRoute>(
                enterTransition = { slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(180)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(220)) },
            ) {
                TitleScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTitle = { item -> navController.navigate(CinemaViewModel.titleRoute(item)) },
                    onOpenFranchise = { franchise ->
                        navController.navigate(
                            CatalogListViewModel.route(CatalogListingKind.FRANCHISE, franchise.id.toString()),
                        )
                    },
                )
            }
            composable<CatalogListRoute>(
                enterTransition = { slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(180)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(220)) },
            ) {
                CatalogListScreen(
                    onOpenTitle = { item -> navController.navigate(CinemaViewModel.titleRoute(item)) },
                    onOpenListing = { kind, key -> navController.navigate(CatalogListViewModel.route(kind, key)) },
                    onOpenShow = { id -> navController.navigate(RealityTitleViewModel.route(id)) },
                )
            }
            composable(LikhtarSection.LIVE_TV.route) {
                LiveNowScreen(onSelectSection = navigateTo)
            }
            composable(LikhtarSection.REALITY.route) {
                RealityScreen(
                    onSelectSection = navigateTo,
                    onOpenShow = { id -> navController.navigate(RealityTitleViewModel.route(id)) },
                    onOpenTitle = { item -> navController.navigate(CinemaViewModel.titleRoute(item)) },
                    onOpenAll = { title -> navController.navigate(RealityListViewModel.route(title)) },
                    onResume = { extKey ->
                        RealityTitleViewModel.idOfExtKey(extKey)?.let { id ->
                            navController.navigate(RealityTitleViewModel.route(id))
                        }
                    },
                )
            }
            composable<RealityShowRoute>(
                enterTransition = { slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(180)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(220)) },
            ) {
                RealityTitleScreen()
            }
            composable<RealityListRoute>(
                enterTransition = { slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(180)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(220)) },
            ) {
                RealityListScreen(
                    onOpenShow = { id -> navController.navigate(RealityTitleViewModel.route(id)) },
                    onOpenTitle = { item -> navController.navigate(CinemaViewModel.titleRoute(item)) },
                )
            }
            composable(LikhtarSection.SMART_HOME.route) {
                SmartHomeScreen(onSelectSection = navigateTo)
            }
            composable(LikhtarSection.SEARCH.route) {
                SearchScreen(
                    onSelectSection = navigateTo,
                    onOpenTitle = { item -> navController.navigate(CinemaViewModel.titleRoute(item)) },
                    onOpenShow = { id -> navController.navigate(RealityTitleViewModel.route(id)) },
                )
            }
            composable(LikhtarSection.APPS.route) {
                AppsScreen(onSelectSection = navigateTo)
            }
            composable(LikhtarSection.SETTINGS.route) {
                SettingsScreen(onSelectSection = navigateTo)
            }
        }

        // Пропозиція забрати кнопку HOME — поверх усього, один раз після встановлення.
        // Ставиться саме тут, а не в HomeScreen: стартовий розділ налаштовується, і на
        // «Кінозалі» діалог мусить з'явитися так само.
        if (showFirstRun) {
            OnboardingPanel(width = 640.dp) {
                com.likhtar.home.feature.settings.FirstRunWizard(
                    onFinish = viewModel::finishFirstRun,
                    onSkip = viewModel::skipFirstRun,
                )
            }
        } else if (showHomeOnboarding) {
            HomeLauncherOverlay(
                repository = viewModel.homeLauncherRepository,
                onDismiss = viewModel::dismissHomeOnboarding,
            )
        }
    }
    }
    }
}

/**
 * Затемнення на весь екран і панель онбордингу по центру.
 *
 * Back закриває так само, як «Пізніше»: діалог, який не можна прибрати кнопкою назад, на
 * пульті читається як зависання.
 */
@Composable
private fun HomeLauncherOverlay(
    repository: com.likhtar.home.core.data.HomeLauncherRepository,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true, onBack = onDismiss)
    OnboardingPanel(width = 560.dp) {
        HomeLauncherOnboarding(repository = repository, onDismiss = onDismiss)
    }
}

/** Затемнення на весь екран і панель по центру — для майстра й для пропозиції HOME. */
@Composable
private fun OnboardingPanel(width: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingScrim),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .clip(RoundedCornerShape(LikhtarDimens.CornerLarge))
                .background(LikhtarColors.Surface)
                .border(
                    1.dp,
                    LikhtarColors.GlassBorder,
                    RoundedCornerShape(LikhtarDimens.CornerLarge),
                )
                .padding(24.dp),
        ) {
            content()
        }
    }
}

private val OnboardingScrim = LikhtarColors.ShadeCold.copy(alpha = 0.86f)

/** Куди відкрити лаунчер ззовні — з кнопки пульта. */
sealed interface OpenRequest {
    /** [question] — запитання текстом; `null` — слухати голос. */
    data class Assistant(val question: String? = null) : OpenRequest
    data class Section(val section: LikhtarSection) : OpenRequest
    data class Streaming(val brand: String) : OpenRequest
}
