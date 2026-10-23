package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.likhtar.home.core.catalog.Candidate
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.catalog.WatchMood
import com.likhtar.home.core.catalog.WatchTime
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.SectionTitle
import com.likhtar.home.core.designsystem.cachedScrim
import com.likhtar.home.core.designsystem.requestAfterFrame

/** Картка кандидата: майже весь екран, але саме «майже» — краї віддані сусідам. */
private const val CandidateCardWidth = 0.84f
private const val CandidateCardHeight = 0.86f
private const val CandidateCardWeight = 9f

/** Панель радника: вужча за третину екрана, щоб за нею лишався видимим Кінозал. */
private val AdvisorPanelWidth = 380.dp

/**
 * Відповідь радника: кандидат майже на весь екран, сусіди визирають з боків.
 *
 * Не список і не ряд карток: питання було одне — «що подивитись», — і відповідь має бути
 * однією. Сусіди по краях існують рівно для того, щоб було видно, що вона не єдина, і щоб
 * ← → читались без підказки.
 */
@Composable
internal fun CandidatesOverlay(
    candidates: List<Candidate>,
    index: Int,
    onIndex: (Int) -> Unit,
    onOpen: (CatalogItem) -> Unit,
    onEmptyBack: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var forward by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { focus.requestAfterFrame() }
    Box(
        modifier = Modifier.fillMaxSize().background(LikhtarColors.Background.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        // Поки підбір їде, картка вже існує — просто порожня. Оверлей без жодного
        // фокусованого вузла віддавав фокус сторінці позаду, і той більше не повертався.
        val safe = index.coerceIn(0, maxOf(candidates.lastIndex, 0))
        val candidate = candidates.getOrNull(safe)

        // Сусіди — самі кадри, без тексту: вони тут не для читання, а для відчуття ряду.
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CandidatePeek(candidates.getOrNull(safe - 1)?.item, Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(CandidateCardWeight))
            CandidatePeek(candidates.getOrNull(safe + 1)?.item, Modifier.weight(1f))
        }

        FocusableSurface(
            onClick = { candidate?.let { onOpen(it.item) } ?: onEmptyBack() },
            modifier = Modifier
                .fillMaxWidth(CandidateCardWidth)
                .fillMaxHeight(CandidateCardHeight)
                .focusRequester(focus)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        // По колу: список кандидатів короткий, і впертись у край на п'ятому
                        // з п'яти — це тупик там, де його не мало б бути.
                        Key.DirectionRight -> {
                            if (candidates.isNotEmpty()) {
                                forward = true
                                onIndex((safe + 1) % candidates.size)
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            if (candidates.isNotEmpty()) {
                                forward = false
                                onIndex((safe - 1 + candidates.size) % candidates.size)
                            }
                            true
                        }
                        else -> false
                    }
                },
            shape = RoundedCornerShape(LikhtarDimens.CornerLarge),
            background = LikhtarColors.CardBlack,
            focusedBackground = LikhtarColors.SurfaceAlt,
            focusedScale = 1f,
        ) { _ ->
            if (candidate == null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.cinema_advisor_picking),
                        style = LikhtarType.SectionTitle,
                        color = LikhtarColors.TextSecondary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                return@FocusableSurface
            }
            Box(modifier = Modifier.fillMaxSize()) {
                // Кадр перетікає, а текст приходить на такт пізніше — та сама мова, що в
                // героя нагорі: різкий обмін місцями читався як перемальовування, а не як
                // перехід до наступного кандидата.
                AnimatedContent(
                    targetState = candidate.item,
                    transitionSpec = {
                        val from = if (forward) 1 else -1
                        slideInHorizontally(tween(HeroSlideMs, easing = FastOutSlowInEasing)) {
                            it * from / 4
                        } + fadeIn(tween(HeroSlideMs)) togetherWith fadeOut(tween(HeroSlideMs))
                    },
                    label = "candidateArt",
                ) { shown ->
                    AsyncImage(
                        // Порадник приходить із сервера й віддає лише постер: бекдропа в
                        // його відповіді немає, і без фолбеку картка кандидата була
                        // порожньою плямою.
                        model = TmdbImages.backdrop(shown.backdropPath)
                            ?: TmdbImages.poster(shown.posterPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .cachedScrim(dim = Color.Transparent) { listOf(HeroScrim) },
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.62f)
                        .padding(34.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TitleLogoArea(
                        logoPath = candidate.item.logoPath,
                        title = candidate.item.title,
                        height = HeroLogoHeight,
                    )
                    Text(
                        text = itemMeta(candidate.item),
                        style = LikhtarType.Body2,
                        color = LikhtarColors.TextSecondary,
                    )
                    if (candidate.why.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.cinema_advisor_because_liked, candidate.why),
                            style = LikhtarType.Label,
                            color = LikhtarColors.LanternAmber,
                        )
                    }
                    // Опис TMDB — те, за чим людина й вирішує. Був схований за тим, що
                    // радник «має говорити від себе», але без картки Ліхтаря він говорив
                    // шаблоном, однаковим у всіх п'яти кандидатів.
                    if (candidate.item.overview.isNotBlank()) {
                        Text(
                            text = candidate.item.overview,
                            style = LikhtarType.Body1,
                            color = LikhtarColors.TextPrimary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // «Чи варто» — слово радника, і воно є лише там, де є картка Ліхтаря.
                    if (candidate.worth.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.cinema_advisor_worth, candidate.worth),
                            style = LikhtarType.Body2,
                            color = LikhtarColors.TextSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(R.string.cinema_advisor_ok_open),
                        style = LikhtarType.MonoSmall,
                        color = LikhtarColors.TextMuted,
                    )
                    if (candidates.size > 1) {
                        HeroDots(count = candidates.size, active = safe)
                    }
                }
            }
        }
    }
}

