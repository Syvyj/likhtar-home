package com.likhtar.home.feature.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.data.AppLanguage
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.data.HomeLauncherRepository
import com.likhtar.home.core.data.LikhtarSettings
import com.likhtar.home.core.data.ParentalControlRepository
import com.likhtar.home.core.data.RecentChannel
import com.likhtar.home.core.data.RecentHistoryRepository
import com.likhtar.home.core.data.GeoPlace
import com.likhtar.home.core.data.GeocodingRepository
import com.likhtar.home.core.catalog.Profile
import com.likhtar.home.core.catalog.ProfileRepository
import com.likhtar.home.core.catalog.ServiceContinueRepository
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.data.LanDiscovery
import com.likhtar.home.core.data.TitleActions
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.smarthome.SmartHomeRepository
import com.likhtar.home.core.smarthome.SmartHomeStatus
import com.likhtar.home.core.tv.TvCountry
import com.likhtar.home.core.tv.TvGroupsRepository
import com.likhtar.home.core.tv.TvGuideRepository
import com.likhtar.home.core.wallpaper.WallpaperRepository
import com.likhtar.home.core.wallpaper.WallpaperTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Розділи налаштувань згруповані за **екраном, на який впливають**, а не за джерелом
 * даних.
 *
 * Було дванадцять плоских пунктів, і керування однією лише Головною лежало в семи з них:
 * «Домашній екран», «Головний екран» і «Вигляд» читались як синоніми, погода мала два
 * різні перемикачі в різних розділах, а «…на Головній» стояло ще й у Кінозалі та
 * Телебаченні. Тепер: один екран — один розділ.
 */
enum class SettingsSection(@StringRes val title: Int, @StringRes val summary: Int?) {
    HOME(R.string.settings_section_home, R.string.settings_section_home_summary),
    WATCH(R.string.settings_section_watch, R.string.settings_section_watch_summary),
    TV(R.string.settings_section_tv, R.string.settings_section_tv_summary),
    PROFILES(R.string.settings_section_profiles, R.string.settings_section_profiles_summary),
    STARTUP(R.string.settings_section_startup, R.string.settings_section_startup_summary),
    CONNECTIONS(R.string.settings_section_connections, R.string.settings_section_connections_summary),
    ABOUT(R.string.settings_section_about, R.string.settings_section_about_summary),
}

data class SettingsUiState(
    /** Чи є сесія MEGOGO у сховищі WebView цієї приставки. */
    val megogoLinked: Boolean = false,
    /** Що сервіс відповів на цю сесію — перевірка словом «Підключено» не є. */
    val megogoStatus: String = "",
    val settings: LikhtarSettings = LikhtarSettings(),
    val section: SettingsSection = SettingsSection.HOME,
    val pinSet: Boolean = false,
    val pinPrompt: String? = null,
    val pinError: String? = null,
    val message: String? = null,
    /** Пари «id → назва» доступних джерел шпалер. */
    val wallpaperProviders: List<Pair<String, String>> = emptyList(),
    val smartHomeStatus: SmartHomeStatus = SmartHomeStatus.NotConfigured,
    /** Відкрите поле введення тексту; `null` — діалогу немає. */
    val textPrompt: TextPrompt? = null,
    /** Яке поле редагували востаннє — щоб повернути на його рядок фокус після діалогу. */
    val lastEditedField: TextField? = null,
    /** Країни каталогу iptv-org; порожній список означає «ще не завантажилися». */
    val tvCountries: List<TvCountry> = emptyList(),
    /** Фільтр списку країн: 250 країн пультом інакше не пройти. */
    val tvCountryFilter: String = "",
    /** Знайдені міста після пошуку погоди; порожньо — пошуку ще не було. */
    val weatherPlaces: List<GeoPlace> = emptyList(),
    /** Історія каналів — саме з неї обираються закріплені для Головної. */
    val recentChannels: List<RecentChannel> = emptyList(),
)

/** Що саме зараз редагується в діалозі введення. */
enum class TextField { HA_URL, HA_TOKEN, TV_EPG_URL, TV_PLAYLIST_URL, TV_COUNTRY_FILTER, WEATHER_CITY, TMDB_KEY, LIKHTAR_URL, PROFILE_NAME, FLAVOR }

