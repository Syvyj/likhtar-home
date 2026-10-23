package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.data.TitleActions
import com.likhtar.home.core.designsystem.Notice
import com.likhtar.home.core.designsystem.EmptyState
import com.likhtar.home.core.designsystem.HeroBackdrop
import com.likhtar.home.core.designsystem.HeroBackdropTone
import com.likhtar.home.core.designsystem.LanternLoaderScreen
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import com.likhtar.home.core.designsystem.requestAfterFrame
import kotlinx.coroutines.launch

/**
 * Сторінка шоу — та сама сторінка матеріалу, що в Кінозалі.
 *
 * Обидві сцени тут не свої: шапка — `PosterHead`, випуски — `EpisodesScene`, обидві з
 * `TitleScreen`. Різниця лише в даних і в трьох підписах («шоу» замість «серіал», назва
 * сезону замість номера, «Дивитись» без озвучок), бо озвучок і торентів у Телепорталу
 * немає — він віддає готовий потік.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RealityTitleScreen(viewModel: RealityTitleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val details = state.details
    val firstActionFocus = rememberInitialFocusRequester(if (details != null) 1 else 0)
    val seasonFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val episodeRail = rememberLazyListState()
    var viewingEpisodes by remember { mutableStateOf(false) }

    // BACK у випусках повертає на шапку; наступний уже обробить навігація застосунку.
    BackHandler(enabled = viewingEpisodes) { viewingEpisodes = false }
    // Серія, яку відкривали (YouTube): на ній фокус після повернення з YouTube.
    val openedIndex = (state.resumeEpisode - 1).takeIf { state.resumeEpisode > 0 && state.season == state.resumeSeason && it < state.episodes.size }
    LaunchedEffect(viewingEpisodes) {
        if (viewingEpisodes) {
            // На саму серію ставить курсор сцени серій (EpisodesScene, resumeRow).
            if (openedIndex == null) runCatching { seasonFocus.requestFocus() }
        } else {
            runCatching { firstActionFocus.requestFocus() }
        }
    }
    // Повернення з YouTube — фокус на щойно відкриту серію, а не туди, куди його поставить
    // система (на перший-ліпший вузол), — так само, як після трейлера на сторінці тайтла.
    val resumeScope = rememberCoroutineScope()
    LifecycleResumeEffect(viewingEpisodes) {
        viewModel.onReturnedFromYoutube()
        if (viewingEpisodes && openedIndex != null) resumeScope.launch { episodeFocus.requestAfterFrame() }
        onPauseOrDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val backdrop = TmdbImages.backdrop(details?.item?.backdropPath)
        // Як на сторінці тайтла (прототип): той самий кадр, на сцені серій — графіт .9.
        HeroBackdrop(imageUri = backdrop, tone = HeroBackdropTone.Detail)
        if (viewingEpisodes) Box(Modifier.fillMaxSize().background(com.likhtar.home.core.designsystem.LikhtarColors.BackgroundDeep.copy(alpha = 0.9f)))

        if (details == null) {
            if (state.loading) {
                LanternLoaderScreen(phrases = titlePhrases())
            } else {
                val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                EmptyState(
                    title = state.message ?: stringResource(R.string.cinema_page_failed),
                    message = stringResource(R.string.cinema_page_failed_hint),
                    actionLabel = stringResource(R.string.cinema_back),
                    onAction = { backDispatcher?.onBackPressed() },
                )
            }
            return@Box
        }

        if (!viewingEpisodes) {
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = HeroPadding)) {
                PosterHead(
                    details = details,
                    mainLabel = stringResource(R.string.cinema_watch_plain), // знак ліхтаря вже на кнопці
                    // Лише головна дія: озвучок, сервісів і списку для шоу Телепорталу
                    // немає — потік у нього один, і він свій.
                    actionsOrder = listOf(TitleActions.PLAY, TitleActions.SERVICES),
                    appOptions = state.services + listOfNotNull(state.youtube),
                    inWatchlist = false,
                    playFocus = firstActionFocus,
                    onPlay = {
                        if (state.seasons.isEmpty() && !state.youtubeEpisodes) viewModel.watchMain() else viewingEpisodes = true
                    },
                    // Позиції Реаліті озвучок не мають: у порталів і ютубу доріжка одна.
                    onPick = {},
                    onWatchlist = {},
                    onOpenFranchise = {},
                    onOpenElsewhere = viewModel::openYoutube,
                    kindLabel = stringResource(if (state.serial) R.string.cinema_kind_series else R.string.cinema_kind_show),
                    // Портали й YouTube мовників — українське за самим джерелом; доказом
                    // стоїть мовник, як сервіс на сторінці тайтла: «UA · СТБ».
                    uaAudio = true,
                    uaProof = listOfNotNull(state.channel.takeIf { it.isNotBlank() }),
                    modifier = Modifier
                        .fillMaxSize()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                viewingEpisodes = true
                                true
                            } else {
                                false
                            }
                        },
                )
            }
        } else {
            EpisodesScene(
                details = details,
                // Друга сцена читає стан Кінозалу — віддаємо їй рівно ті два поля, які в
                // неї є сенс: обраний сезон і його випуски. Решта (озвучки, таймкоди,
                // франшиза) для шоу Телепорталу не існує, і порожні значення тут чесні.
                state = TitleUiState(
                    loading = false, season = state.season, episodes = state.episodes,
                    resumeSeason = state.resumeSeason, resumeEpisode = state.resumeEpisode,
                ),
                firstSeasonFocus = seasonFocus,
                resumeEpisodeFocus = episodeFocus,
                episodeRail = episodeRail,
                onExit = { viewingEpisodes = false },
                onSeason = viewModel::showSeason,
                onEpisode = viewModel::watch,
                onVideo = {},
                onOpenTitle = {},
                onFranchiseParts = {},
                seasonLabel = viewModel::seasonLabel,
                // Номер випуску вже стоїть у його назві, а дату має кожен випуск — саме
                // за нею шоу й дивляться.
                episodeNumbers = false,
                episodeDates = true,
                // Логотипів у шоу Телепорталу немає — вгорі завжди назва текстом, і їй
                // потрібна не третина екрана, а більшість рядка.
                titleWidth = 0.7f,
                // Опис Телепорталу — SEO-текст на кілька тисяч знаків: без стелі він
                // заливав увесь екран і все одно йшов за нижній край.
                overviewMaxLines = 14,
                castLabel = stringResource(R.string.cinema_hosts),
                channel = state.channel,
            )
        }

        state.nextUp?.let { next ->
            NextEpisodeCard(
                episode = next,
                countdown = state.nextUpAuto,
                newSeason = state.nextUpSeason.takeIf { it != state.season } ?: 0,
                onPlay = viewModel::playNext,
                onCancel = {
                    viewModel.cancelNext()
                    resumeScope.launch { episodeFocus.requestAfterFrame() }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(LikhtarDimens.ScreenPadding),
            )
        }

        // Відмова плеєрної ручки має бути видною. Без цього натиснутий випуск без запису
        // не робив НІЧОГО: причина лягала в стан і там і лишалась — на екрані та сама
        // сцена, і з дивана це читається як «застосунок завис».
        Notice(message = state.message, modifier = Modifier.align(Alignment.BottomCenter), onShown = viewModel::clearMessage)
        com.likhtar.home.core.designsystem.PreparingScrim(
            visible = state.searching,
            title = stringResource(R.string.cinema_searching_ukrainian),
            subtitle = stringResource(R.string.cinema_searching_ukrainian_sources),
        )
    }
}

/** Поля ширші за загальні 32dp — рівно ті самі, що на сторінці матеріалу Кінозалу. */
private val HeroPadding = 52.dp
