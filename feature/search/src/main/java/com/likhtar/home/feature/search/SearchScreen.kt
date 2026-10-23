package com.likhtar.home.feature.search

import android.content.res.Resources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.likhtar.home.core.designsystem.PreparingScrim
import com.likhtar.home.core.designsystem.MicrophoneIcon
import com.likhtar.home.core.designsystem.requestWithRetry
import com.likhtar.home.core.designsystem.LikhtarChip
import com.likhtar.home.core.designsystem.FOCUS_FRAMES
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import com.likhtar.home.core.catalog.Advice
import com.likhtar.home.core.designsystem.LikhtarIcons
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.ResumeCard
import com.likhtar.home.core.catalog.open
import com.likhtar.home.core.catalog.realityRouteId
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarScreenScaffold
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.NoBringIntoView
import com.likhtar.home.core.designsystem.QrCode
import com.likhtar.home.core.designsystem.centerItem
import com.likhtar.home.core.designsystem.focusDirections
import com.likhtar.home.core.designsystem.requestAfterFrame
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.model.SearchLaunch
import com.likhtar.home.feature.cinema.TitleSpotlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ліхтарик — компаньйон великого Ліхтаря: шукає, пам'ятає, що ви дивились і відклали, і
 * радить.
 *
 * Порожнє поле — історія, закладки, поради Ліхтарика й ряди «з чого почати». З запитом —
 * найкращий збіг банером, здогадки Ліхтарика за описом («фільм про синіх інопланетян» →
 * «Аватар») і ряди TMDB. Фокус на старті — на першій картці, а не в полі: клавіатура на
 * пів екрана без запиту заважала. Ввести можна пультом, голосом чи з телефону.
 */
