package com.likhtar.home.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.Candidate
import com.likhtar.home.core.catalog.CatalogRepository
import com.likhtar.home.core.catalog.WatchMood
import com.likhtar.home.core.catalog.WatchFormat
import com.likhtar.home.core.catalog.WatchTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Радник «Що подивитись» у Ліхтарику: два питання й поради сервера (/api/advisor). */
data class AdvisorUiState(
    /** Нічого не вибрано наперед: порада — лише коли людина відповіла хоч на одне питання. */
    val format: WatchFormat? = null,
    val mood: WatchMood? = null,
    val picks: List<Candidate> = emptyList(),
    val loading: Boolean = false,
    /** Сервер не відповів — це не «нічого не знайшов». */
    val offline: Boolean = false,
)

/**
 * Радник живе на сторінці Ліхтарика поруч із пошуком (прототип): час і настрій —
 * пігулками під полем, поради — рядом і банером. Тут лише стан і запит; як це звучить —
 * вирішує екран.
 */
@HiltViewModel
class AdvisorViewModel @Inject constructor(
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val state = MutableStateFlow(AdvisorUiState())
    val uiState: StateFlow<AdvisorUiState> = state.asStateFlow()
    private var job: Job? = null
    private var round = 0

    fun selectFormat(format: WatchFormat) {
        if (format == state.value.format) return
        state.update { it.copy(format = format) }
        load()
    }

    fun selectMood(mood: WatchMood) {
        if (mood == state.value.mood) return
        state.update { it.copy(mood = mood) }
        load()
    }

    private fun load() {
        job?.cancel()
        state.update { it.copy(loading = true) }
        job = viewModelScope.launch {
            val current = state.value
            val result = runCatching { catalog.candidates(WatchTime.ANY, current.mood, round++, current.format) }
            state.update { it.copy(picks = result.getOrDefault(emptyList()), loading = false, offline = result.isFailure) }
        }
    }
}