/** Сусідній кандидат: лише край кадру, притлумлений. */
@Composable
private fun CandidatePeek(item: CatalogItem?, modifier: Modifier) {
    Box(modifier = modifier.fillMaxHeight(CandidateCardHeight * 0.94f)) {
        if (item == null) return@Box
        AsyncImage(
            model = TmdbImages.backdrop(item.backdropPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(LikhtarDimens.CornerLarge)),
        )
        Box(modifier = Modifier.fillMaxSize().background(LikhtarColors.Background.copy(alpha = 0.62f)))
    }
}

/**
 * Радник бічною панеллю.
 *
 * Панеллю, а не колонкою на сторінці: він потрібен зрідка й саме тоді, коли решта екрана
 * не допомогла, — тримати під нього третину Кінозалу постійно було задорого. І тому це
 * окремий компонент: та сама панель має відкриватись і з Головної.
 */
@Composable
internal fun AdvisorPanel(
    state: CinemaUiState,
    onTime: (WatchTime) -> Unit,
    onMood: (WatchMood) -> Unit,
    onShow: () -> Unit,
    onClose: () -> Unit,
) {
    val entry = remember { FocusRequester() }
    LaunchedEffect(Unit) { entry.requestAfterFrame() }
    Box(modifier = Modifier.fillMaxSize().background(LikhtarColors.Background.copy(alpha = 0.69f))) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(AdvisorPanelWidth)
                .background(LikhtarColors.Surface)
                .padding(horizontal = 22.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(text = stringResource(R.string.cinema_what_to_watch))
            Text(
                text = stringResource(R.string.cinema_advisor_how_much_time),
                style = LikhtarType.MonoSmall,
                color = LikhtarColors.TextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            WatchTime.entries.forEachIndexed { index, time ->
                AnswerPill(
                    text = stringResource(time.titleRes()),
                    selected = state.time == time,
                    onClick = { onTime(time) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(entry) else Modifier),
                )
            }
            Text(
                text = stringResource(R.string.cinema_advisor_mood),
                style = LikhtarType.MonoSmall,
                color = LikhtarColors.TextMuted,
                modifier = Modifier.padding(top = 10.dp),
            )
            // Вузька панель Кінозалу — три первісні настрої; решта — у Ліхтарику, там ряд прокручується.
            listOf(WatchMood.LIGHT, WatchMood.TENSE, WatchMood.SMART).forEach { mood ->
                AnswerPill(
                    text = stringResource(mood.titleRes()),
                    selected = state.mood == mood,
                    onClick = { onMood(mood) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Дія, а не вибір — кнопка, а не чип: світлішає лише під фокусом, як усі дії.
            LikhtarButton(
                label = stringResource(R.string.cinema_advisor_show),
                onClick = {
                    onShow()
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun WatchTime.titleRes(): Int = when (this) {
    WatchTime.NINETY -> R.string.cinema_advisor_time_ninety
    WatchTime.EPISODE -> R.string.cinema_advisor_time_episode
    WatchTime.ANY -> R.string.cinema_advisor_time_any
}

private fun WatchMood.titleRes(): Int = when (this) {
    WatchMood.LIGHT -> R.string.cinema_advisor_mood_light
    WatchMood.TENSE -> R.string.cinema_advisor_mood_tense
    WatchMood.SMART -> R.string.cinema_advisor_mood_smart
    WatchMood.FUNNY -> R.string.cinema_advisor_mood_funny
    WatchMood.ROMANTIC -> R.string.cinema_advisor_mood_romantic
    WatchMood.MYSTERY -> R.string.cinema_advisor_mood_mystery
    WatchMood.ADVENTURE -> R.string.cinema_advisor_mood_adventure
}