@Composable
fun SearchScreen(
    onSelectSection: (LikhtarSection) -> Unit,
    onOpenTitle: (CatalogItem) -> Unit,
    /** Наше шоу відкривається сторінкою «Реаліті», а не сторінкою Кінозалу. */
    onOpenShow: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    companion: CompanionViewModel = hiltViewModel(),
    advisorModel: AdvisorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val advisor by advisorModel.uiState.collectAsStateWithLifecycle()
    val history by companion.history.collectAsStateWithLifecycle()
    val bookmarks by companion.bookmarks.collectAsStateWithLifecycle()
    val advice by companion.advice.collectAsStateWithLifecycle()
    val message by companion.message.collectAsStateWithLifecycle()
    val voiceOnTv by companion.voiceOnTv.collectAsStateWithLifecycle()
    val canAsk by viewModel.canAsk.collectAsStateWithLifecycle()
    val personal by companion.personal.collectAsStateWithLifecycle()
    val shelves = Shelves(history.orEmpty(), bookmarks, advice, personal)
    val res = LocalContext.current.resources

    val menuFocusRequester = remember { FocusRequester() }
    val queryFocusRequester = remember { FocusRequester() }
    val voiceFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val bookmarksFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Прийшли з «Пошуку» на Головній — фокус у поле, а не на першу картку.
    val focusField = remember { SearchLaunch.focusField.also { SearchLaunch.focusField = false } }
    // Прийшли з «Закладок» у Кінозалі — ряд закладок першим, і початковий фокус (перша
    // картка сторінки) падає саме на нього.
    val bookmarksFirst = remember { SearchLaunch.focusBookmarks.also { SearchLaunch.focusBookmarks = false } }
    // Готовий запит із Головної — Ліхтарик відповідає на нього одразу.
    val launchQuery = remember { SearchLaunch.query.also { SearchLaunch.query = null } }
    LaunchedEffect(Unit) { launchQuery?.let { viewModel.setQuery(it); viewModel.submit() } }
    LaunchedEffect(Unit) {
        if (!focusField) return@LaunchedEffect
        withFrameNanos { }
        queryFocusRequester.requestWithRetry()
    }

    // Початковий фокус (прототип): у раднику — вибраний настрій, у пошуку — «Відкрити»
    // найкращого збігу; з «Закладок» Кінозалу — перша закладка під згином. Вузли з'являються
    // після даних, тож просимо, доки не стане.
    LaunchedEffect(focusField, launchQuery) {
        if (focusField || launchQuery != null) return@LaunchedEffect
        repeat(FOCUS_FRAMES * 4) {
            withFrameNanos { }
            val ok = runCatching { (if (bookmarksFirst) bookmarksFocus else contentFocus).requestFocus() }.isSuccess
            if (ok) return@LaunchedEffect
        }
    }
    // Готовий запит із Головної — як «Пошук»: фокус на «Відкрити» найкращого збігу.
    var focusResults by remember { mutableStateOf(launchQuery != null) }
    LaunchedEffect(focusResults, state.status, state.best) {
        if (focusResults && state.best != null) {
            contentFocus.requestAfterFrame()
            focusResults = false
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            focusResults = true
            viewModel.setQuery(spoken)
        }
    }
    val startVoice: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, res.getString(R.string.search_voice_prompt))
        }
        runCatching { voiceLauncher.launch(intent) }
    }
    val open: (CatalogItem) -> Unit = { item ->
        if (state.query.isNotBlank()) viewModel.rememberQuery()
        val ours = viewModel.portalId(item)
        if (ours != null) onOpenShow(ours) else onOpenTitle(item)
    }
    // Історія веде так само, як ряди «Продовжити» на інших екранах (ResumeCard.open).
    val playCard: (ResumeCard) -> Unit = { card ->
        card.open(
            onShow = { key -> realityRouteId(key)?.let(onOpenShow) ?: onSelectSection(LikhtarSection.REALITY) },
            onTitle = onOpenTitle,
            onLaunch = { if (!companion.play(it)) onSelectSection(it.section) },
            onSection = onSelectSection,
        )
    }

    // «З телефону»: адреса з'являється лише після натискання — до того сервер не слухає.
    var phone by remember { mutableStateOf<PhoneLink?>(null) }
    val openPhone: () -> Unit = {
        scope.launch { phone = PhoneLink(withContext(Dispatchers.IO) { companion.phone.start() }) }
    }
    LaunchedEffect(state, shelves) { companion.phone.state = phoneState(state, shelves, companion::isBookmarked, res) }
    LaunchedEffect(Unit) {
        companion.phone.commands.collect { cmd ->
            when (cmd) {
                PhoneCommand.Hello -> {
                    phone = null
                    companion.say(res.getString(R.string.search_phone_connected))
                }
                is PhoneCommand.Type -> viewModel.type(cmd.text)
                is PhoneCommand.Query -> {
                    viewModel.setQuery(cmd.text)
                    // Ряд, на якому стояв фокус, зникає разом зі старою видачею, і фокус падав
                    // у верхнє меню. Тримаємо його на «З телефону» — людина все одно в телефоні.
                    runCatching { phoneFocus.requestFocus() }
                }
                is PhoneCommand.Open -> when (val target = cmd.entry.target) {
                    is ResumeCard -> playCard(target)
                    is CatalogItem -> open(target)
                }
                is PhoneCommand.Bookmark -> when (val target = cmd.entry.target) {
                    is ResumeCard -> target.continueItem?.let { companion.toggleBookmark(it.asCatalogItem()) }
                    is CatalogItem -> companion.toggleBookmark(target)
                }
            }
        }
    }

    var forgetting by remember { mutableStateOf<ResumeCard?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LikhtarikPage(
            state = state,
            advisor = advisor,
            shelves = shelves,
            voiceOnTv = voiceOnTv,
            canAsk = canAsk,
            queryFocus = queryFocusRequester,
            menuFocus = menuFocusRequester,
            voiceFocus = voiceFocus,
            phoneFocus = phoneFocus,
            startFocus = contentFocus,
            bookmarksFocus = bookmarksFocus,
            onType = viewModel::type,
            onSubmit = {
                focusResults = true
                viewModel.submit()
            },
            onVoice = startVoice,
            onPhone = openPhone,
            // «Що подивитись» — радник: порожній запит і є його стан.
            onAdvisor = viewModel::clear,
            onPickRecent = { query ->
                focusResults = true
                viewModel.useSuggestion(query)
            },
            onClearRecent = viewModel::clearRecent,
            onFormat = advisorModel::selectFormat,
            onMood = advisorModel::selectMood,
            open = open,
            playCard = playCard,
            onForget = { forgetting = it },
            onBookmark = companion::toggleBookmark,
        )
        // Меню поверх сторінки: її розміри відраховані від верху екрана, як у прототипі.
        LikhtarScreenScaffold(
            section = LikhtarSection.SEARCH,
            onSelectSection = onSelectSection,
            menuFocusRequester = menuFocusRequester,
            // ↓ з меню — у поле: воно перше під меню.
            contentFocusRequester = queryFocusRequester,
        ) {}

        forgetting?.let { card ->
            ForgetDialog(
                title = card.title,
                onConfirm = {
                    companion.forget(card)
                    forgetting = null
                },
                onDismiss = { forgetting = null },
            )
        }
        phone?.let { link -> PhoneDialog(url = link.url, onDismiss = { phone = null }) }
        Notice(message = message, onShown = companion::clearMessage, modifier = Modifier.align(Alignment.BottomEnd))
        PreparingScrim(companion.preparing.collectAsStateWithLifecycle().value)
    }
}

/** Обгортка, щоб «сервер не піднявся» (url = null) відрізнявся від «діалог закрито». */
private class PhoneLink(val url: String?)

/** Полиці Ліхтарика — те, що не залежить від запиту. */
internal data class Shelves(
    val history: List<ResumeCard>,
    val bookmarks: List<CatalogItem>,
    /** `null` — ще думає. */
    val advice: Advice?,
    /** Щоденні добірки профілю — ряд на кожну. */
    val personal: List<Advice> = emptyList(),
) {
    fun any(): Boolean = history.isNotEmpty() || bookmarks.isNotEmpty() || !advice?.items.isNullOrEmpty()
}

