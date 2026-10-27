package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.FranchiseRef
import com.likhtar.home.core.catalog.TitleDetails
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.catalog.TitleKind
import androidx.compose.runtime.saveable.rememberSaveable
import com.likhtar.home.core.designsystem.Notice
import com.likhtar.home.core.designsystem.EmptyState
import com.likhtar.home.core.designsystem.HeroBackdrop
import com.likhtar.home.core.designsystem.HeroBackdropTone
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.LocalFocusBlocked
import com.likhtar.home.core.designsystem.LanternLoaderScreen
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarIcons
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import com.likhtar.home.core.designsystem.requestAfterFrame
import androidx.activity.compose.BackHandler
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Сторінка фільму: каталожні дані TMDB, панелі Ліхтаря і головне — куди піти дивитися.
 *
 * Порядок навмисний: спершу «Дивитися в…», бо саме по це сюди й заходять, і лише потім
 * усе інше. Панелі Ліхтаря показуються, тільки якщо для цього фільму картка є — база
 * наростає прогонами, і порожні заголовки виглядали б як поломка.
 */
@Composable
fun TitleScreen(
    onBack: () -> Unit,
    onOpenFranchise: (FranchiseRef) -> Unit,
    onOpenTitle: (CatalogItem) -> Unit,
    viewModel: TitleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val localFiles by viewModel.localFiles.collectAsStateWithLifecycle()
    var pickingLocal by remember { mutableStateOf(false) }
    if (pickingLocal && localFiles.isNotEmpty()) {
        // Фокус — на першій серії після останньої переглянутої за прогресом «Продовжити».
        val resume = state.resumeSeason to state.resumeEpisode
        val start = localFiles.indexOfFirst { it.season == resume.first && it.episode == resume.second }.coerceAtLeast(0)
        LocalFilesDialog(
            files = localFiles,
            start = start,
            onPick = { pickingLocal = false; viewModel.playLocal(it) },
            onDismiss = { pickingLocal = false },
        )
    }
    val details = state.details
    // Ключ — і сам тайтл, не лише кількість сервісів: у фільму без офіційних сервісів
    // («Дюна» з пошуку) кількість лишалась 0 до й після завантаження, ефект не
    // перезапускався, і сторінка стояла зовсім без фокуса.
    val firstActionFocus = rememberInitialFocusRequester(details?.item?.tmdbId to (details?.watchOptions?.size ?: 0))
    val seasonFocus = remember { FocusRequester() }
    val resumeEpisodeFocus = remember { FocusRequester() }
    val episodeRail = rememberLazyListState()
    var viewingEpisodes by remember { mutableStateOf(false) }
    // З якою вкладкою відкривати другу сцену: `null` — звичайний ↓, перша вкладка.
    var episodesTab by remember { mutableStateOf<TitleTab?>(null) }
    val trailer = state.trailer
    // Паралакс: фон ледь зсувається проти фокуса — кадр ніби лежить глибше за кнопки.
    var focusX by remember { mutableStateOf(0.5f) }
    val parallax by animateFloatAsState((0.5f - focusX) * 2f, tween(700), label = "parallax")
    val focusManager = LocalFocusManager.current
    val episodeAnchorPx = with(androidx.compose.ui.platform.LocalDensity.current) { 30.dp.roundToPx() }

    // Повернення з плеєра — єдиний момент, коли можна чесно закрити фільм: вихід із
    // чужого застосунку нам ніхто не повідомляє, тому ловимо власний ON_RESUME.
    // Фокус теж повертаємо на кнопку перегляду: після плеєра система ставила його на
    // перший-ліпший вузол — на «У список» (Just+).
    val resumeScope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        viewModel.onReturnedFromPlayer()
        if (!viewingEpisodes && state.picker == null) {
            resumeScope.launch { firstActionFocus.requestAfterFrame() }
        }
        onPauseOrDispose { }
    }
    // Дві окремі сцени, а не одна довга сторінка: так блок серій не ділить екран з hero.
    // BACK у серіях повертає на hero; наступний BACK уже обробить навігація застосунку.
    BackHandler(enabled = viewingEpisodes) { viewingEpisodes = false }
    // З «Продовжити» серіалу — одразу серії: щойно сторінка знає, що це серіал і на якій
    // серії продовжувати (фокус на ній ставить ефект нижче).
    var openedOnEpisodes by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(details?.item?.kind, state.resumeEpisode) {
        if (viewModel.openOnEpisodes && !openedOnEpisodes && details?.item?.kind == TitleKind.TV && state.resumeEpisode > 0) {
            openedOnEpisodes = true
            viewingEpisodes = true
        }
    }
    // Фокус ставимо після того, як серії приїхали: до цього картки, на яку треба стати,
    // просто не існує. Два requestFocus на одному вузлі вбивають перший мовчки, тому тут
    // рівно одна гілка — або серія, або пігулка сезону.
    val resumeIndex = if (state.season == state.resumeSeason && state.resumeEpisode > 0) {
        state.episodes.indexOfFirst { it.number == state.resumeEpisode }
    } else {
        -1
    }
    LaunchedEffect(viewingEpisodes, resumeIndex) {
        if (!viewingEpisodes) return@LaunchedEffect
        if (resumeIndex < 0 || episodesTab != null) {
            runCatching { seasonFocus.requestFocus() }
            return@LaunchedEffect
        }
        // Спершу докрутити, потім просити фокус: LazyRow компонує лише видиме, і на картці
        // за краєм екрана requestFocus мовчки не робить нічого — саме так п'ята серія
        // лишалась без фокуса, поки ряд стояв на першій.
        // Над серіями може стояти рядок «Що було в N−1 сезоні» — він зсуває індекси.
        // Нульовий рядок — сезони, далі може стояти «Що було в N−1 сезоні»; серія — на 60 px від верху.
        episodeRail.scrollToItem(resumeIndex + 1 + if (state.recapBefore > 0) 1 else 0, -episodeAnchorPx)
        runCatching { resumeEpisodeFocus.requestFocus() }
    }
    LaunchedEffect(viewingEpisodes) {
        if (!viewingEpisodes) runCatching { firstActionFocus.requestFocus() }
    }
    // Поки йде підготовка, кнопки сторінки не фокусабельні (див. sceneBlocked), і фокус
    // з них падає. Після підготовки — чи то запуск плеєра, чи відмова — його треба
    // повернути: інакше після виходу з плеєра сторінка стояла без фокуса, і пульт
    // «оживав» лише зі стрілки.
    LaunchedEffect(state.preparing) {
        if (!state.preparing && !viewingEpisodes) firstActionFocus.requestAfterFrame()
    }

    // Поки відкрита модалка або триває підготовка, СЦЕНИ під нею фокус не беруть: інакше
    // по картці можна бігати пультом крізь оверлей, а натискання йдуть у невидиме.
    //
    // Саме сцени, а не весь екран: `LocalFocusBlocked` вимикає фокус усім `FocusableSurface`
    // нижче в дереві, і обгорнутий цілком екран гасив разом зі сторінкою саму модалку —
    // кнопка «Шукати» ставала недосяжною. Оверлеї навмисно лежать ПОЗА цією обгорткою.
    val sceneBlocked = state.picker != null || state.preparing
    Box(modifier = Modifier.fillMaxSize()) {
        // Деталі не мають успадковувати щільне затемнення каталогу: тут кадр є частиною
        // історії матеріалу, тому темними лишаються лише полиця під текстом і нижній край.
        // Сцена серій має власний фон: не затемнений кадр, а світла кольорова пляма з
        // нього ж — так це виглядає в Apple ([apple-tv/07.webp]).
        // Прототип: кадр той самий на обох сценах; на сцені серій поверх нього — графіт
        // .9 (.dim), що наростає за .4s.
        HeroBackdrop(
            imageUri = TmdbImages.backdrop(details?.item?.backdropPath),
            tone = HeroBackdropTone.Detail,
            stills = if (state.liveStills && !viewingEpisodes) details?.stills.orEmpty().mapNotNull(TmdbImages::backdrop) else emptyList(),
            parallax = { parallax },
        )
        val dim by animateFloatAsState(if (viewingEpisodes) 1f else 0f, tween(400), label = "dim")
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(LikhtarColors.BackgroundDeep.copy(alpha = 0.9f)))

        if (details == null) {
            if (state.loading) {
                LanternLoaderScreen(phrases = titlePhrases())
            } else {
                val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                // «Спробувати ще», а не «Назад»: назад є на пульті, а повторити запит — ні.
                EmptyState(
                    title = state.message ?: stringResource(R.string.cinema_page_failed),
                    message = stringResource(R.string.cinema_page_failed_hint),
                    actionLabel = stringResource(R.string.cinema_retry),
                    onAction = viewModel::retry,
                )
            }
            return@Box
        }

        CompositionLocalProvider(LocalFocusBlocked provides sceneBlocked) {
        if (!viewingEpisodes) {
            // Перша сцена — тільки загальна інформація й дії. ↓ з будь-якої її дії
            // відкриває самостійний екран серій, без частково видимого hero зверху.
            // Поля ширші за загальні 32dp: текст і кнопки стоять просто на кадрі, і біля
            // самого краю блок читався як приклеєний до рамки телевізора.
            // Як в Apple TV: спершу фон, і лише потім плавно — усе інше.
            // Тільки прозорість: зсув шару над вузлами фокуса ламає D-pad.
            val reveal = remember(details.item.tmdbId) { Animatable(0f) }
            LaunchedEffect(details.item.tmdbId) {
                delay(RevealDelayMs)
                reveal.animateTo(1f, tween(RevealMs))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = reveal.value }
                    .onFocusedBoundsChanged { focused ->
                        val root = focused?.findRootCoordinates() ?: return@onFocusedBoundsChanged
                        val center = focused.boundsInRoot().center.x
                        if (root.size.width > 0) focusX = center / root.size.width
                    }
                    .padding(horizontal = HeroPadding),
            ) {
                PosterHead(
                    details = details,
                    mainLabel = mainActionLabel(details, state),
                    actionsOrder = state.actionsOrder,
                    appOptions = state.appOptions,
                    inWatchlist = state.inWatchlist,
                    uaAudio = state.uaAudio,
                    uaProof = state.uaProof,
                    voices = state.voices,
                    quality = state.quality,
                    playFocus = firstActionFocus,
                    onPlay = if (state.resumeAirDate.isNotBlank()) {
                        // Кнопка, яка нічого не вмикає, — гірше за її відсутність. Поки серії
                        // немає, головна дія веде туди, де видно весь сезон і дати виходу.
                        { viewingEpisodes = true }
                    } else {
                        viewModel::watchBest
                    },
                    onPick = { viewModel.openPicker() },
                    onWatchlist = viewModel::toggleWatchlist,
                    onOpenFranchise = onOpenFranchise,
                    uaWaitLabel = state.uaWaitSince?.let { since ->
                        if (since > 0) stringResource(R.string.cinema_ua_wait_since, java.text.SimpleDateFormat("d.MM", java.util.Locale.US).format(java.util.Date(since * 1000)))
                        else stringResource(R.string.cinema_ua_wait)
                    },
                    onUaWait = viewModel::toggleUaWait,
                    mark = markState(state.mark),
                    onMark = viewModel::openMarkPanel,
                    // Під вкладкою «Серії» визирають кадри серій (з тієї, на якій зупинились),
                    // під «Подібним» — подібне; інакше самі вкладки, без чужих картинок.
                    // Під вкладками визирає наступна серія — та, на яку падає світло.
                    peekEpisode = state.episodes.getOrNull(resumeIndex.coerceAtLeast(0))
                        ?.takeIf { details.seasonNumbers.isNotEmpty() }
                        ?.let { ep -> TmdbImages.still(ep.stillPath) to (if (ep.hasOwnTitle) "${ep.number}. ${ep.title}" else ep.title) },
                    onOpenElsewhere = viewModel::open,
                    localLabel = stringResource(R.string.cinema_play_local).takeIf { localFiles.isNotEmpty() },
                    onLocal = { if (localFiles.size == 1) viewModel.playLocal(localFiles[0]) else pickingLocal = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .onKeyEvent { event ->
                            // Не «якщо є сезони», а «якщо на другій сцені є що показати».
                            // Деталі є завжди — саме там лежить повний синопсис, який ця
                            // сцена обрізає до двох речень, — тож для фільму вхід теж є.
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                // Спершу даємо фокусу піти вниз У МЕЖАХ сцени, і лише
                                // коли йти нема куди — відкриваємо другу. Без цієї
                                // перевірки ряд дій, що не вліз в один рядок, був
                                // недосяжний: перше ж ↓ забирало зі сцени, і до нижніх
                                // кнопок не можна було дістатись узагалі.
                                if (!focusManager.moveFocus(FocusDirection.Down)) {
                                    episodesTab = null
                                    viewingEpisodes = true
                                }
                                true
                            } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp &&
                                trailer != null && state.trailerOnUp && !focusManager.moveFocus(FocusDirection.Up)
                            ) {
                                // Вище за кнопки нічого немає — туди «виїжджає» трейлер: офіційний
                                // YouTube на весь екран, BACK повертає сюди.
                                viewModel.openVideo(trailer)
                                true
                            } else {
                                false
                            }
                        },
                )
                if (trailer != null && state.trailerOnUp) {
                    // .hint: «˄ Трейлер ˄» — 22 px / 600, .8, 40 px від верху.
                    Row(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val tint = LikhtarColors.Paper.copy(alpha = 0.8f)
                        androidx.tv.material3.Icon(androidx.compose.ui.res.painterResource(com.likhtar.home.core.designsystem.R.drawable.ic_solar_arrow_up), null, tint = tint, modifier = Modifier.size(13.dp))
                        Text(
                            text = stringResource(R.string.cinema_trailer),
                            style = androidx.compose.ui.text.TextStyle(fontFamily = LikhtarType.Body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.22.sp),
                            color = tint,
                        )
                        androidx.tv.material3.Icon(androidx.compose.ui.res.painterResource(com.likhtar.home.core.designsystem.R.drawable.ic_solar_arrow_up), null, tint = tint, modifier = Modifier.size(13.dp))
                    }
                }
                // «Чи варто?» на першій сцені більше не плашкою: у прототипі його кличе вогник на вкладці.
            }
        } else {
            EpisodesScene(
                details = details,
                state = state,
                firstSeasonFocus = seasonFocus,
                resumeEpisodeFocus = resumeEpisodeFocus,
                episodeRail = episodeRail,
                onExit = { viewingEpisodes = false },
                onSeason = viewModel::showSeason,
                onEpisode = viewModel::watchEpisode,
                onVideo = viewModel::openVideo,
                onOpenTitle = onOpenTitle,
                onFranchiseParts = viewModel::loadFranchise,
                onRecap = viewModel::openRecap,
                startTab = episodesTab,
            )
        }
        }

        if (state.markPanel) details?.let { d ->
            MarkPanel(
                title = d.item.title,
                isTv = d.item.kind == TitleKind.TV,
                mark = state.mark,
                season = if (d.item.kind == TitleKind.TV) state.season else 0,
                resumeSeason = state.resumeSeason,
                resumeEpisode = state.resumeEpisode,
                genres = d.genres,
                franchise = d.franchise?.name,
                onChoice = { c -> viewModel.applyMark(c.mark, c.scope, c.reason, c.rating, c.season, c.episode) },
                onClear = viewModel::clearMark,
                onDismiss = viewModel::closeMarkPanel,
            )
        }

        state.recap?.let { recap ->
            RecapDialog(recap = recap, title = details?.item?.title.orEmpty(), onDismiss = viewModel::closeRecap)
        }

        // Прогрів роздачі: без нього перші секунди торента плеєр стоїть чорним екраном.
        // Відсотків тут немає навмисно — сервер віддає прочитані байти, а це не позиція
        // у фільмі, і показувати їх користувачу немає сенсу.
        if (state.preparing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LikhtarColors.BackgroundDeep.copy(alpha = 0.82f)),
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Живий голос замість «Готуємо»: фрази міняються, поки сервер перебирає
                    // роздачі — 15–20 секунд не лякають, коли видно, що щось відбувається.
                    val phrase = rememberPreparingPhrase(buffering = (state.preparingStat?.bufferShare ?: 0f) > 0f)
                    val note = state.preparingNote
                        ?: (stringResource(R.string.cinema_prep_connecting) to stringResource(R.string.cinema_prep_waiting_peers))
                    LanternLoader(title = phrase, subtitle = note.first)
                    ReleaseChips(note.second)
                    state.preparingStat?.let { stat -> PreparingStat(stat, needMbps(note.second)) }
                }
            }
        }

        // Оверлей вибору — поверх картки, щоб BACK крокував усередині вибору,
        // а не викидав зі сторінки фільму.
        state.picker?.let { picker ->
            StreamPicker(
                picker = picker,
                voices = viewModel.visibleVoices(),
                emptyNote = state.details?.scan.emptyNote(),
                onPlay = viewModel::playVoice,
                onBack = viewModel::back,
                onSearch = viewModel::searchByName.takeIf { state.details?.scan?.known == false },
            )
        }

        // Повідомлення сторінки («Не вдалося відкрити плеєр», «Позначку знято») — досі тут
        // не показувались зовсім, поки сторінка відкрита.
        if (!state.preparing) {
            Notice(message = state.message, modifier = Modifier.align(Alignment.BottomCenter), onShown = viewModel::clearMessage)
        }
    }
}

