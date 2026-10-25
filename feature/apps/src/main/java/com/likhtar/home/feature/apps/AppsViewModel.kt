package com.likhtar.home.feature.apps

import com.likhtar.home.core.model.StreamingService
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.data.AppStateRepository
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.data.RecentHistoryRepository
import com.likhtar.home.core.data.ParentalControlRepository
import com.likhtar.home.core.model.AppCategory
import com.likhtar.home.core.model.LauncherApp
import com.likhtar.home.core.model.LauncherAppWithState
import com.likhtar.home.core.model.effectiveCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.likhtar.home.core.catalog.SectionRowsRepository
import com.likhtar.home.core.catalog.ServiceContinueRepository
import com.likhtar.home.core.catalog.TmdbImages

/** Дії довгого натискання на застосунку. */
enum class AppAction { LAUNCH, PIN_HOME, HIDE, LOCK, PIN_CINEMA, PIN_LIVE_TV, CATEGORY, FORCE_STOP, UNINSTALL }

/** Група сітки: заголовок і застосунки під ним. */
data class AppSection(
    val title: String,
    val apps: List<LauncherAppWithState>,
    /** Лічильник у заголовку: у «Усіх застосунках» — усі, а не лише перший ряд. */
    val count: Int = apps.size,
)

enum class PinPurpose {
    /** PIN ще не створено, а користувач хоче щось заблокувати. */
    CREATE,

    /** Зняти блокування із застосунку. */
    UNLOCK,

    /** Запустити заблокований застосунок. */
    LAUNCH,
}

data class PinRequest(
    val purpose: PinPurpose,
    val appId: String,
    val prompt: String,
    val error: String? = null,
)

/**
 * Шапка сфокусованого застосунку. [backdrop] — кадр: для відеосервісу це хіт, що зараз
 * на ньому, або те, що людина там дивиться; `null` — шапка з банера на кольорі застосунку.
 */
data class AppHero(
    val backdrop: String? = null,
    /** «Продовжити: …» або «Зараз на Netflix: …». */
    val note: String? = null,
)

data class AppsUiState(
    val apps: List<LauncherAppWithState> = emptyList(),
    val sections: List<AppSection> = emptyList(),
    /** Нещодавно відкриті — першим рядом. */
    val recent: List<LauncherAppWithState> = emptyList(),
    val hero: AppHero = AppHero(),
    val selectedId: String? = null,
    val actionsOpen: Boolean = false,
    val actionIndex: Int = 0,
    val pinRequest: PinRequest? = null,
    val message: String? = null,
) {
    val selected: LauncherAppWithState?
        get() = apps.firstOrNull { it.app.id == selectedId }
}

