package com.likhtar.home.feature.cinema

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.CatalogNetwork
import com.likhtar.home.core.catalog.CatalogRepository
import com.likhtar.home.core.catalog.CatalogStudio
import com.likhtar.home.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StreamingsUiState(
    val loading: Boolean = true,
    val studios: List<CatalogStudio> = emptyList(),
    val networks: List<CatalogNetwork> = emptyList(),
    /**
     * Регіони, за якими лишаємо телеканали.
     *
     * Це «Доступність для» ПЛЮС країна локалі приставки, а не одне з двох. «Доступність
     * для» відповідає на інше питання — де дивитись стрімінги, — і в людини за польським
     * VPN там стоїть PL. Якби відсів ішов лише за нею, зникли б українські канали, тобто
     * рівно ті, по які ця сторінка й потрібна. Локаль (`uk-UA` на приставці) додає той
     * регіон, у якому людина насправді живе, і жодного списку країн у коді для цього не
     * треба.
     */
    val regions: Set<String> = emptySet(),
    val message: String? = null,
)

/**
 * Перелік усіх сервісів.
 *
 * Своя модель, а не CinemaViewModel: та на вході тягне слайдер, «продовжити», радника
 * й усі добірки — для сторінки з плитками це задорого. Тут два запити, і обидва
 * кешовані в репозиторії, тож повторний захід нічого не коштує.
 */
@HiltViewModel
class StreamingsViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val state = MutableStateFlow(StreamingsUiState())
    val uiState: StateFlow<StreamingsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val (curation, networks) = coroutineScope {
                val curation = async { catalog.curation() }
                val networks = async { catalog.networks() }
                curation.await() to networks.await()
            }
            state.update {
                it.copy(
                    loading = false,
                    studios = curation.studios,
                    networks = networks,
                    regions = settings.settings.first().availabilityCountries
                        .map { code -> code.uppercase() }
                        .plus(java.util.Locale.getDefault().country.uppercase())
                        .filter { code -> code.isNotBlank() }
                        .toSet(),
                    message = curation.message,
                )
            }
        }
    }
}