/**
 * Що обіцяє головна дія — рівно те, що вона зробить.
 *
 * «Кнопка каже куди веде, не абстрактне „Дивитися"» (IDEAS.md). Для серіала це означає
 * назвати серію, і саме тому потрібен [TitleUiState.resumeKnown]: поки відповідь про
 * продовження в дорозі, чесніше сказати просто «Дивитись», ніж пообіцяти першу серію
 * й за півсекунди підмінити підпис.
 */
@Composable
private fun mainActionLabel(details: TitleDetails, state: TitleUiState): String {
    // Одна кнопка на обидва випадки: «Продовжити» — це та сама дія, лише з таймкоду.
    if (details.item.kind != TitleKind.TV) {
        return stringResource(if (state.resumeStarted) R.string.cinema_continue else R.string.cinema_start)
    }
    if (!state.resumeKnown) return stringResource(R.string.cinema_start)
    val resuming = state.resumeSeason > 0 && state.resumeEpisode > 0
    val season = if (resuming) state.resumeSeason else details.seasonNumbers.firstOrNull { it > 0 } ?: 1
    val episode = if (resuming) state.resumeEpisode else 1
    val code = String.format(java.util.Locale.US, "S%02dE%02d", season, episode)
    // Серія ще попереду: підпис каже коли, а не обіцяє відтворення. Без трикутника — він
    // тут читався б як «зараз почнеться».
    if (state.resumeAirDate.isNotBlank()) return "$code — ${humanDate(state.resumeAirDate)}"
    return stringResource(if (resuming) R.string.cinema_continue_code else R.string.cinema_start_code, code)
}

/** «2022-02-03» → «3 лют. 2022»: сирий ISO на картці читається як службовий код. */
internal fun humanDate(iso: String): String = runCatching {
    java.time.LocalDate.parse(iso)
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.getDefault()))
}.getOrDefault(iso)

/** Частка ширини екрана під інформаційний блок сторінки — решта лишається кадру. */
private val HeroPadding = LikhtarDimens.ScreenPadding

/** Поява сцени після фону: пауза, щоб кадр устиг проявитись, і сам наплив. */
private const val RevealDelayMs = 250L
private const val RevealMs = 550

/** Фрази очікування сторінки тайтла. */
@Composable
internal fun titlePhrases(): List<String> = stringArrayResource(R.array.cinema_title_phrases).toList()
