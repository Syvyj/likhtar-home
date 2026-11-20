package com.likhtar.home.feature.cinema

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.CalendarEvent
import com.likhtar.home.core.catalog.TrackerRepository
import com.likhtar.home.core.catalog.byDay
import com.likhtar.home.core.catalog.window
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val tab: CalendarViewModel.Tab = CalendarViewModel.Tab.MINE,
    val today: String = "",
    val days: List<Pair<String, List<CalendarEvent>>> = emptyList(),
    val loading: Boolean = true,
)

/** Повний календар: 7 днів назад і 30 наперед; «Мої» — серіали й закладки, «Усі прем'єри» — TMDB. */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val tracker: TrackerRepository,
) : ViewModel() {
    enum class Tab { MINE, ALL }

    private val state = MutableStateFlow(CalendarUiState(today = today()))
    val uiState: StateFlow<CalendarUiState> = state.asStateFlow()

    init { load(Tab.MINE) }

    fun select(tab: Tab) {
        if (tab != state.value.tab) load(tab)
    }

    private fun load(tab: Tab) {
        state.update { it.copy(tab = tab, loading = true, days = emptyList()) }
        viewModelScope.launch {
            val (from, to) = window(state.value.today)
            val events = if (tab == Tab.MINE) tracker.events(from, to) else tracker.premieres(from, to)
            if (state.value.tab == tab) state.update { it.copy(days = byDay(events), loading = false) }
        }
    }

    private companion object {
        fun today(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }
}
