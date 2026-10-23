package com.likhtar.home.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.Advice
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.ContinuePlayback
import com.likhtar.home.core.catalog.ResumeCard
import com.likhtar.home.core.catalog.TitleSearch
import com.likhtar.home.core.catalog.WatchProgressRepository
import com.likhtar.home.core.catalog.WatchlistRepository
import com.likhtar.home.core.catalog.openIn
import com.likhtar.home.core.catalog.ResumeRows
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Те, що Ліхтарик знає про людину: історія, закладки й поради на їх основі.
 *
 * Окремо від [SearchViewModel]: пошук живе запитом, а це — полиці, які від запиту не
 * залежать і не мусять перераховуватись на кожну літеру.
 */
@HiltViewModel
class CompanionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchProgress: WatchProgressRepository,
    private val continuePlayback: ContinuePlayback,
    private val resumeRows: ResumeRows,
    private val apps: AppsRepository,
    private val watchlist: WatchlistRepository,
    private val catalog: TitleSearch,
    private val settings: SettingsRepository,
    val phone: PhoneRemote,
) : ViewModel() {

    /** Картка історії шукає джерело — PreparingScrim, як на інших екранах. */
    val preparing = continuePlayback.preparing

    private val cards = MutableStateFlow<List<ResumeCard>?>(null)

    /** `null` — ще вантажиться: екран чекає, щоб поставити фокус саме на історію, а не на закладки. */
    val history: StateFlow<List<ResumeCard>?> = cards.asStateFlow()

    val bookmarks: StateFlow<List<CatalogItem>> =
        watchlist.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val advised = MutableStateFlow<Advice?>(null)

    /** `null` — Ліхтарик ще думає; порожньо — нема з чого радити чи модель недоступна. */
    val advice: StateFlow<Advice?> = advised.asStateFlow()

    /** Кнопка «Голосом» — лише коли увімкнено в налаштуваннях. */
    val voiceOnTv: StateFlow<Boolean> =
        settings.settings.map { it.voiceSearchOnTv }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val personalShelves = MutableStateFlow<List<Advice>>(emptyList())

    /** Щоденні особисті добірки від Antigravity — показуються миттєво, без очікування моделі. */
    val personal: StateFlow<List<Advice>> = personalShelves.asStateFlow()

    private val notice = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = notice.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = settings.settings.first().activeProfileId
            personalShelves.value = runCatching { catalog.shelves(profile) }.getOrDefault(emptyList())
        }
        viewModelScope.launch {
            refresh()
            runCatching { watchlist.sync() }
            advise()
        }
    }

    private suspend fun refresh() {
        cards.value = resumeRows.history(HISTORY_MAX)
    }

    /**
     * Поради — за тим, що дивились і відклали. Список назв той самий, поки нічого нового,
     * тож сервер віддає з кешу, а не ганяє модель щоразу.
     */
    private suspend fun advise() {
        val like = (cards.value.orEmpty().mapNotNull { it.continueItem?.title } + watchlist.items.first().map { it.title })
            .distinct().take(ADVISE_FROM)
        if (like.isEmpty()) {
            advised.value = Advice()
            return
        }
        val known = cards.value.orEmpty().mapNotNull { it.continueItem?.tmdbId }.toSet() + watchlist.items.first().map { it.tmdbId }
        val advice = runCatching { catalog.advise(like) }.getOrDefault(Advice())
        advised.value = advice.copy(items = advice.items.filterNot { it.tmdbId in known })
    }

    /** Картка історії: наша — вмикаємо з місця зупинки, MEGOGO — у MEGOGO. `false` — не вийшло. */
    fun play(card: ResumeCard): Boolean {
        card.continueItem?.let { item ->
            viewModelScope.launch {
                if (!continuePlayback.play(item)) notice.value = context.getString(R.string.search_play_failed, item.title)
                refresh()
            }
            return true
        }
        return card.openIn(apps)
    }

    fun forget(card: ResumeCard) {
        val item = card.continueItem ?: return
        viewModelScope.launch {
            cards.value = cards.value.orEmpty().filterNot { it.continueItem?.id == item.id }
            val forgotten = watchProgress.forget(item)
            notice.value = if (forgotten) context.getString(R.string.search_history_removed, item.title) else context.getString(R.string.search_server_no_reply)
            if (!forgotten) refresh()
        }
    }

    fun toggleBookmark(item: CatalogItem) {
        viewModelScope.launch {
            val added = watchlist.toggle(item)
            notice.value = context.getString(if (added) R.string.search_bookmark_added else R.string.search_bookmark_removed, item.title)
        }
    }

    fun isBookmarked(item: CatalogItem): Boolean = bookmarks.value.any { it.id == item.id }

    fun say(text: String) {
        notice.value = text
    }

    fun clearMessage() {
        notice.value = null
    }

    private companion object {
        const val HISTORY_MAX = 30
        const val ADVISE_FROM = 12
    }
}
