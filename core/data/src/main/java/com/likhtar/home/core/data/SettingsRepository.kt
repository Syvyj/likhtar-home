package com.likhtar.home.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.likhtar.home.core.model.LikhtarSection
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "likhtar_settings",
)

// DEFAULT_VIDEO_PLAYER — у джерелах flavor-а (src/personal, src/play).

/** Налаштування лаунчера, які реально впливають на поведінку. */

data class LikhtarSettings(
    /** Розділ, який відкривається при старті лаунчера. */
    val startSection: LikhtarSection = LikhtarSection.HOME,
    val showWeather: Boolean = true,
    /**
     * Чи показували вже пропозицію зробити лаунчер домашнім екраном.
     *
     * Саме «показували», а не «закріплено»: якщо користувач свідомо відмовився, лізти
     * до нього при кожному старті не можна — розділ у налаштуваннях лишається завжди.
     */
    val homeOnboardingShown: Boolean = false,
    /** Майстер першого запуску пройдено чи пропущено. */
    val firstRunDone: Boolean = false,

    // ── Контекстні ряди Головної ────────────────────────────────────────────────
    // Кожен ряд з'являється лише під фокусом свого пункту доку, тому налаштування
    // тут — це відповідь на «що саме показувати, коли я став на цей пункт».

    /** Картка «Ліхтарик» у ряду LIKHTAR HOME. */
    val homeShowTorch: Boolean = true,
    /**
     * Кнопка «Голосом» у Ліхтарику. За замовчуванням вимкнена: на частині прошивок
     * системне розпізнавання ламається, а надиктувати можна з телефону («З телефону»).
     */
    val voiceSearchOnTv: Boolean = false,
    /** Живий фон першої сцени тайтла: кадри повільно пливуть і змінюються. */
    val titleLiveStills: Boolean = true,
    /** ↑ над кнопками сторінки фільму відкриває трейлер; вимкнено — ↑ нікуди не веде. */
    val titleTrailerOnUp: Boolean = true,
    /** Скільки карток у «Продовжити перегляд»: 3, 5 або 7. */
    /** Скільки каналів показує «Останні канали». */
    val homeChannelCount: Int = 4,
    /** Закріплені канали (URL) — стоять першими й не витісняються історією. */
    val pinnedChannels: Set<String> = emptySet(),
    /** Ряд «Застосунки» показує закріплені, а не останні запущені. */
    val homeAppsPinned: Boolean = false,
    /** Скільки застосунків у ряду. */
    val homeAppsCount: Int = 5,

    /** Ідентифікатор джерела шпалер (`WallpaperProvider.id`). */
    // Likhtar Home має власний набір, який працює без мережі й уже підготовлений під
    // safe-зони Головної. "Без шпалер" лишається доступним, але не є першим досвідом.
    val wallpaperProviderId: String = "bundled",
    val wallpaperIntervalMinutes: Int = 15,
    /** Затемнення шпалер, 0–60 %: захист контрасту тексту й OLED-панелей. */
    val wallpaperDimPercent: Int = 0,
    /** Хто зараз дивиться. «default» — профіль «Дім», у який падає все без profile. */
    val activeProfileId: String = "default",
    /**
     * Прибирати з каталожних рядів те, що профіль уже додивився.
     *
     * Вимкнено за замовчуванням: галочка в кутку картки нічого не ховає, а прибирання
     * лишає єдиний шлях назад — пошук, і людині, яка хотіла переглянути ще раз, це
     * читається як «фільм зник».
     */
    val hideWatched: Boolean = false,
    /** Ряд «Аніме» в Кінозалі й його сторінка. Вимкнено: аніме дивляться не всі. */
    val showAnime: Boolean = false,
    /**
     * Кінозал лише з того, що вмикається встановленими застосунками: без топів і новинок
     * сервісів, яких на приставці немає. Вимкнено: побачити, що варте підписки, теж корисно
     */
    val onlyMyApps: Boolean = false,
    /** Сервіси, яких людина не хоче бачити в Кінозалі: ні плитки, ні їхніх рядів (ключі StreamingService). */
    val hiddenServices: Set<String> = emptySet(),
    /** Розділи, прибрані з доку Головної (маршрути LikhtarSection); меню вгорі їх лишає. */
    val hiddenDockSections: Set<String> = emptySet(),
    /**
     * Мова й країна контенту (майстер першого запуску): мова назв і описів TMDB,
     * країна — сервіси, топи, провайдери, вікові рейтинги. Порожньо — не вибрано, беремо
     * системні ([locale]).
     */
    val contentLanguage: String = "",
    val contentCountry: String = "",
    /**
     * Показувати в рядах те, для чого ми НЕ знаємо української доріжки.
     *
     * УВІМКНЕНО за замовчуванням, і це зміна рішення, а не недогляд. Раніше ряд був
     * вибіркою з нашої бази, і вимкнене налаштування означало «показуй лише те, що
     * грається». Тепер каталог бере TMDB, а наші шари його анотують — і жорсткий фільтр
     * перетворює світовий ряд на кілька карток, тобто робить те саме відчуття порожнечі,
     * проти якого все й будувалось.
     *
     * Придатне однаково стоїть ПОПЕРЕДУ: це ранжування. Вимкнене налаштування лишається
     * для того, хто хоче бачити рівно те, що відкриється зараз, — і воно ж потрібне на
     * інсталяції без власних джерел, щоб не обіцяти зайвого.
     */
    val showWithoutUaAudio: Boolean = true,
    val showWallpaperAttribution: Boolean = true,
    /** URL JSON-маніфесту для віддаленого набору шпалер; порожній — джерело вимкнене. */
    val wallpaperManifestUrl: String = "",
    /** Тема для джерела «Теми»: nature / animals / cities / space / mix. */
    val wallpaperTheme: String = "mix",

    /** «Смарт дім» можна повністю прибрати з меню та з Головної. */
    val smartHomeEnabled: Boolean = true,
    val smartHomeRefreshSeconds: Int = 15,
    /** Адреса Home Assistant і long-lived access token; порожні — інтеграція не налаштована. */
    val smartHomeUrl: String = "",
    val smartHomeToken: String = "",
    /** Голосові команди в розділі «Смарт дім». */
    val voiceCommandsEnabled: Boolean = true,

    /** Коди країн (ISO-3166-1 alpha-2), канали яких показує «ТВ Ефір». */
    val tvCountries: Set<String> = emptySet(),
    /** URL XMLTV із телепрограмою; порожній — сітка показує лише канали. */
    val tvEpgUrl: String = "",
    /**
     * URL плейлиста M3U від провайдера; порожній — канали беруться з вбудованих прив'язок.
     *
     * Саме порожнє значення тримає обіцянку «типовим лишається вбудоване джерело»: поки тут
     * нічого немає, ТВ Ефір працює так, як працював.
     */
    val tvPlaylistUrl: String = "",

    /** Через скільки секунд бездіяльності Головна ховає плитки; 0 — ніколи. */
    val ambientTimeoutSeconds: Int = 60,

    /** Ключ TMDB для каталогу Кінозалу; порожній — каталог вимкнено. */
    val tmdbApiKey: String = "",
    /** Базова адреса проєкту Ліхтаря, звідки читаються збагачені картки. */
    val likhtarBaseUrl: String = "",

    /**
     * Свій AI людини (публічна збірка): провайдер із [com.likhtar.home.core.data.AiProviders]
     * чи порожньо — AI немає. Адреса й модель порожні — типові провайдера. Ключ — секрет:
     * лише з телефона, ніколи на екран і в лог.
     */
    val aiProvider: String = "",
    val aiUrl: String = "",
    val aiModel: String = "",
    val aiKey: String = "",

    /**
     * Чим відкривати потоки Кінозалу. Порожньо — питати щоразу.
     *
     * Тримаємо вибір у себе, бо системний типовий застосунок для відео задається один раз
     * і назавжди: на приставці Just Player перехопив усе відео, і вибору більше не було.
     *
     * Типовий — [DEFAULT_VIDEO_PLAYER] зі збірки (чому саме він — там). Якщо його не
     * встановлено, запуск падає у системний вибір — це робить LikhtarApp.
     */
    val videoPlayerPackage: String = DEFAULT_VIDEO_PLAYER,

    /**
     * Для якої країни показувати доступність на сторінці фільму.
     *
     * Порожньо — `auto`, сервер визначає країну сам за своїм виходом у мережу. Явні країни
     * потрібні тим, хто дивиться через VPN іншої країни: з польського IP «а що є в Україні» —
     * законне питання, і навпаки. Кілька країн разом сервер приймає (`country=UA,PL`), тому
     * це набір, а не одне значення. MEGOGO і SWEET.TV цим не керуються, вони завжди
     * українські.
     */
    val availabilityCountries: Set<String> = emptySet(),

    /**
     * Порядок груп кнопок на картці фільму. Ключі — [TitleActions].
     *
     * Список, а не набір: тут важливий саме порядок, а `stringSetPreferencesKey` його не
     * зберігає. Тому в DataStore лежить один рядок через кому.
     */
    val titleActionsOrder: List<String> = TitleActions.DEFAULT,

    /** Місто погоди на Головній. Координати зберігаємо разом із назвою — щоб не геокодувати щоразу. */
    /** Порожнє — ще не визначено: погода сама бере місто з часового поясу (OpenMeteoWeatherRepository). */
    val weatherCity: String = "",
    val weatherLatitude: Double = 0.0,
    val weatherLongitude: Double = 0.0,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        repositoryScope.launch {
            context.settingsDataStore.edit { prefs ->
                // «Затемнення» раніше діяло на всі розділи, ОКРІМ Головної, а тепер —
                // тільки на Головну. Старе значення було вибором про інші екрани, тож
                // переносити його на Головну не можна: скидаємо один раз.
                if (prefs[KEY_DIM_IS_HOME_ONLY] != true) {
                    prefs[KEY_WALLPAPER_DIM] = 0
                    prefs[KEY_DIM_IS_HOME_ONLY] = true
                }
                // Секрети, збережені до шифрування, — переписати шифрованими.
                SECRET_KEYS.forEach { key ->
                    val stored = prefs[key]
                    if (!stored.isNullOrEmpty() && !Secrets.isSealed(stored)) prefs[key] = Secrets.seal(stored)
                }
            }
        }
    }

    val settings: Flow<LikhtarSettings> = context.settingsDataStore.data.map { prefs ->
        LikhtarSettings(
            startSection = LikhtarSection.fromRoute(prefs[KEY_START_SECTION])
                ?: LikhtarSection.HOME,
            showWeather = prefs[KEY_SHOW_WEATHER] ?: true,
            homeOnboardingShown = prefs[KEY_HOME_ONBOARDING] ?: false,
            firstRunDone = prefs[KEY_FIRST_RUN_DONE] ?: false,
            homeShowTorch = prefs[KEY_HOME_TORCH] ?: true,
            voiceSearchOnTv = prefs[KEY_VOICE_ON_TV] ?: false,
            titleLiveStills = prefs[KEY_LIVE_STILLS] ?: true,
            titleTrailerOnUp = prefs[KEY_TRAILER_UP] ?: true,
            homeChannelCount = prefs[KEY_HOME_CHANNELS] ?: 4,
            pinnedChannels = prefs[KEY_PINNED_CHANNELS].orEmpty(),
            homeAppsPinned = prefs[KEY_HOME_APPS_PINNED] ?: false,
            homeAppsCount = prefs[KEY_HOME_APPS_COUNT] ?: 5,
            wallpaperProviderId = prefs[KEY_WALLPAPER_PROVIDER] ?: "bundled",
            wallpaperIntervalMinutes = prefs[KEY_WALLPAPER_INTERVAL] ?: 15,
            wallpaperDimPercent = prefs[KEY_WALLPAPER_DIM] ?: 0,
            activeProfileId = prefs[KEY_ACTIVE_PROFILE] ?: "default",
            hideWatched = prefs[KEY_HIDE_WATCHED] ?: false,
            showAnime = prefs[KEY_SHOW_ANIME] ?: false,
            onlyMyApps = prefs[KEY_ONLY_MY_APPS] ?: false,
            hiddenServices = prefs[KEY_HIDDEN_SERVICES].orEmpty(),
            hiddenDockSections = prefs[KEY_HIDDEN_DOCK].orEmpty(),
            contentLanguage = prefs[KEY_CONTENT_LANGUAGE].orEmpty(),
            contentCountry = prefs[KEY_CONTENT_COUNTRY].orEmpty(),
            showWithoutUaAudio = prefs[KEY_SHOW_WITHOUT_UA] ?: true,
            showWallpaperAttribution = prefs[KEY_WALLPAPER_ATTRIBUTION] ?: true,
            wallpaperManifestUrl = prefs[KEY_WALLPAPER_MANIFEST] ?: "",
            wallpaperTheme = prefs[KEY_WALLPAPER_THEME] ?: "mix",
            smartHomeEnabled = prefs[KEY_SMART_HOME_ENABLED] ?: true,
            smartHomeRefreshSeconds = prefs[KEY_SMART_HOME_REFRESH] ?: 15,
            smartHomeUrl = prefs[KEY_SMART_HOME_URL] ?: "",
            smartHomeToken = Secrets.open(prefs[KEY_SMART_HOME_TOKEN]),
            voiceCommandsEnabled = prefs[KEY_VOICE_COMMANDS] ?: true,
            tvCountries = prefs[KEY_TV_COUNTRIES].orEmpty(),
            tvEpgUrl = prefs[KEY_TV_EPG_URL] ?: "",
            tvPlaylistUrl = Secrets.open(prefs[KEY_TV_PLAYLIST_URL]),
            tmdbApiKey = Secrets.open(prefs[KEY_TMDB_KEY]),
            likhtarBaseUrl = prefs[KEY_LIKHTAR_URL] ?: "",
            aiProvider = prefs[KEY_AI_PROVIDER] ?: "",
            aiUrl = prefs[KEY_AI_URL] ?: "",
            aiModel = prefs[KEY_AI_MODEL] ?: "",
            aiKey = Secrets.open(prefs[KEY_AI_KEY]),
            videoPlayerPackage = prefs[KEY_VIDEO_PLAYER] ?: DEFAULT_VIDEO_PLAYER,
            availabilityCountries = prefs[KEY_AVAILABILITY_COUNTRIES].orEmpty(),
            titleActionsOrder = TitleActions.parse(prefs[KEY_TITLE_ACTIONS]),
            ambientTimeoutSeconds = prefs[KEY_AMBIENT_TIMEOUT] ?: 60,
            weatherCity = prefs[KEY_WEATHER_CITY] ?: "",
            weatherLatitude = prefs[KEY_WEATHER_LAT] ?: 0.0,
            weatherLongitude = prefs[KEY_WEATHER_LON] ?: 0.0,
        )
    }

    suspend fun setSmartHomeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SMART_HOME_ENABLED] = enabled }
    }

    suspend fun setVoiceCommandsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_VOICE_COMMANDS] = enabled }
    }

    suspend fun setSmartHomeRefreshSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[KEY_SMART_HOME_REFRESH] = seconds.coerceIn(5, 300) }
    }

    /** Токен — секрет користувача: лаунчер його лише зберігає й ніколи не логує. */
    suspend fun setSmartHomeCredentials(url: String, token: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SMART_HOME_URL] = url.trim()
            prefs[KEY_SMART_HOME_TOKEN] = Secrets.seal(token.trim())
        }
    }

    suspend fun setWallpaperProvider(providerId: String) {
        context.settingsDataStore.edit { it[KEY_WALLPAPER_PROVIDER] = providerId }
    }

    /** Перемикає країну в списку — на пульті це одна дія замість «додати»/«прибрати». */
    suspend fun toggleTvCountry(code: String) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_TV_COUNTRIES].orEmpty()
            prefs[KEY_TV_COUNTRIES] = if (code in current) current - code else current + code
        }
    }

    suspend fun setTmdbApiKey(key: String) {
        context.settingsDataStore.edit { it[KEY_TMDB_KEY] = Secrets.seal(key.trim()) }
    }

    /** Порожній [provider] вимикає AI і стирає ключ. */
    suspend fun setAi(provider: String, url: String, model: String, key: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_AI_PROVIDER] = provider
            prefs[KEY_AI_URL] = url.trim().trimEnd('/')
            prefs[KEY_AI_MODEL] = model.trim()
            prefs[KEY_AI_KEY] = if (provider.isEmpty()) "" else Secrets.seal(key.trim())
        }
    }

    suspend fun setLikhtarBaseUrl(url: String) {
        context.settingsDataStore.edit { it[KEY_LIKHTAR_URL] = url.trim() }
    }

    suspend fun setTitleActionsOrder(order: List<String>) {
        context.settingsDataStore.edit { it[KEY_TITLE_ACTIONS] = order.joinToString(",") }
    }

    suspend fun setAvailabilityCountries(countries: Set<String>) {
        context.settingsDataStore.edit { it[KEY_AVAILABILITY_COUNTRIES] = countries }
    }

    suspend fun setVideoPlayerPackage(packageName: String) {
        context.settingsDataStore.edit { it[KEY_VIDEO_PLAYER] = packageName.trim() }
    }

    suspend fun setAmbientTimeoutSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[KEY_AMBIENT_TIMEOUT] = seconds.coerceIn(0, 3600) }
    }

    suspend fun setWeatherPlace(city: String, latitude: Double, longitude: Double) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_WEATHER_CITY] = city
            prefs[KEY_WEATHER_LAT] = latitude
            prefs[KEY_WEATHER_LON] = longitude
        }
    }

    suspend fun setTvEpgUrl(url: String) {
        context.settingsDataStore.edit { it[KEY_TV_EPG_URL] = url.trim() }
    }

    suspend fun setTvPlaylistUrl(url: String) {
        context.settingsDataStore.edit { it[KEY_TV_PLAYLIST_URL] = Secrets.seal(url.trim()) }
    }

    suspend fun setWallpaperTheme(themeId: String) {
        context.settingsDataStore.edit { it[KEY_WALLPAPER_THEME] = themeId }
    }

    suspend fun setWallpaperIntervalMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[KEY_WALLPAPER_INTERVAL] = minutes }
    }

    suspend fun setWallpaperDimPercent(percent: Int) {
        context.settingsDataStore.edit { it[KEY_WALLPAPER_DIM] = percent.coerceIn(0, 60) }
    }

    suspend fun setWallpaperAttribution(show: Boolean) {
        context.settingsDataStore.edit { it[KEY_WALLPAPER_ATTRIBUTION] = show }
    }

    suspend fun setStartSection(section: LikhtarSection) {
        context.settingsDataStore.edit { it[KEY_START_SECTION] = section.route }
    }

    suspend fun setFirstRunDone() {
        context.settingsDataStore.edit { it[KEY_FIRST_RUN_DONE] = true }
    }

    suspend fun setHomeOnboardingShown() {
        context.settingsDataStore.edit { it[KEY_HOME_ONBOARDING] = true }
    }

    suspend fun setShowWeather(show: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_WEATHER] = show }
    }

    suspend fun setHomeShowTorch(show: Boolean) {
        context.settingsDataStore.edit { it[KEY_HOME_TORCH] = show }
    }

    suspend fun setVoiceSearchOnTv(on: Boolean) {
        context.settingsDataStore.edit { it[KEY_VOICE_ON_TV] = on }
    }

    suspend fun setTitleTrailerOnUp(on: Boolean) {
        context.settingsDataStore.edit { it[KEY_TRAILER_UP] = on }
    }

    suspend fun setTitleLiveStills(on: Boolean) {
        context.settingsDataStore.edit { it[KEY_LIVE_STILLS] = on }
    }

    /** Перебір 3 → 5 → 7 по колу: проміжні значення на цьому ряду нічого не міняють. */
    suspend fun setActiveProfile(id: String) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_ACTIVE_PROFILE] = id }
    }

    suspend fun setShowWithoutUaAudio(show: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SHOW_WITHOUT_UA] = show }
    }

    suspend fun setHideWatched(hide: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_HIDE_WATCHED] = hide }
    }

    suspend fun toggleDockSection(route: String) {
        context.settingsDataStore.edit { prefs ->
            val hidden = prefs[KEY_HIDDEN_DOCK].orEmpty()
            prefs[KEY_HIDDEN_DOCK] = if (route in hidden) hidden - route else hidden + route
        }
    }

    suspend fun toggleHiddenService(key: String) {
        context.settingsDataStore.edit { prefs ->
            val hidden = prefs[KEY_HIDDEN_SERVICES].orEmpty()
            prefs[KEY_HIDDEN_SERVICES] = if (key in hidden) hidden - key else hidden + key
        }
    }

    suspend fun setOnlyMyApps(only: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_ONLY_MY_APPS] = only }
    }

    suspend fun setContentLocale(language: String, country: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_CONTENT_LANGUAGE] = language
            prefs[KEY_CONTENT_COUNTRY] = country
        }
    }

    suspend fun setShowAnime(show: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SHOW_ANIME] = show }
    }

    suspend fun cycleHomeChannelCount() {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_HOME_CHANNELS] ?: 4
            prefs[KEY_HOME_CHANNELS] = CHANNEL_COUNTS.next(current)
        }
    }

    suspend fun toggleFav(channelUrl: String) {
        context.settingsDataStore.edit { prefs ->
            val pinned = prefs[KEY_PINNED_CHANNELS].orEmpty()
            prefs[KEY_PINNED_CHANNELS] =
                if (channelUrl in pinned) pinned - channelUrl else pinned + channelUrl
        }
    }

    suspend fun setHomeAppsPinned(pinned: Boolean) {
        context.settingsDataStore.edit { it[KEY_HOME_APPS_PINNED] = pinned }
    }

    suspend fun cycleHomeAppsCount() {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_HOME_APPS_COUNT] ?: 5
            prefs[KEY_HOME_APPS_COUNT] = APPS_COUNTS.next(current)
        }
    }

    private companion object {
        val KEY_START_SECTION = stringPreferencesKey("start_section")
        val KEY_HOME_ONBOARDING = booleanPreferencesKey("home_onboarding_shown")
        val KEY_FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
        val KEY_SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val KEY_HOME_TORCH = booleanPreferencesKey("home_torch")
        val KEY_VOICE_ON_TV = booleanPreferencesKey("voice_search_on_tv")
        val KEY_LIVE_STILLS = booleanPreferencesKey("title_live_stills")
        val KEY_TRAILER_UP = booleanPreferencesKey("title_trailer_on_up")
        val KEY_HOME_CHANNELS = intPreferencesKey("home_channel_count")
        val KEY_PINNED_CHANNELS = stringSetPreferencesKey("pinned_channels")
        val KEY_HOME_APPS_PINNED = booleanPreferencesKey("home_apps_pinned")
        val KEY_HOME_APPS_COUNT = intPreferencesKey("home_apps_count")

        val CHANNEL_COUNTS = listOf(4, 6, 8)
        val APPS_COUNTS = listOf(3, 5, 7)
        val KEY_WALLPAPER_PROVIDER = stringPreferencesKey("wallpaper_provider")
        val KEY_WALLPAPER_INTERVAL = intPreferencesKey("wallpaper_interval_minutes")
        val KEY_DIM_IS_HOME_ONLY = booleanPreferencesKey("dim_is_home_only")
        val KEY_ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val KEY_HIDE_WATCHED = booleanPreferencesKey("hide_watched")
        val KEY_SHOW_ANIME = booleanPreferencesKey("show_anime")
        val KEY_ONLY_MY_APPS = booleanPreferencesKey("only_my_apps")
        val KEY_HIDDEN_SERVICES = stringSetPreferencesKey("hidden_services")
        val KEY_HIDDEN_DOCK = stringSetPreferencesKey("hidden_dock_sections")
        val KEY_CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val KEY_CONTENT_COUNTRY = stringPreferencesKey("content_country")
        val KEY_SHOW_WITHOUT_UA = booleanPreferencesKey("show_without_ua_audio")
        val KEY_WALLPAPER_DIM = intPreferencesKey("wallpaper_dim_percent")
        val KEY_WALLPAPER_ATTRIBUTION = booleanPreferencesKey("wallpaper_attribution")
        val KEY_WALLPAPER_MANIFEST = stringPreferencesKey("wallpaper_manifest_url")
        val KEY_WALLPAPER_THEME = stringPreferencesKey("wallpaper_theme")
        val KEY_SMART_HOME_ENABLED = booleanPreferencesKey("smart_home_enabled")
        val KEY_SMART_HOME_REFRESH = intPreferencesKey("smart_home_refresh_seconds")
        val KEY_SMART_HOME_URL = stringPreferencesKey("smart_home_url")
        val KEY_SMART_HOME_TOKEN = stringPreferencesKey("smart_home_token")
        val KEY_VOICE_COMMANDS = booleanPreferencesKey("voice_commands_enabled")
        val KEY_TV_COUNTRIES = stringSetPreferencesKey("tv_countries")
        val KEY_TV_EPG_URL = stringPreferencesKey("tv_epg_url")
        val KEY_TV_PLAYLIST_URL = stringPreferencesKey("tv_playlist_url")
        val KEY_TMDB_KEY = stringPreferencesKey("tmdb_api_key")
        val KEY_LIKHTAR_URL = stringPreferencesKey("likhtar_base_url")
        val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        val KEY_AI_URL = stringPreferencesKey("ai_url")
        val KEY_AI_MODEL = stringPreferencesKey("ai_model")
        val KEY_AI_KEY = stringPreferencesKey("ai_key")

        /** Що лежить на диску лише шифрованим ([Secrets]). */
        private val SECRET_KEYS = listOf(KEY_SMART_HOME_TOKEN, KEY_TV_PLAYLIST_URL, KEY_TMDB_KEY, KEY_AI_KEY)
        val KEY_VIDEO_PLAYER = stringPreferencesKey("video_player_package")
        val KEY_AVAILABILITY_COUNTRIES = stringSetPreferencesKey("availability_countries")
        val KEY_TITLE_ACTIONS = stringPreferencesKey("title_actions_order")
        val KEY_AMBIENT_TIMEOUT = intPreferencesKey("ambient_timeout_seconds")
        val KEY_WEATHER_CITY = stringPreferencesKey("weather_city")
        val KEY_WEATHER_LAT = doublePreferencesKey("weather_latitude")
        val KEY_WEATHER_LON = doublePreferencesKey("weather_longitude")
    }
}

/** Наступне значення по колу; невідоме поточне повертає перше зі списку. */
private fun List<Int>.next(current: Int): Int {
    val index = indexOf(current)
    return if (index < 0) first() else this[(index + 1) % size]
}