data class TextPrompt(
    val field: TextField,
    val title: String,
    val description: String,
    val placeholder: String,
    val initialValue: String,
    val allowClear: Boolean,
    /** Для [TextField.FLAVOR]: зберігає значення й повертає повідомлення про результат. */
    val onSubmit: (suspend (String) -> String)? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val appUpdate: com.likhtar.home.core.data.AppUpdate,
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val lan: LanDiscovery,
    private val profileRepository: ProfileRepository,
    private val parentalControlRepository: ParentalControlRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val smartHomeRepository: SmartHomeRepository,
    private val tvGuideRepository: TvGuideRepository,
    private val tvGroupsRepository: TvGroupsRepository,
    private val geocodingRepository: GeocodingRepository,
    /** Тонкий адаптер над PackageManager, стану не тримає — тому віддається екрану як є. */
    val homeLauncherRepository: HomeLauncherRepository,
    private val appsRepository: AppsRepository,
    private val recentHistory: RecentHistoryRepository,
    private val serviceContinue: ServiceContinueRepository,
    private val remoteKeys: com.likhtar.home.core.data.RemoteKeysRepository,
    private val keyCatcher: com.likhtar.home.core.data.RemoteKeyCatcher,
    /** Медіатека: джерела й скан — групі налаштувань як є, стан тримає сама. */
    val media: com.likhtar.home.core.catalog.media.MediaLibrary,
    /** «З телефона»: форма адрес, ключів і паролів у браузері телефона. */
    val phoneSetup: SettingsPhoneLink,
) : ViewModel() {

    // ── Кнопки пульта ───────────────────────────────────────────────────
    val keyBindings: StateFlow<List<com.likhtar.home.core.data.KeyBinding>> =
        remoteKeys.bindings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Застосунки для дії кнопки — ті самі, що в «Застосунках». */
    val launchableApps: StateFlow<List<com.likhtar.home.core.model.LauncherApp>> =
        appsRepository.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Наступна кнопка пульта — сюди, а не на виконання. */
    val caughtKeys: kotlinx.coroutines.flow.SharedFlow<Int> = keyCatcher.caught

    fun startCatchingKey() = keyCatcher.start()

    fun stopCatchingKey() = keyCatcher.stop()

    fun setKeyAction(keyCode: Int, press: com.likhtar.home.core.data.KeyPress, action: com.likhtar.home.core.data.KeyAction?) {
        viewModelScope.launch { remoteKeys.set(keyCode, press, action) }
    }

    override fun onCleared() {
        keyCatcher.stop()
        super.onCleared()
    }

    /**
     * Текст мовою інтерфейсу. Контекст застосунку обгорнуто мовою ще на старті процесу, а
     * мову міняють без перезапуску — тому обгортаємо щоразу заново (рідко: лише повідомлення).
     */
    private fun str(@StringRes id: Int, vararg args: Any): String =
        AppLanguage.wrap(appContext).getString(id, *args)

    /** Мова інтерфейсу змінилась — мова контенту йде за нею, країна лишається. */
    /** [then] — після запису: далі процес перезапускається, і незаписане загубилось би. */
    fun setContentLanguage(language: String, then: () -> Unit = {}) {
        viewModelScope.launch {
            val current = settingsRepository.settings.first()
            settingsRepository.setContentLocale(language, current.contentCountry)
            then()
        }
    }

    fun setContentCountry(country: String) {
        viewModelScope.launch {
            settingsRepository.setContentLocale(settingsRepository.settings.first().contentLanguage, country)
        }
    }

    fun toggleTrailerOnUp() {
        viewModelScope.launch {
            settingsRepository.setTitleTrailerOnUp(!settingsRepository.settings.first().titleTrailerOnUp)
        }
    }

    fun toggleTitleLiveStills() {
        viewModelScope.launch {
            settingsRepository.setTitleLiveStills(!settingsRepository.settings.first().titleLiveStills)
        }
    }



    /**
     * Стан акаунта MEGOGO — не «пароль збережено», а відповідь сервісу на нашу сесію.
     *
     * Питаємо щоразу при відкритті екрана: сесія живе у файлі на сервері й злітає від
     * виходу з акаунта в самому MEGOGO, а рядок, який показує «Підключено» після того, —
     * гірший за відсутній.
     */
    fun refreshMegogo() {
        viewModelScope.launch {
            megogoState.value = serviceContinue.megogoState()
        }
    }

    private val weatherPlaces = MutableStateFlow<List<GeoPlace>>(emptyList())
    private val tvCountries = MutableStateFlow<List<TvCountry>>(emptyList())
    private val tvCountryFilter = MutableStateFlow("")

    init {
        // Список країн — 18 КБ і кешується на диск, тож тягнемо його одразу: інакше розділ
        // «Телебачення» відкривався б порожнім і виглядав би зламаним.
        //
        // Тут же поруч стояв запит preview-каналів YouTube. Він живив блок «КАНАЛИ
        // YOUTUBE», а той керував `feedYouTubeEnabled` — прапорцем під
        // `INCLUDE_NAVIGATION_BANNERS = false`. Тобто запит до TvProvider робився на
        // кожне відкриття налаштувань заради перемикачів, які нічого не вмикали.
        viewModelScope.launch { tvCountries.value = tvGuideRepository.countries() }
    }

    private val section = MutableStateFlow(
        com.likhtar.home.core.model.SettingsTarget.take()
            ?.let { name -> SettingsSection.entries.firstOrNull { it.name == name } } ?: SettingsSection.HOME,
    )
    private val pinPrompt = MutableStateFlow<String?>(null)
    private val pinError = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val textPrompt = MutableStateFlow<TextPrompt?>(null)
    private val lastEditedField = MutableStateFlow<TextField?>(null)

    /**
     * Профілі окремим потоком, а не в `uiState`.
     *
     * `combine` там уже на п'яти джерелах — межа типізованої перевантаженої версії, — і
     * шостим довелося б городити ще один рівень вкладення заради списку, який оновлюється
     * вручну й ні з чим не мусить синхронізуватись.
     */
    /**
     * Стан акаунта MEGOGO окремим потоком.
     *
     * У `combine` вище вже п'ять джерел — межа типізованої версії, — а цей стан і не
     * мусить із ними синхронізуватись: він оновлюється, коли екран про нього спитає.
     */
    private val megogoState = MutableStateFlow(false to str(R.string.settings_not_linked))

    private val profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profileList: StateFlow<List<Profile>> = profiles.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        parentalControlRepository.isPinSet,
        section,
        combine(pinPrompt, pinError, message, textPrompt, lastEditedField, ::Overlays),
        combine(
            smartHomeRepository.state,
            tvCountries,
            tvCountryFilter,
            weatherPlaces,
            // Шосте джерело не влазить у типізований combine, тому їде разом із каналами
            // однією парою: заводити третій рівень вкладення заради двох полів — гірше.
            combine(recentHistory.channels, megogoState) { channels, account -> channels to account },
            ::Sources,
        ),
    ) { settings, pinSet, currentSection, overlays, sources ->
        val (prompt, error, text, input, edited) = overlays
        SettingsUiState(
            settings = settings,
            section = currentSection,
            pinSet = pinSet,
            pinPrompt = prompt,
            pinError = error,
            message = text,
            wallpaperProviders = wallpaperRepository.providerNames(),
            smartHomeStatus = sources.smartHome.status,
            textPrompt = input,
            lastEditedField = edited,
            tvCountries = sources.countries,
            tvCountryFilter = sources.countryFilter,
            weatherPlaces = sources.weatherPlaces,
            recentChannels = sources.recentChannels,
            megogoLinked = sources.megogoLinked,
            megogoStatus = sources.megogoStatus,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    /** Повідомлення внизу екрана — для того, що сталося не в налаштуваннях. */
    fun say(text: String) {
        message.value = text
    }

    fun selectSection(next: SettingsSection) {
        section.value = next
        message.value = null
    }

    fun toggleShowWithoutUaAudio() {
        viewModelScope.launch {
            val show = !settingsRepository.settings.first().showWithoutUaAudio
            settingsRepository.setShowWithoutUaAudio(show)
            message.value = str(if (show) R.string.settings_msg_without_ua_on else R.string.settings_msg_without_ua_off)
        }
    }

    fun toggleOnlyMyApps() {
        viewModelScope.launch {
            val only = !settingsRepository.settings.first().onlyMyApps
            settingsRepository.setOnlyMyApps(only)
            message.value = str(if (only) R.string.settings_msg_only_my_apps_on else R.string.settings_msg_only_my_apps_off)
        }
    }

    // ── «З телефона» ────────────────────────────────────────────────────
    // Вікно QR відкривають і рядок угорі, і група AI — тому стан тут, а не в екрані.
    private val _phoneUrl = MutableStateFlow<String?>(null)
    val phoneUrl: StateFlow<String?> = _phoneUrl.asStateFlow()
    private val _phoneOpen = MutableStateFlow(false)
    val phoneOpen: StateFlow<Boolean> = _phoneOpen.asStateFlow()

    fun openPhone() {
        _phoneUrl.value = phoneSetup.start()
        _phoneOpen.value = true
    }

    fun closePhone() {
        _phoneOpen.value = false
    }

    /** Відключити AI: ключ стирається з приставки. */
    fun disconnectAi() {
        viewModelScope.launch { settingsRepository.setAi("", "", "", "") }
    }

    fun toggleDockSection(route: String) {
        viewModelScope.launch { settingsRepository.toggleDockSection(route) }
    }

    fun toggleHiddenService(key: String) {
        viewModelScope.launch { settingsRepository.toggleHiddenService(key) }
    }

    fun toggleShowAnime() {
        viewModelScope.launch {
            val show = !settingsRepository.settings.first().showAnime
            settingsRepository.setShowAnime(show)
            message.value = str(if (show) R.string.settings_msg_anime_on else R.string.settings_msg_anime_off)
        }
    }

    fun toggleHideWatched() {
        viewModelScope.launch {
            val hide = !settingsRepository.settings.first().hideWatched
            settingsRepository.setHideWatched(hide)
            message.value = str(if (hide) R.string.settings_msg_hide_watched_on else R.string.settings_msg_hide_watched_off)
        }
    }

    fun toggleWeather() {
        viewModelScope.launch {
            // Не uiState: до першої емісії combine там лежить дефолт, і перший клік «перемикав» його.
            val show = !settingsRepository.settings.first().showWeather
            settingsRepository.setShowWeather(show)
            message.value = str(if (show) R.string.settings_msg_weather_on else R.string.settings_msg_weather_off)
        }
    }


    fun selectWallpaperProvider(providerId: String) {
        viewModelScope.launch {
            settingsRepository.setWallpaperProvider(providerId)
            val name = uiState.value.wallpaperProviders.firstOrNull { it.first == providerId }?.second
            message.value = str(R.string.settings_msg_wallpaper_source, name ?: providerId)
        }
    }

    /** Інтервал і затемнення перемикаються по колу — на пульті це одна кнопка замість слайдера. */
    fun cycleWallpaperInterval() {
        viewModelScope.launch {
            val current = settingsRepository.settings.first().wallpaperIntervalMinutes
            val next = INTERVALS[(INTERVALS.indexOf(current).takeIf { it >= 0 }?.plus(1) ?: 0) % INTERVALS.size]
            settingsRepository.setWallpaperIntervalMinutes(next)
            message.value = str(R.string.settings_msg_wallpaper_interval, next)
        }
    }

    /** Тема діє на джерело «Теми»; «Мікс» бере всі теми разом і перемішує. */
    fun selectWallpaperTheme(theme: WallpaperTheme) {
        viewModelScope.launch {
            settingsRepository.setWallpaperTheme(theme.id)
            message.value = str(R.string.settings_msg_wallpaper_theme, theme.title)
        }
    }

    fun cycleWallpaperDim() {
        viewModelScope.launch {
            val current = settingsRepository.settings.first().wallpaperDimPercent
            val next = DIM_LEVELS[(DIM_LEVELS.indexOf(current).takeIf { it >= 0 }?.plus(1) ?: 0) % DIM_LEVELS.size]
            settingsRepository.setWallpaperDimPercent(next)
            message.value = str(R.string.settings_msg_wallpaper_dim, next)
        }
    }

    fun toggleWallpaperAttribution() {
        viewModelScope.launch {
            val show = !settingsRepository.settings.first().showWallpaperAttribution
            settingsRepository.setWallpaperAttribution(show)
            message.value = str(if (show) R.string.settings_msg_attribution_on else R.string.settings_msg_attribution_off)
        }
    }

    /**
     * Вимкнення прибирає «Смарт дім» із меню та з Головної повністю — це і є той пункт,
     * який дозволяє користувачу не бачити розділ, якщо він йому не потрібен.
     */
    fun toggleSmartHome() {
        viewModelScope.launch {
            val enabled = !settingsRepository.settings.first().smartHomeEnabled
            settingsRepository.setSmartHomeEnabled(enabled)
            message.value = str(if (enabled) R.string.settings_msg_smart_home_on else R.string.settings_msg_smart_home_off)
        }
    }

    fun toggleVoiceCommands() {
        viewModelScope.launch {
            val enabled = !settingsRepository.settings.first().voiceCommandsEnabled
            settingsRepository.setVoiceCommandsEnabled(enabled)
            message.value = str(if (enabled) R.string.settings_msg_voice_on else R.string.settings_msg_voice_off)
        }
    }

    fun cycleSmartHomeRefresh() {
        viewModelScope.launch {
            val current = settingsRepository.settings.first().smartHomeRefreshSeconds
            val next = REFRESH_INTERVALS[
                (REFRESH_INTERVALS.indexOf(current).takeIf { it >= 0 }?.plus(1) ?: 0) % REFRESH_INTERVALS.size,
            ]
            settingsRepository.setSmartHomeRefreshSeconds(next)
            message.value = str(R.string.settings_msg_smart_home_refresh, next)
        }
    }

    fun refreshProfiles() {
        viewModelScope.launch { profiles.value = profileRepository.all() }
    }

    fun selectProfile(id: String) {
        viewModelScope.launch {
            profileRepository.setActive(id)
            message.value = str(R.string.settings_msg_profile, profiles.value.firstOrNull { it.id == id }?.name ?: id)
        }
    }

    fun addProfile() {
        lastEditedField.value = TextField.PROFILE_NAME
        textPrompt.value = TextPrompt(
            field = TextField.PROFILE_NAME,
            title = str(R.string.settings_prompt_profile_title),
            description = str(R.string.settings_prompt_profile_desc),
            placeholder = str(R.string.settings_prompt_profile_placeholder),
            initialValue = "",
            allowClear = false,
        )
    }

    /** Діалог введення для групи з джерел flavor-а: зберігає її [TextPrompt.onSubmit]. */
    fun openPrompt(prompt: TextPrompt) {
        lastEditedField.value = prompt.field
        textPrompt.value = prompt
    }

    /** Той самий обхід, але для нашого сервера: кандидат мусить віддати `/api/profiles`. */
    fun findServer() {
        viewModelScope.launch {
            message.value = str(R.string.settings_msg_searching_server)
            val found = lan.scanServer()
            when {
                found.isEmpty() -> message.value =
                    str(R.string.settings_msg_server_not_found)

                else -> {
                    settingsRepository.setLikhtarBaseUrl(found.first())
                    message.value = str(R.string.settings_found, found.first())
                }
            }
        }
    }

    fun editSmartHomeUrl() {
        lastEditedField.value = TextField.HA_URL
        textPrompt.value = TextPrompt(
            field = TextField.HA_URL,
            title = str(R.string.settings_prompt_ha_url_title),
            description = str(R.string.settings_prompt_ha_url_desc),
            placeholder = "http://homeassistant.local:8123",
            initialValue = uiState.value.settings.smartHomeUrl,
            allowClear = uiState.value.settings.smartHomeUrl.isNotBlank(),
        )
    }

    fun editSmartHomeToken() {
        lastEditedField.value = TextField.HA_TOKEN
        textPrompt.value = TextPrompt(
            field = TextField.HA_TOKEN,
            title = str(R.string.settings_prompt_ha_token_title),
            description = str(R.string.settings_prompt_ha_token_desc),
            placeholder = "eyJhbGciOi…",
            // Наявне значення не підставляємо: показувати секрет на екрані телевізора не треба,
            // а редагувати токен посимвольно однаково ніхто не буде — його вставляють цілком.
            initialValue = "",
            allowClear = uiState.value.settings.smartHomeToken.isNotBlank(),
        )
    }

    fun submitTextPrompt(value: String) {
        val prompt = textPrompt.value ?: return
        textPrompt.value = null
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            when (prompt.field) {
                TextField.PROFILE_NAME -> {
                    val created = profileRepository.create(value, kids = false)
                    if (created == null) {
                        message.value = str(R.string.settings_msg_profile_failed)
                    } else {
                        profiles.value = profileRepository.all()
                        profileRepository.setActive(created.id)
                        message.value = str(R.string.settings_msg_profile_created, created.name)
                    }
                }

                TextField.FLAVOR -> prompt.onSubmit?.let { message.value = it(value) }

                TextField.HA_URL -> {
                    settingsRepository.setSmartHomeCredentials(value, settings.smartHomeToken)
                    if (value.isNotBlank()) smartHomeRepository.movePhoneLink(value)
                    message.value = if (value.isBlank()) {
                        str(R.string.settings_msg_ha_url_cleared)
                    } else {
                        str(R.string.settings_msg_address_saved_value, value)
                    }
                }

                TextField.WEATHER_CITY -> {
                    weatherPlaces.value = geocodingRepository.search(value)
                    if (weatherPlaces.value.isEmpty()) {
                        message.value = str(R.string.settings_msg_nothing_found, value)
                    }
                }

                TextField.TMDB_KEY -> {
                    settingsRepository.setTmdbApiKey(value)
                    message.value = str(if (value.isBlank()) R.string.settings_msg_tmdb_removed else R.string.settings_msg_tmdb_saved)
                }

                TextField.LIKHTAR_URL -> {
                    settingsRepository.setLikhtarBaseUrl(value)
                    message.value = str(if (value.isBlank()) R.string.settings_msg_likhtar_url_removed else R.string.settings_address_saved)
                }

                TextField.TV_COUNTRY_FILTER -> {
                    tvCountryFilter.value = value
                }

                TextField.TV_PLAYLIST_URL -> {
                    settingsRepository.setTvPlaylistUrl(value)
                    message.value = str(if (value.isBlank()) R.string.settings_msg_playlist_cleared else R.string.settings_msg_playlist_saved)
                }

                TextField.TV_EPG_URL -> {
                    settingsRepository.setTvEpgUrl(value)
                    tvGuideRepository.refresh()
                    message.value = str(if (value.isBlank()) R.string.settings_msg_epg_removed else R.string.settings_msg_epg_loading)
                }

                TextField.HA_TOKEN -> {
                    settingsRepository.setSmartHomeCredentials(settings.smartHomeUrl, value)
                    // Сам токен у повідомленні не показуємо — він секрет, як і в логах.
                    message.value = str(if (value.isBlank()) R.string.settings_msg_token_removed else R.string.settings_msg_token_saved)
                }

            }
        }
    }

    fun toggleHomeTorch() {
        viewModelScope.launch {
            settingsRepository.setHomeShowTorch(!settingsRepository.settings.first().homeShowTorch)
        }
    }

    fun toggleVoiceOnTv() {
        viewModelScope.launch {
            settingsRepository.setVoiceSearchOnTv(!settingsRepository.settings.first().voiceSearchOnTv)
        }
    }

    fun cycleHomeChannelCount() {
        viewModelScope.launch { settingsRepository.cycleHomeChannelCount() }
    }

    fun togglePinnedChannel(url: String) {
        viewModelScope.launch { settingsRepository.toggleFav(url) }
    }

    fun toggleHomeAppsPinned() {
        viewModelScope.launch {
            settingsRepository.setHomeAppsPinned(!settingsRepository.settings.first().homeAppsPinned)
        }
    }

    fun cycleHomeAppsCount() {
        viewModelScope.launch { settingsRepository.cycleHomeAppsCount() }
    }

    fun dismissTextPrompt() {
        textPrompt.value = null
    }

    /** Обіцянка з тосту «Повернути всі — Налаштування → Телебачення». */
    /** Приховані канали ТВ Ефіру: «Повернути всі» — лише коли є що повертати. */
    val hiddenChannels: StateFlow<Set<String>> =
        tvGroupsRepository.hiddenChannels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun restoreHiddenChannels() {
        viewModelScope.launch {
            tvGroupsRepository.clearHiddenChannels()
            message.value = str(R.string.settings_msg_hidden_restored)
        }
    }

    fun toggleTvCountry(code: String) {
        viewModelScope.launch {
            settingsRepository.toggleTvCountry(code)
            val selected = settingsRepository.settings.first().tvCountries
            message.value = if (selected.isEmpty()) {
                str(R.string.settings_msg_no_tv_country)
            } else {
                str(R.string.settings_msg_tv_countries, selected.size)
            }
        }
    }

    fun editTvCountryFilter() {
        lastEditedField.value = TextField.TV_COUNTRY_FILTER
        textPrompt.value = TextPrompt(
            field = TextField.TV_COUNTRY_FILTER,
            title = str(R.string.settings_country_search),
            description = str(R.string.settings_prompt_country_desc),
            placeholder = "Ukraine, Poland…",
            initialValue = tvCountryFilter.value,
            allowClear = tvCountryFilter.value.isNotBlank(),
        )
    }

    /**
     * Плеєр для потоків Кінозалу: «Питати щоразу» плюс усі встановлені плеєри по колу.
     *
     * Список читається щоразу з системи, а не кешується: плеєр можуть поставити чи знести
     * між заходами в налаштування, і тоді в списку висіло б неіснуюче.
     */
    /**
     * Країни доступності: сервер приймає `country=UA,PL`, тому це набір. Порожній набір
     * означає `auto`, і це нормальний стан, а не «нічого не обрано». Саме вікно вибору —
     * стан екрана, не моделі: `combine` в [uiState] і без нього на межі арності.
     */
    fun toggleAvailabilityCountry(code: String) {
        viewModelScope.launch {
            val current = settingsRepository.settings.first().availabilityCountries
            val next = if (code in current) current - code else current + code
            settingsRepository.setAvailabilityCountries(next)
        }
    }

    /** «Автоматично» — це порожній набір, а не окрема країна в списку. */
    fun clearAvailabilityCountries() {
        viewModelScope.launch { settingsRepository.setAvailabilityCountries(emptySet()) }
    }

    /** Підпис у рядку: прапорці обраних країн, бо коди на екрані ні про що не кажуть. */
    fun availabilityCountriesLabel(codes: Set<String>): String {
        if (codes.isEmpty()) return str(R.string.settings_auto)
        val known = uiState.value.tvCountries.filter { it.code in codes }
        return known.joinToString(" ") { "${it.flag} ${it.code}" }
            .ifBlank { codes.sorted().joinToString(", ") }
    }

    /** Порядок кнопок на картці: стрілка в рядку рухає його на одну позицію. */
    fun moveTitleAction(key: String, up: Boolean) {
        viewModelScope.launch {
            val order = settingsRepository.settings.first().titleActionsOrder
            val next = if (up) TitleActions.moveUp(order, key) else TitleActions.moveDown(order, key)
            settingsRepository.setTitleActionsOrder(next)
        }
    }

    fun titleActionLabel(key: String): String = str(TitleActions.labelRes(key))

    fun cycleVideoPlayer() {
        viewModelScope.launch {
            val players = appsRepository.videoPlayers().map { it.packageName }
            val order = listOf("") + players
            val current = settingsRepository.settings.first().videoPlayerPackage
            val next = order[(order.indexOf(current).takeIf { it >= 0 }?.plus(1) ?: 0) % order.size]
            settingsRepository.setVideoPlayerPackage(next)
        }
    }

    /** Людська назва обраного плеєра — пакет на екрані ні про що не каже. */
    fun videoPlayerLabel(packageName: String): String = when {
        packageName.isBlank() -> str(R.string.settings_player_ask)
        else -> appsRepository.videoPlayers().firstOrNull { it.packageName == packageName }
            ?.label
            ?: packageName
    }

    fun cycleAmbientTimeout() {
        viewModelScope.launch {
            val current = settingsRepository.settings.first().ambientTimeoutSeconds
            val next = AMBIENT_TIMEOUTS[
                (AMBIENT_TIMEOUTS.indexOf(current).takeIf { it >= 0 }?.plus(1) ?: 0) % AMBIENT_TIMEOUTS.size,
            ]
            settingsRepository.setAmbientTimeoutSeconds(next)
            message.value = if (next == 0) {
                str(R.string.settings_msg_tiles_stay)
            } else {
                str(
                    R.string.settings_msg_tiles_hide,
                    if (next < 120) str(R.string.settings_seconds, next) else str(R.string.settings_minutes, next / 60),
                )
            }
        }
    }

    fun editTmdbKey() {
        lastEditedField.value = TextField.TMDB_KEY
        textPrompt.value = TextPrompt(
            field = TextField.TMDB_KEY,
            title = str(R.string.settings_prompt_tmdb_title),
            description = str(R.string.settings_prompt_tmdb_desc),
            placeholder = str(R.string.settings_prompt_tmdb_placeholder),
            initialValue = "",
            allowClear = uiState.value.settings.tmdbApiKey.isNotBlank(),
        )
    }

    fun editLikhtarUrl() {
        lastEditedField.value = TextField.LIKHTAR_URL
        textPrompt.value = TextPrompt(
            field = TextField.LIKHTAR_URL,
            title = str(R.string.settings_prompt_likhtar_title),
            description = str(R.string.settings_prompt_likhtar_desc),
            placeholder = "http://192.168.1.10:18118",
            initialValue = uiState.value.settings.likhtarBaseUrl,
            allowClear = uiState.value.settings.likhtarBaseUrl.isNotBlank(),
        )
    }

    fun editWeatherCity() {
        lastEditedField.value = TextField.WEATHER_CITY
        textPrompt.value = TextPrompt(
            field = TextField.WEATHER_CITY,
            title = str(R.string.settings_prompt_weather_title),
            description = str(R.string.settings_prompt_weather_desc),
            placeholder = str(R.string.settings_prompt_weather_placeholder),
            initialValue = "",
            allowClear = false,
        )
    }

    fun selectWeatherPlace(place: GeoPlace) {
        viewModelScope.launch {
            settingsRepository.setWeatherPlace(place.name, place.latitude, place.longitude)
            weatherPlaces.value = emptyList()
            message.value = str(R.string.settings_msg_weather_city, place.name)
        }
    }

    fun editTvPlaylistUrl() {
        lastEditedField.value = TextField.TV_PLAYLIST_URL
        textPrompt.value = TextPrompt(
            field = TextField.TV_PLAYLIST_URL,
            title = str(R.string.settings_prompt_playlist_title),
            description = str(R.string.settings_prompt_playlist_desc),
            placeholder = "https://provider.example/playlist.m3u",
            initialValue = uiState.value.settings.tvPlaylistUrl,
            allowClear = uiState.value.settings.tvPlaylistUrl.isNotBlank(),
        )
    }

    fun editTvEpgUrl() {
        lastEditedField.value = TextField.TV_EPG_URL
        textPrompt.value = TextPrompt(
            field = TextField.TV_EPG_URL,
            title = str(R.string.settings_epg_title),
            description = str(R.string.settings_prompt_epg_desc),
            placeholder = "https://provider.example/epg.xml.gz",
            initialValue = uiState.value.settings.tvEpgUrl,
            allowClear = uiState.value.settings.tvEpgUrl.isNotBlank(),
        )
    }

    /** Відкритий QR «Підключити з телефону»; усередині `null` — мережі не видно. */
    private val _haLink = MutableStateFlow<HaLinkQr?>(null)
    val haLink: StateFlow<HaLinkQr?> = _haLink
    val haLinkStatus get() = smartHomeRepository.phoneLinkStatus

    fun startHaPhoneLink() {
        viewModelScope.launch { _haLink.value = HaLinkQr(smartHomeRepository.startPhoneLink()) }
    }

    fun closeHaPhoneLink() {
        _haLink.value = null
    }

    fun haLinkedFromPhone(): Boolean = smartHomeRepository.linkedFromPhone()

    fun forgetHaPhoneLink() {
        smartHomeRepository.forgetPhoneLink()
        message.value = str(R.string.settings_msg_phone_link_forgotten)
    }

    fun testSmartHomeConnection() {
        viewModelScope.launch {
            message.value = str(R.string.settings_msg_ha_checking)
            smartHomeRepository.ping()
                .onSuccess { message.value = str(R.string.settings_msg_ha_answered, it) }
                .onFailure { message.value = it.message ?: str(R.string.settings_msg_no_connection) }
        }
    }

    fun setStartSection(next: LikhtarSection) {
        viewModelScope.launch {
            settingsRepository.setStartSection(next)
            message.value = str(R.string.settings_msg_start_section, str(next.titleRes))
        }
    }

    fun requestPinReset() {
        if (!uiState.value.pinSet) {
            message.value = str(R.string.settings_msg_pin_not_set)
            return
        }
        pinError.value = null
        pinPrompt.value = str(R.string.settings_pin_reset_prompt)
    }

    fun submitPin(pin: String) {
        viewModelScope.launch {
            if (parentalControlRepository.verifyPin(pin)) {
                parentalControlRepository.clearPin()
                dismissPin()
                message.value = str(R.string.settings_msg_pin_reset)
            } else {
                pinError.value = str(R.string.settings_pin_wrong)
            }
        }
    }

    fun dismissPin() {
        pinPrompt.value = null
        pinError.value = null
    }

    /** Все, що перекриває екран або показується поверх нього — одним пакетом для `combine`. */
    private data class Sources(
        val smartHome: com.likhtar.home.core.smarthome.SmartHomeState,
        val countries: List<TvCountry>,
        val countryFilter: String,
        val weatherPlaces: List<GeoPlace>,
        val recent: Pair<List<RecentChannel>, Pair<Boolean, String>>,
    ) {
        val recentChannels: List<RecentChannel> get() = recent.first
        val megogoLinked: Boolean get() = recent.second.first
        val megogoStatus: String get() = recent.second.second
    }

    private data class Overlays(
        val pinPrompt: String?,
        val pinError: String?,
        val message: String?,
        val textPrompt: TextPrompt?,
        val lastEditedField: TextField?,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val INTERVALS = listOf(5, 15, 30, 60)
        val DIM_LEVELS = listOf(0, 15, 30, 45, 60)
        val REFRESH_INTERVALS = listOf(5, 15, 30, 60)
        val AMBIENT_TIMEOUTS = listOf(30, 60, 180, 300, 0)
    }
}

/** Обгортка, щоб «сервер не піднявся» (url = null) відрізнявся від «діалог закрито». */
class HaLinkQr(val url: String?)