@HiltViewModel
class AppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appsRepository: AppsRepository,
    private val appStateRepository: AppStateRepository,
    private val parentalControlRepository: ParentalControlRepository,
    private val recentHistory: RecentHistoryRepository,
    private val sectionRows: SectionRowsRepository,
    private val serviceContinue: ServiceContinueRepository,
) : ViewModel() {

    private val interaction = MutableStateFlow(InteractionState())
    private val heroes = MutableStateFlow<Map<String, AppHero>>(emptyMap())
    private var heroJob: Job? = null

    /** Запам'ятовує, що саме треба заблокувати після створення PIN. */
    private var pinFirstEntry: String? = null

    val uiState: StateFlow<AppsUiState> =
        combine(appsRepository.apps, appStateRepository.states, interaction, recentHistory.apps, heroes) { apps, states, ui, recentIds, heroMap ->
            val entries = apps.map { LauncherAppWithState(it, states.stateOf(it.id)) }
            val byId = entries.associateBy { it.app.id }
            val selected = entries.firstOrNull { it.app.id == ui.selectedId }
            AppsUiState(
                apps = entries,
                sections = sectionsOf(entries),
                recent = recentIds.mapNotNull { byId[it] }.filterNot { it.state.hidden }.take(RECENT_MAX),
                hero = selected?.let { heroMap[it.app.packageName] } ?: AppHero(),
                selectedId = ui.selectedId,
                actionsOpen = ui.actionsOpen,
                actionIndex = ui.actionIndex,
                pinRequest = ui.pinRequest,
                message = ui.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppsUiState(),
        )

    /**
     * Розкладка сітки по групах.
     *
     * Приховані йдуть окремою групою в кінець, а не в свою категорію: інакше «приховати»
     * означало б «застосунок кудись подівся», і знайти його назад було б квестом.
     */
    private fun sectionsOf(entries: List<LauncherAppWithState>): List<AppSection> {
        val (hidden, visible) = entries.partition { it.state.hidden }
        val byCategory = visible.groupBy { it.effectiveCategory }

        return buildList {
            AppCategory.entries.forEach { category ->
                val apps = byCategory[category].orEmpty()
                if (apps.isNotEmpty()) add(AppSection(context.getString(category.titleRes), apps))
            }
            if (hidden.isNotEmpty()) add(AppSection(context.getString(R.string.apps_section_hidden), hidden))
        }
    }

    fun onAppFocused(appId: String) {
        interaction.value = interaction.value.copy(selectedId = appId)
        val pkg = appId.substringBefore('/')
        if (pkg in heroes.value) return
        heroJob?.cancel()
        // Пауза — щоб прогін стрілкою по ряду не смикав сервер на кожну плитку.
        heroJob = viewModelScope.launch {
            delay(HERO_DELAY_MS)
            val hero = heroFor(pkg) ?: return@launch
            heroes.value = heroes.value + (pkg to hero)
        }
    }

    /** Кадр для шапки: MEGOGO — те, що людина там дивиться; сервіс Кінозалу — його хіт. */
    private suspend fun heroFor(pkg: String): AppHero? {
        if (pkg in StreamingService.MEGOGO.packages) {
            val watching = runCatching { serviceContinue.megogo() }.getOrDefault(emptyList()).firstOrNull()
            if (watching != null) return AppHero(backdrop = watching.imageUrl, note = context.getString(R.string.apps_hero_continue, watching.title))
        }
        val brand = StreamingService.byPackage(pkg)?.key ?: return AppHero()
        val row = runCatching { sectionRows.cinemaBrandRows(brand) }.getOrDefault(emptyList()).firstOrNull()
        val hit = row?.items?.firstOrNull { it.backdropPath != null } ?: return AppHero()
        return AppHero(backdrop = TmdbImages.backdrop(hit.backdropPath), note = "${row.title}: ${hit.title}")
    }

    fun openActions(appId: String) {
        interaction.value = interaction.value.copy(
            selectedId = appId,
            actionsOpen = true,
            actionIndex = 0,
            message = null,
        )
    }

    fun closeActions() {
        interaction.value = interaction.value.copy(actionsOpen = false)
    }

    /**
     * Клавіші при відкритому меню дій. Меню керується станом, а не фокусом: фокус
     * лишається на плитці сітки, тому «протекти» вниз йому нікуди, а після закриття
     * нічого не треба відновлювати — та сама схема, що в панелях каталогів.
     */
    fun moveActions(key: String): Boolean {
        val ui = interaction.value
        if (!ui.actionsOpen) return false
        when (key) {
            "up" -> interaction.value = ui.copy(actionIndex = (ui.actionIndex - 1).coerceAtLeast(0))
            "down" -> interaction.value =
                ui.copy(actionIndex = (ui.actionIndex + 1).coerceAtMost(ACTIONS.lastIndex))
            "enter" -> ACTIONS.getOrNull(ui.actionIndex)?.let(::perform)
            "back", "left" -> closeActions()
            // «→» мовчить навмисно: інакше він пішов би в сітку під меню.
        }
        return true
    }

    /** Enter на картці: заблокований застосунок спершу питає PIN. */
    fun launch(entry: LauncherAppWithState) {
        if (entry.state.locked) {
            requestPin(PinPurpose.LAUNCH, entry.app.id, context.getString(R.string.apps_pin_prompt_launch, entry.app.label))
        } else {
            startApp(entry.app)
        }
    }

    fun perform(action: AppAction) {
        val entry = uiState.value.selected ?: return
        val id = entry.app.id
        viewModelScope.launch {
            when (action) {
                AppAction.LAUNCH -> {
                    closeActions()
                    launch(entry)
                }

                // Видалення робить система: свого діалогу тут не малюємо, бо підтвердження
                // видалення — її обов'язок і її ж вигляд у всіх застосунках.
                AppAction.UNINSTALL -> {
                    closeActions()
                    if (!appsRepository.requestUninstall(entry.app.packageName)) {
                        message(context.getString(R.string.apps_msg_uninstall_failed))
                    }
                }

                AppAction.FORCE_STOP -> {
                    closeActions()
                    if (!appsRepository.openAppInfo(entry.app.packageName)) {
                        message(context.getString(R.string.apps_msg_app_info_failed))
                    }
                }

                AppAction.HIDE -> {
                    appStateRepository.toggleHidden(id)
                    message(if (entry.state.hidden) context.getString(R.string.apps_msg_unhidden) else context.getString(R.string.apps_msg_hidden))
                }

                AppAction.LOCK -> when {
                    entry.state.locked ->
                        requestPin(PinPurpose.UNLOCK, id, context.getString(R.string.apps_pin_prompt_unlock))

                    !parentalControlRepository.isPinSetOnce() ->
                        requestPin(PinPurpose.CREATE, id, context.getString(R.string.apps_pin_prompt_create))

                    else -> {
                        appStateRepository.toggleLocked(id)
                        message(context.getString(R.string.apps_msg_locked))
                    }
                }

                AppAction.PIN_HOME -> {
                    appStateRepository.togglePinnedToHome(id)
                    message(
                        if (entry.state.pinnedToHome) {
                            context.getString(R.string.apps_msg_unpinned_home)
                        } else {
                            context.getString(R.string.apps_msg_pinned_home)
                        },
                    )
                }

                AppAction.PIN_CINEMA -> {
                    appStateRepository.togglePinnedToCinema(id)
                    message(
                        if (entry.state.pinnedToCinema) {
                            context.getString(R.string.apps_msg_removed_cinema)
                        } else {
                            context.getString(R.string.apps_msg_added_cinema)
                        },
                    )
                }

                // Одна кнопка, що перебирає категорії по колу: чотири окремі кнопки на панелі
                // дій — це чотири зайві ↓ на пульті заради дії, яку роблять раз на застосунок.
                AppAction.CATEGORY -> {
                    val next = AppCategory.entries[
                        (entry.effectiveCategory.ordinal + 1) % AppCategory.entries.size,
                    ]
                    appStateRepository.setCategory(id, next)
                    message(context.getString(R.string.apps_category, context.getString(next.titleRes)))
                }

                AppAction.PIN_LIVE_TV -> {
                    appStateRepository.togglePinnedToLiveTv(id)
                    message(
                        if (entry.state.pinnedToLiveTv) {
                            context.getString(R.string.apps_msg_removed_live_tv)
                        } else {
                            context.getString(R.string.apps_msg_added_live_tv)
                        },
                    )
                }
            }
        }
    }

    fun submitPin(pin: String) {
        val request = interaction.value.pinRequest ?: return
        viewModelScope.launch {
            when (request.purpose) {
                PinPurpose.CREATE -> handlePinCreation(request, pin)

                PinPurpose.UNLOCK -> if (parentalControlRepository.verifyPin(pin)) {
                    appStateRepository.toggleLocked(request.appId)
                    dismissPin()
                    message(context.getString(R.string.apps_msg_unlocked))
                } else {
                    pinError(request, context.getString(R.string.apps_pin_wrong))
                }

                PinPurpose.LAUNCH -> if (parentalControlRepository.verifyPin(pin)) {
                    val app = uiState.value.apps.firstOrNull { it.app.id == request.appId }?.app
                    dismissPin()
                    app?.let(::startApp)
                } else {
                    pinError(request, context.getString(R.string.apps_pin_wrong))
                }
            }
        }
    }

    fun dismissPin() {
        pinFirstEntry = null
        interaction.value = interaction.value.copy(pinRequest = null)
    }

    private suspend fun handlePinCreation(request: PinRequest, pin: String) {
        val first = pinFirstEntry
        if (first == null) {
            pinFirstEntry = pin
            interaction.value = interaction.value.copy(
                pinRequest = request.copy(prompt = context.getString(R.string.apps_pin_prompt_repeat), error = null),
            )
            return
        }
        if (first != pin) {
            pinFirstEntry = null
            pinError(request.copy(prompt = context.getString(R.string.apps_pin_prompt_create)), context.getString(R.string.apps_pin_mismatch))
            return
        }
        parentalControlRepository.setPin(pin)
        appStateRepository.toggleLocked(request.appId)
        dismissPin()
        message(context.getString(R.string.apps_msg_pin_created))
    }

    private fun requestPin(purpose: PinPurpose, appId: String, prompt: String) {
        pinFirstEntry = null
        interaction.value = interaction.value.copy(
            pinRequest = PinRequest(purpose, appId, prompt),
            message = null,
        )
    }

    private fun pinError(request: PinRequest, error: String) {
        interaction.value = interaction.value.copy(pinRequest = request.copy(error = error))
    }

    private fun startApp(app: LauncherApp) {
        viewModelScope.launch { recentHistory.rememberApp(app.id) }
        if (!appsRepository.launch(app)) {
            message(context.getString(R.string.apps_msg_launch_failed, app.label))
        }
    }

    private fun message(text: String) {
        interaction.value = interaction.value.copy(message = text)
    }

    private suspend fun ParentalControlRepository.isPinSetOnce(): Boolean = isPinSet.first()

    private data class InteractionState(
        val selectedId: String? = null,
        val actionsOpen: Boolean = false,
        val actionIndex: Int = 0,
        val pinRequest: PinRequest? = null,
        val message: String? = null,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val RECENT_MAX = 10
        private const val HERO_DELAY_MS = 350L
        /** Порядок дій у меню — єдине джерело і для рендера, і для Enter за індексом. */
        val ACTIONS = listOf(
            AppAction.LAUNCH,
            AppAction.PIN_HOME,
            AppAction.PIN_CINEMA,
            AppAction.PIN_LIVE_TV,
            AppAction.HIDE,
            AppAction.LOCK,
            AppAction.CATEGORY,
            AppAction.FORCE_STOP,
            AppAction.UNINSTALL,
        )
    }
}
