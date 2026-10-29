package com.likhtar.home.navigation

import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.catalog.PlaybackController
import com.likhtar.home.core.data.HomeLauncherRepository
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.wallpaper.WallpaperRepository
import com.likhtar.home.core.wallpaper.WallpaperState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Тримає розділ, з якого стартує лаунчер («Поведінка запуску» в налаштуваннях).
 *
 * `null` означає «налаштування ще читаються з диска»: NavHost не можна створювати з
 * тимчасовим startDestination, бо він фіксується назавжди при першій композиції.
 */
@HiltViewModel
class LikhtarAppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    wallpaperRepository: WallpaperRepository,
    val homeLauncherRepository: HomeLauncherRepository,
    /** Прохання відтворити приходять сюди — запустити їх може лише Activity. */
    val playback: PlaybackController,
) : ViewModel() {

    /** Плеєр закрився: віддаємо таймкод тому, хто вміє його записати. */
    fun onPlayerClosed(positionMs: Long, durationMs: Long, endedUri: String? = null) {
        viewModelScope.launch { playback.onPlayerClosed(positionMs, durationMs, endedUri) }
    }

    /**
     * Чи пропонувати зробити лаунчер домашнім екраном.
     *
     * Питаємо один раз і тільки якщо кнопка HOME справді не наша: користувач, який
     * встановив лаунчер уже дефолтним, не має бачити діалог узагалі. Прапорець
     * зберігається на «показали», а не на «погодився» — інакше відмова оберталася б
     * питанням при кожному старті.
     */
    val homeOnboarding: StateFlow<Boolean> = settingsRepository.settings
        .map { it.firstRunDone && !it.homeOnboardingShown && !homeLauncherRepository.status().isDefault }
        // status() ходить у PackageManager (queryIntentActivities) — це не для main.
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    /** Майстер першого запуску — перед пропозицією забрати HOME. */
    val firstRun: StateFlow<Boolean> = settingsRepository.settings
        .map { !it.firstRunDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    fun finishFirstRun(language: String, country: String, onlyMyApps: Boolean) {
        viewModelScope.launch {
            settingsRepository.setContentLocale(language, country)
            settingsRepository.setOnlyMyApps(onlyMyApps)
            settingsRepository.setFirstRunDone()
        }
    }

    fun skipFirstRun() {
        viewModelScope.launch { settingsRepository.setFirstRunDone() }
    }

    fun dismissHomeOnboarding() {
        viewModelScope.launch { settingsRepository.setHomeOnboardingShown() }
    }

    /**
     * Шпалери живуть тут, а не в конкретному екрані: фон спільний для всіх розділів,
     * і ротація не має перезапускатися при кожному переході.
     */
    val wallpaper: StateFlow<WallpaperState> = wallpaperRepository.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = WallpaperState(),
        )

    val startSection: StateFlow<LikhtarSection?> = settingsRepository.settings
        .map { it.startSection }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    val titleUnderLogo: StateFlow<Boolean> = settingsRepository.settings
        .map { it.titleUnderLogo }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 0 означає «ніколи»: користувач може лишити плитки на екрані назавжди. */
    val ambientTimeoutSeconds: StateFlow<Int> = settingsRepository.settings
        .map { it.ambientTimeoutSeconds }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0,
        )

    /**
     * Розділи, видимі в меню та на Головній. «Смарт дім» можна вимкнути в налаштуваннях —
     * тоді він зникає з інтерфейсу повністю, а не просто показує заглушку. «Ігри» — навпаки,
     * з'являються лише після ввімкнення.
     */
    val visibleSections: StateFlow<List<LikhtarSection>> = settingsRepository.settings
        .map { settings ->
            LikhtarSection.ordered(
                LikhtarSection.entries.filter {
                    (it != LikhtarSection.SMART_HOME || settings.smartHomeEnabled) &&
                        (it != LikhtarSection.GAMES || settings.gamesEnabled)
                },
                settings.sectionOrder,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LikhtarSection.entries - LikhtarSection.GAMES,
        )
}
