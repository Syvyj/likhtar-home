package com.likhtar.home.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.data.WeatherRepository
import com.likhtar.home.core.model.WeatherSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Тонкі ViewModel для екранів, що відкриваються з Головної.
 *
 * Раніше Погода та Історія створювали кожен ВЛАСНИЙ [HomeViewModel] через
 * `hiltViewModel()` — і піднімали весь combine-граф Головної (застосунки, смарт дім,
 * годинник…) заради одного поля. Кожен екран тепер тягне рівно своє джерело.
 */

@HiltViewModel
class WeatherDetailViewModel @Inject constructor(
    weatherRepository: WeatherRepository,
) : ViewModel() {
    val weather: StateFlow<WeatherSnapshot?> = weatherRepository.weather()
        .map<WeatherSnapshot, WeatherSnapshot?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
}

private const val STOP_TIMEOUT_MS = 5_000L