/** Що бачить телефон: те саме, що на екрані, у тому самому порядку. */
private fun phoneState(state: SearchUiState, shelves: Shelves, bookmarked: (CatalogItem) -> Boolean, res: Resources): PhoneState {
    val why = state.aiWhy + shelves.advice?.why.orEmpty() + shelves.personal.flatMap { it.why.entries }.associate { it.key to it.value }
    fun entry(item: CatalogItem) = PhoneEntry(
        id = item.id,
        title = item.title,
        note = why[item.id]?.takeIf { it.isNotBlank() }
            ?: listOf(res.getString(if (item.kind == TitleKind.TV) R.string.search_kind_series else R.string.search_kind_movie), item.year).filter { it.isNotBlank() }.joinToString(" · "),
        image = TmdbImages.posterThumb(item.posterPath),
        bookmarked = bookmarked(item),
        target = item,
    )
    val sections = if (state.query.isNotBlank()) {
        listOf(
            res.getString(R.string.search_section_best) to listOfNotNull(state.best?.let(::entry)),
            res.getString(R.string.search_section_ai_suggests) to state.aiRest.map(::entry),
            res.getString(R.string.search_section_shows) to state.showItems.map(::entry),
            res.getString(R.string.search_section_movies) to state.movies.map(::entry),
            res.getString(R.string.search_section_series) to state.series.map(::entry),
        )
    } else {
        listOf(
            res.getString(R.string.search_section_history) to shelves.history.map { card ->
                PhoneEntry(
                    id = card.id,
                    title = card.title,
                    note = cardNote(card, res),
                    image = card.continueItem?.posterPath?.let(TmdbImages::posterThumb) ?: card.imageUrl,
                    bookmarked = card.continueItem?.let { bookmarked(it.asCatalogItem()) },
                    target = card,
                )
            },
            res.getString(R.string.search_section_bookmarks) to shelves.bookmarks.map(::entry),
            *shelves.personal.map { it.title to it.items.map(::entry) }.toTypedArray(),
            res.getString(R.string.search_section_advice) to shelves.advice?.items.orEmpty().map(::entry),
        )
    }
    val thought = if (state.query.isNotBlank()) state.aiRefined else shelves.advice?.thought.orEmpty()
    return PhoneState(query = state.query, thought = thought, sections = sections)
}

/** Що під назвою в історії: серія, а далі — скільки лишилось чи що вже переглянуто. */
internal fun cardNote(card: ResumeCard, res: Resources): String {
    val item = card.continueItem ?: return card.subtitle
    val tail = if (item.finished) res.getString(R.string.search_watched) else item.leftMinutes?.let { res.getString(com.likhtar.home.core.catalog.R.string.catalog_minutes_left, it) }
    return listOfNotNull(card.subtitle.takeIf { it.isNotBlank() }, tail).joinToString(" · ")
}

@Composable
private fun PhoneDialog(url: String?, onDismiss: () -> Unit) {
    LikhtarDialog(onDismiss = onDismiss, width = 560.dp) {
        Text(text = stringResource(R.string.search_phone_title), style = LikhtarType.Label, color = LikhtarColors.TextPrimary)
        if (url == null) {
            Text(
                text = stringResource(R.string.search_phone_no_network),
                style = LikhtarType.Body2,
                color = LikhtarColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
        } else {
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QrCode(content = url, size = 168.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.search_phone_scan),
                        style = LikhtarType.Body2,
                        color = LikhtarColors.TextSecondary,
                    )
                    Text(
                        text = stringResource(R.string.search_phone_features),
                        style = LikhtarType.Body2,
                        color = LikhtarColors.TextSecondary,
                    )
                    Text(
                        text = stringResource(R.string.search_phone_same_network, url),
                        style = LikhtarType.Body2,
                        color = LikhtarColors.TextTertiary,
                    )
                }
            }
        }
        Chip(text = stringResource(R.string.search_done), onClick = onDismiss)
    }
}

@Composable
private fun ForgetDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    LikhtarDialog(onDismiss = onDismiss) {
        Text(text = stringResource(R.string.search_forget_title, title), style = LikhtarType.Label, color = LikhtarColors.TextPrimary)
        Text(
            text = stringResource(R.string.search_forget_text),
            style = LikhtarType.Body2,
            color = LikhtarColors.TextTertiary,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // «Скасувати» першим: незворотне не має стояти під пальцем за замовчуванням.
            Chip(text = stringResource(R.string.search_cancel), onClick = onDismiss)
            Chip(text = stringResource(R.string.search_remove), onClick = onConfirm)
        }
    }
}

@Composable
private fun Notice(message: String?, onShown: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            onShown()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(end = LikhtarDimens.ScreenPadding, bottom = 28.dp),
    ) {
        Text(
            text = "✓  " + message.orEmpty(),
            style = LikhtarType.Label,
            color = LikhtarColors.OnWarmWhite,
            modifier = Modifier
                .clip(RoundedCornerShape(LikhtarDimens.CornerLarge))
                .background(LikhtarColors.WarmWhite)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}


@Composable
internal fun Chip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, muted: Boolean = false) =
    LikhtarChip(text = text, onClick = onClick, modifier = modifier.padding(vertical = 6.dp), muted = muted)
