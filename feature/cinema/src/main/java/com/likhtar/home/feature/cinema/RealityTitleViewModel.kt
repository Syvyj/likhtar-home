package com.likhtar.home.feature.cinema

import androidx.navigation.toRoute
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.Episode
import com.likhtar.home.core.catalog.PlayRequest
import com.likhtar.home.core.catalog.QueueEntry
import com.likhtar.home.core.catalog.PlayingEpisode
import com.likhtar.home.core.catalog.PlaybackController
import com.likhtar.home.core.catalog.TitleDetails
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.LaunchAction
import com.likhtar.home.core.catalog.WatchOption
import com.likhtar.home.core.catalog.TmdbImages
import com.likhtar.home.core.catalog.ShowProgressRepository
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.tv.RealityItem
import com.likhtar.home.core.tv.RealityRepository
import com.likhtar.home.core.tv.Plus1Client
import com.likhtar.home.core.tv.Plus1Season
import com.likhtar.home.core.tv.TeleportalClient
import com.likhtar.home.core.tv.TeleportalReason
import com.likhtar.home.core.tv.TeleportalSeason
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RealityTitleUiState(
    val loading: Boolean = true,
    /** «Дивитись» позиції сервісу шукає наше джерело — до 45 с, тож екран очікування. */
    val searching: Boolean = false,
    val details: TitleDetails? = null,
    /** Сезони Телепорталу. Порожньо — це набір, у якого сезонів немає за природою. */
    val seasons: List<TeleportalSeason> = emptyList(),
    /** Номер обраного сезону, 1-based: `TitleDetails` говорить номерами, Телепортал — слагами. */
    val season: Int = 1,
    val episodes: List<Episode> = emptyList(),
    /** Канал, на якому шоу виходить: «СТБ», «Новий канал». Порожньо — не знаємо. */
    val channel: String = "",
    /** Серії беруться з YouTube-плейлиста: портал серій не дав (лежить або їх немає). */
    val youtubeEpisodes: Boolean = false,
    /** Офіційний плейлист мовника на YouTube — кнопка «YouTube» поруч із «Дивитись». */
    val youtube: WatchOption? = null,
    /** Той самий твір на сервісах (MEGOGO, Sweet.TV, youtv) — кнопками поруч із YouTube. */
    val services: List<WatchOption> = emptyList(),
    /** Серіал порталу (з каталогу Кінозалу), а не шоу: інакше на мітці стояло «Шоу». */
    val serial: Boolean = false,
    /**
     * Остання відкрита серія YouTube: сезон і номер у ньому, 0 — не відкривали. Позиції
     * YouTube не віддає, тож це «відкривав», а не «дивився до хвилини»: на неї стає фокус
     * після повернення, і вона позначена почату.
     */
    val resumeSeason: Int = 0,
    val resumeEpisode: Int = 0,
    /**
     * Наступна серія YouTube, коли попередня дограла до кінця: YouTube закривається сам
     * (finish_on_ended — без нього BACK вів на головну YouTube), а грати далі пропонуємо ми.
     */
    val nextUp: Episode? = null,
    /** Сезон [nextUp]: після останньої серії сезону — перша наступного. */
    val nextUpSeason: Int = 0,
    /** Серія майже напевно дограла — наступна вмикається сама через 5 с; інакше лише на OK. */
    val nextUpAuto: Boolean = false,
    val message: String? = null,
)

/**
 * Сторінка шоу — та сама сторінка матеріалу Кінозалу, лише з даними Телепорталу.
 *
 * Малюють її `PosterHead` і `EpisodesScene` із `TitleScreen`; тут збирається те, що вони
 * читають: `TitleDetails` і список серій. Звідси й переклад сезонів у номери — у моделі
 * Кінозалу сезон це число, а в Телепорталі слаг («sezon-2026»), тому номер слугує ключем,
 * а справжня назва їде підписом.
 *
 * Наборів із `channel-links.json` це не стосується: у них немає ані сезонів, ані випусків —
 * лише діплінк у застосунок, тож у них сторінка складається з самої шапки.
 */
@HiltViewModel
class RealityTitleViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val repository: RealityRepository,
    private val teleportal: TeleportalClient,
    private val plus1: Plus1Client,
    private val playback: PlaybackController,
    private val settings: SettingsRepository,
    private val showProgress: ShowProgressRepository,
    private val apps: AppsRepository,
    private val resolver: com.likhtar.home.core.catalog.ResolverClient,
    private val watchOptions: com.likhtar.home.core.catalog.WatchOptionsResolver,
    private val streams: com.likhtar.home.core.catalog.LikhtarStreams,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val state = MutableStateFlow(RealityTitleUiState())
    val uiState: StateFlow<RealityTitleUiState> = state.asStateFlow()

    private var item: RealityItem? = null
    /** Хеш випуску за його номером — плеєрна ручка адресується саме хешем. */
    private var hashes: Map<Int, String> = emptyMap()

    /** Запис «Продовжити» цього шоу з порталу — для позначок і фокуса (applyPortalResume). */
    private var portalResume: com.likhtar.home.core.catalog.ShowContinue? = null

    /** Сезон [number] завантажено: якщо продовжуємо саме в ньому — серія за хешем випуску. */
    private fun applyPortalResume(number: Int) {
        val r = portalResume ?: return
        val at = hashes.entries.firstOrNull { it.value == r.episode }?.key ?: return
        val next = if (r.finished && hashes.containsKey(at + 1)) at + 1 else at
        state.update { it.copy(resumeSeason = number, resumeEpisode = next) }
    }

    /** Сезони 1+1: у них числові id, і випуски доводиться питати окремо. */
    private var plus1Seasons: List<Plus1Season> = emptyList()

    /** Серії з YouTube-плейлиста, коли портал їх не дав. */
    private var youtubeVideos: List<com.likhtar.home.core.tv.PortalVideo> = emptyList()
    private var youtubeSeasons = false

    /** Що й коли відкрили в YouTube: за часом повернення видно, чи серія дограла. */
    private data class Launched(val season: Int, val number: Int, val atMs: Long, val seconds: Int)
    private var launched: Launched? = null

    /** Серії обраного сезону YouTube-твору; у творі з одного плейлиста — усі. */
    private val shownVideos
        get() = if (youtubeSeasons) youtubeVideos.filter { it.season == state.value.season } else youtubeVideos

    init {
        val id: String? = savedStateHandle.toRoute<RealityShowRoute>().id
        viewModelScope.launch { load(id) }
    }

    private suspend fun load(id: String?) {
        val found = id?.let { runCatching { repository.item(it) }.getOrNull() }
        if (found == null) {
            state.update { it.copy(loading = false, message = context.getString(R.string.cinema_show_not_found)) }
            return
        }
        item = found
        val project = found.teleportal?.let { runCatching { teleportal.project(it) }.getOrNull() }
        // 1+1 має свою сторінку проєкту: сезони там числовими id, а випуски приходять
        // окремим запитом — на відміну від Телепорталу, який віддає їх разом.
        val plus1Project = found.plus1?.let { runCatching { plus1.project(it) }.getOrNull() }
        // Сезони — від першого, за часом: спецвипуск 2023 не має стати після сезону 2025.
        // Телепортал — за id (росте з часом створення), 1+1 — за датою випуску (його id
        // хронології не тримають). Не знаємо всіх — порядок, як віддав портал.
        val plus1Dated = plus1Project?.seasons.orEmpty().all { it.date.isNotBlank() }
        plus1Seasons = plus1Project?.seasons.orEmpty().let { all -> if (plus1Dated) all.sortedBy { it.date } else all }
        // Телепортал і сам віддає за спаданням id — без order досить розвернути.
        val seasons = project?.seasons.orEmpty().let { all ->
            if (all.all { it.order > 0 }) all.sortedBy { it.order } else all.reversed()
        }
        // Кадром сторінки йде широка обкладинка проєкту: у неї ліва третина навмисно
        // порожня — рівно під назву й кнопки. Далі банер, і аж потім дрібне зображення
        // картки: 256×144 на весь екран і були тією мильністю, що псувала вигляд.
        val art = listOf(
            // Обкладинка проєкту перша: у неї порожня ліва третина рівно під наш текст.
            project?.logo.orEmpty(),
            project?.imageBig.orEmpty(),
            plus1Project?.image.orEmpty(),
            // Далі чистий кадр — він без промо-напису, тобто під текстом не заважає.
            found.frame,
            found.imageBig,
            found.image,
        ).firstOrNull { url -> url.isNotBlank() }
        val hint = com.likhtar.home.core.catalog.PortalShowHints.of(found.id)
        state.update {
            it.copy(
                loading = false,
                details = TitleDetails(
                    item = CatalogItem(
                        tmdbId = found.id.hashCode(),
                        kind = TitleKind.TV,
                        // Твір із каталогу Кінозалу приходить лише ключем — назву дає портал.
                        title = found.title.ifBlank { project?.title ?: plus1Project?.title ?: hint?.title.orEmpty() },
                        year = hint?.year.orEmpty(),
                        overview = listOf(
                            project?.description.orEmpty(),
                            plus1Project?.description.orEmpty(),
                            found.description,
                            hint?.overview.orEmpty(),
                        ).firstOrNull { it.isNotBlank() }.orEmpty(),
                        backdropPath = art ?: hint?.let { h ->
                            (h.backdropPath ?: h.posterPath)?.let { TmdbImages.backdrop(it) }
                        },
                        posterPath = found.bestFrame ?: hint?.posterPath?.let { TmdbImages.poster(it) },
                    ),
                    // Номери — позиції в списку сезонів: див. KDoc класу.
                    seasonNumbers = (seasons.indices + plus1Seasons.indices).distinct()
                        .map { index -> index + 1 },
                    // Ведучі й судді. У моделі Кінозалу це поле зветься `cast`, але підпис
                    // під нього екран бере окремо: «У ролях» про шоу — неправда.
                    cast = project?.persons.orEmpty(),
                    genres = found.genres.ifEmpty { hint?.genres.orEmpty() },
                    ageRating = project?.age.orEmpty(),
                ),
                seasons = seasons,
                channel = project?.channelTitle.orEmpty().ifBlank { hint?.channel.orEmpty() },
                serial = hint?.kind == TitleKind.TV && found.id.startsWith("youtube/") ||
                    found.teleportal?.serial == true || found.plus1?.let { plus1Seasons.size > 1 || found.genres.none() } == true,
            )
        }
        // Сезони тепер від першого — відкриваємо найсвіжіший, як і досі: він останній.
        // 1+1 без дат лишився в порядку порталу — там свіжий невідомо де, тож перший.
        // Де зупинились у цьому шоу (Телепортал, 1+1): той сезон і відкриваємо, а серію
        // продовження шукаємо за хешем випуску, щойно сезон завантажиться (інакше в серіалах і реаліті з порталів
        // не було прогресу перегляду).
        portalResume = if (seasons.isEmpty() && plus1Seasons.isEmpty()) null else
            runCatching { showProgress.continueShows() }.getOrDefault(emptyList()).firstOrNull { it.extKey == extKeyOf(found) }
        val resumeIdx = portalResume?.let { r ->
            seasons.indexOfFirst { it.slug == r.season }.takeIf { it >= 0 }
                ?: plus1Seasons.indexOfFirst { it.id == r.season }.takeIf { it >= 0 }
        }
        when {
            resumeIdx != null -> showSeason(resumeIdx + 1)
            seasons.isNotEmpty() -> showSeason(seasons.size)
            plus1Seasons.isNotEmpty() -> showSeason(if (plus1Dated) plus1Seasons.size else 1)
        }
        viewModelScope.launch {
            // Портал серій не дав — серії з офіційного плейлиста: обрати серію має бути
            // можна однаково, звідки б вона не грала.
            if (seasons.isEmpty() && plus1Seasons.isEmpty()) {
                val videos = runCatching { plus1.youtubeVideos(extKeyOf(found)) }.getOrDefault(emptyList())
                if (videos.isNotEmpty()) {
                    // Плейлисти мовників ідуть від новішого; серії — від першої. Дата ISO,
                    // тож рядкове порівняння й є хронологічним.
                    youtubeVideos = videos.sortedWith(compareBy({ it.season }, { it.date }))
                    // Сервер звів кілька плейлистів (сезони) — вкладка на кожен.
                    val numbers = videos.map { it.season }.filter { it > 0 }.distinct().sorted()
                    youtubeSeasons = numbers.size > 1
                    state.update { current ->
                        current.copy(
                            youtubeEpisodes = true,
                            details = current.details?.copy(seasonNumbers = numbers.takeIf { youtubeSeasons } ?: listOf(1)),
                        )
                    }
                    val opened = openedVideo(found)
                    showSeason(opened?.first ?: if (youtubeSeasons) numbers.first() else 1)
                    opened?.let { (season, number) -> state.update { it.copy(resumeSeason = season, resumeEpisode = number) } }
                }
            }
            launch {
                val resolved = resolver.watchByExtKey(extKeyOf(found), serial = true)
                    .ifEmpty { resolver.watchByExtKey(extKeyOf(found), serial = false) }
                val details = state.value.details ?: return@launch
                val options = runCatching { watchOptions.resolve(details, resolved) }.getOrDefault(emptyList())
                    .filter { it.launch != null }
                    // Плейлист на YouTube — кнопкою «YouTube», а не назвою каналу, яку сервіс
                    // бере з плейлиста («РЕАЛІТІ-ШОУ ДЛЯ ЖІНОК» — це не сервіс).
                    .map { if (it.launch?.packageName in setOf(YOUTUBE_TV, YOUTUBE)) it.copy(label = "YouTube") else it }
                if (options.isNotEmpty()) state.update { it.copy(services = options) }
            }
            val playlist = runCatching { plus1.youtubePlaylist(extKeyOf(found)) }.getOrNull() ?: return@launch
            state.update {
                it.copy(
                    youtube = WatchOption(
                        label = "YouTube",
                        kind = WatchOption.OptionKind.DIRECT,
                        launch = LaunchAction(packageName = YOUTUBE_TV, uri = "https://www.youtube.com/playlist?list=$playlist"),
                        audio = "uk",
                    ),
                )
            }
        }
    }

    /**
     * Плейлист мовника — у YouTube. Порядок той, що вимагає політика проєкту: офіційний
     * YouTube для ТБ, звичайний YouTube, і лише тоді системне «чим відкрити».
     */
    fun openYoutube(option: WatchOption) {
        val uri = option.launch?.uri ?: return
        // Кнопка сервісу (MEGOGO, Sweet.TV) — своїм застосунком, а не ютубом.
        val pkg = option.launch?.packageName.orEmpty()
        if (pkg.isNotBlank() && pkg != YOUTUBE_TV && pkg != YOUTUBE) {
            if (!apps.launchIntentUri(uri, pkg)) state.update { it.copy(message = context.getString(R.string.cinema_open_failed_named, option.label)) }
            return
        }
        val launched = listOf(YOUTUBE_TV, YOUTUBE).any { pkg -> apps.launchIntentUri(uri, pkg) } || apps.launchIntentUri(uri, null)
        if (!launched) state.update { it.copy(message = context.getString(R.string.cinema_no_youtube_app)) }
    }

    /** Чи віддає потік хоч плейлист за кілька секунд — інакше плеєр упаде з тайм-аутом. */
    private suspend fun streamAlive(url: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            com.likhtar.home.core.data.http(url, STREAM_CHECK_MS) { c ->
                c.responseCode in 200..399 && c.inputStream.use { it.read(ByteArray(64)) > 0 }
            }
        }.onFailure { android.util.Log.w("LikhtarReality", "потік не перевірився: $url", it) }
            .onSuccess { if (!it) android.util.Log.w("LikhtarReality", "потік порожній: $url") }
            .getOrDefault(false)
    }

    /**
     * Повернулись із YouTube — пропонуємо наступну серію.
     *
     * Чи серія дограла, YouTube не каже: і по кінці, і на BACK він повертає той самий
     * RESULT_CANCELED без даних  запуском «на результат»). Лишається час:
     * пробули ≥90% тривалості — майже напевно дограла, наступна сама через 5 с. Менше, але
     * понад 5 хв — могли продовжити з середини (YouTube пам'ятає позицію) чи перемотати:
     * картка без відліку, лише на OK. Коротше — відкрили не те, нічого не пропонуємо.
     */
    fun onReturnedFromYoutube() {
        val l = launched ?: return
        launched = null
        val elapsed = android.os.SystemClock.elapsedRealtime() - l.atMs
        val ended = l.seconds > 0 && elapsed >= l.seconds * 900L
        if (!ended && elapsed < NEXT_OFFER_MS) return
        if (state.value.season != l.season) return
        val inSeason = state.value.episodes.firstOrNull { it.number == l.number + 1 }
        if (inSeason != null) {
            state.update { it.copy(nextUp = inSeason, nextUpSeason = l.season, nextUpAuto = ended) }
            return
        }
        // Остання серія сезону — перша серія наступного (сезони — окремі плейлисти, [youtubeSeasons]).
        if (!youtubeSeasons) return
        val season = youtubeVideos.map { it.season }.filter { it > l.season }.minOrNull() ?: return
        val first = youtubeVideos.firstOrNull { it.season == season } ?: return
        val episode = Episode(
            number = 1,
            title = episodeTitle(first.title, state.value.details?.item?.title.orEmpty()),
            stillPath = first.image.takeIf { it.isNotBlank() },
            airDate = first.date,
        )
        state.update { it.copy(nextUp = episode, nextUpSeason = season, nextUpAuto = ended) }
    }

    fun playNext() {
        val next = state.value.nextUp ?: return
        val season = state.value.nextUpSeason
        state.update { it.copy(nextUp = null) }
        // Наступний сезон — спершу перемкнути вкладку: серія адресується номером у сезоні.
        if (season > 0 && season != state.value.season) showSeason(season)
        watch(next)
    }

    /** Повідомлення показано (Notice) — прибрати, щоб те саме могло прийти знову. */
    fun clearMessage() = state.update { it.copy(message = null) }

    fun cancelNext() = state.update { it.copy(nextUp = null) }

    /** Остання відкрита серія твору з «Продовжити»: сезон і номер у ньому. */
    private suspend fun openedVideo(found: RealityItem): Pair<Int, Int>? {
        val key = extKeyOf(found)
        val id = runCatching { showProgress.continueShows() }.getOrDefault(emptyList())
            .firstOrNull { it.extKey == key }?.episode ?: return null
        val video = youtubeVideos.firstOrNull { it.id == id } ?: return null
        val season = if (youtubeSeasons) video.season else 1
        val inSeason = if (youtubeSeasons) youtubeVideos.filter { it.season == season } else youtubeVideos
        return season to inSeason.indexOf(video) + 1
    }

    /** Підпис сезону для другої сцени: власна назва Телепорталу, а не вигаданий номер. */
    fun seasonLabel(number: Int): String =
        (if (state.value.youtubeEpisodes) (if (youtubeSeasons) context.getString(R.string.cinema_season_n, number) else "YouTube") else null)
            ?: state.value.seasons.getOrNull(number - 1)?.title
            ?: plus1Seasons.getOrNull(number - 1)?.title
            ?: context.getString(R.string.cinema_season_n, number)

    fun showSeason(number: Int) {
        val current = item ?: return
        if (state.value.youtubeEpisodes) {
            state.update { st ->
                st.copy(
                    season = number,
                    episodes = youtubeVideos.filter { !youtubeSeasons || it.season == number }.mapIndexed { index, v ->
                        Episode(
                            number = index + 1,
                            // «ВОЄННА ДРАМА 2026! … Серіал Живий 3,4 серії» → «Серії 3–4».
                            title = episodeTitle(v.title, state.value.details?.item?.title.orEmpty()),
                            stillPath = v.image.takeIf { it.isNotBlank() },
                            airDate = v.date,
                        )
                    },
                )
            }
            return
        }
        state.update { it.copy(season = number, episodes = emptyList()) }
        current.plus1?.let { showPlus1Season(number) } ?: showTeleportalSeason(number)
    }

    /**
     * Випуски — від першого до свіжого, як серії в Кінозалі: портали віддають навпаки, і
     * свіжий випуск ставав першим, а перший — останнім. Розвертаємо за
     * датами країв списку, порядок усередині — порталу: два випуски на день мають одну дату.
     */
    private fun <T> oldestFirst(raw: List<T>, date: (T) -> String): List<T> =
        if (raw.size > 1 && date(raw.first()) > date(raw.last())) raw.reversed() else raw

    private fun showTeleportalSeason(number: Int) {
        val show = item?.teleportal ?: return
        val season = state.value.seasons.getOrNull(number - 1) ?: return
        viewModelScope.launch {
            val raw = oldestFirst(runCatching { teleportal.episodes(show, season) }.getOrDefault(emptyList())) { it.date }
            hashes = raw.mapIndexed { index, episode -> index + 1 to episode.hash }.toMap()
            state.update { current ->
                if (current.season != number) return@update current
                current.copy(
                    episodes = raw.mapIndexed { index, episode ->
                        Episode(
                            number = index + 1,
                            title = episodeTitle(episode.title, show.name),
                            // Великий кадр, коли він уже приїхав: сервер добирає його
                            // плеєрною ручкою й доганяє у фон.
                            stillPath = episode.imageBigPath.ifBlank { episode.imagePath }
                                .takeIf { it.isNotBlank() },
                            airDate = episode.date,
                            runtimeMinutes = episode.durationSeconds / 60,
                        )
                    },
                    message = if (raw.isEmpty()) context.getString(R.string.cinema_season_no_episodes) else null,
                )
            }
            if (state.value.season == number) applyPortalResume(number)
        }
    }

    private fun showPlus1Season(number: Int) {
        val season = plus1Seasons.getOrNull(number - 1) ?: return
        viewModelScope.launch {
            val raw = oldestFirst(runCatching { plus1.episodes(season) }.getOrDefault(emptyList())) { it.date }
            hashes = raw.mapIndexed { index, episode -> index + 1 to episode.id }.toMap()
            state.update { current ->
                if (current.season != number) return@update current
                current.copy(
                    episodes = raw.mapIndexed { index, episode ->
                        Episode(
                            number = index + 1,
                            title = episode.title,
                            stillPath = episode.imageBigPath.takeIf { it.isNotBlank() },
                            airDate = episode.date,
                            runtimeMinutes = episode.durationSeconds / 60,
                        )
                    },
                    message = if (raw.isEmpty()) context.getString(R.string.cinema_season_no_episodes) else null,
                )
            }
            if (state.value.season == number) applyPortalResume(number)
        }
    }

    /** Головна дія шапки: для шоу — перший випуск, для набору — діплінк у застосунок. */
    fun watchMain() {
        val current = item ?: return
        if (current.id.startsWith("service/")) {
            watchOurs(current)
            return
        }
        if (current.teleportal == null) {
            openLink(current)
            return
        }
        state.value.episodes.firstOrNull()?.let(::watch)
    }

    fun watch(episode: Episode) {
        if (state.value.youtubeEpisodes) {
            shownVideos.getOrNull(episode.number - 1)?.let { v ->
                state.update { it.copy(resumeSeason = it.season, resumeEpisode = episode.number, nextUp = null) }
                launched = Launched(state.value.season, episode.number, android.os.SystemClock.elapsedRealtime(), v.seconds)
                openYoutube(WatchOption(label = "YouTube", kind = WatchOption.OptionKind.DIRECT,
                    launch = LaunchAction(packageName = YOUTUBE_TV, uri = v.url)))
                // Та сама вісь, що для випусків порталів: позиції немає (0 — «плеєр не
                // сказав»), але проєкт стає в «Продовжити», а серія — відомою.
                val current = item ?: return@let
                viewModelScope.launch {
                    showProgress.save(
                        extKey = extKeyOf(current), episode = v.id, season = v.season.toString(),
                        // Позиція книги приходить без назви — назва в шапці сторінки.
                        title = state.value.details?.item?.title?.ifBlank { null } ?: current.title,
                        episodeTitle = episode.title,
                        positionSeconds = 0, durationSeconds = 0,
                    )
                }
            }
            return
        }
        val hash = hashes[episode.number] ?: return
        val current = item ?: return
        if (current.teleportal == null && current.plus1 == null) return
        // Те саме, що для YouTube: запущений випуск — поточний, позначки й фокус по ньому.
        state.update { it.copy(resumeSeason = it.season, resumeEpisode = episode.number) }
        // Попередню відмову прибираємо на вході: інакше повідомлення про минулий випуск
        // висить над наступним, який цілком може грати.
        state.update { it.copy(message = null) }
        viewModelScope.launch {
            // Продовжуємо з тієї секунди, де зупинились: рядок «Продовжити» приходить із
            // тим самим хешем випуску, тож позиція знаходиться без жодних домислів.
            val resumeMs = runCatching { showProgress.continueShows() }.getOrDefault(emptyList())
                .firstOrNull { it.episode == hash }
                ?.positionSeconds
                ?.times(1000L)
                ?: 0L
            // Посилання 1+1 живе недовго — у токені мітка часу, — тому питаємо його
            // перед самим запуском і ніде не кешуємо.
            val playbackResult = if (current.plus1 != null) plus1.player(hash) else teleportal.player(hash)
            val url = playbackResult.url
            // Портал віддав посилання, але сам потік не відповідає (Телепортал: плеєр
            // стартував і падав з тайм-аутом) — або посилання немає зовсім. Тоді, якщо є
            // офіційний плейлист мовника, одразу відкриваємо YouTube, а не лишаємо помилку.
            if (url == null || !streamAlive(url)) {
                val yt = state.value.youtube
                if (yt != null) {
                    state.update { it.copy(message = context.getString(R.string.cinema_portal_down_youtube)) }
                    openYoutube(yt)
                } else {
                    state.update {
                        it.copy(message = if (url == null) reasonText(playbackResult.reason) else context.getString(R.string.cinema_portal_no_video))
                    }
                }
                return@launch
            }
            val seasonKey = state.value.seasons.getOrNull(state.value.season - 1)?.slug
                ?: plus1Seasons.getOrNull(state.value.season - 1)?.id.orEmpty()
            fun playing(e: Episode, h: String) = PlayingEpisode(
                extKey = extKeyOf(current), episode = h, season = seasonKey,
                title = current.title, episodeTitle = e.title,
            )
            // Черга плеєра — від цього випуску до свіжіших (список уже від першого, див.
            // oldestFirst). Без неї після випуску не вмикалось нічого.
            val ordered = state.value.episodes.filter { it.number >= episode.number }
            val kind = if (current.plus1 != null) "plus1" else "teleportal"
            val queued = ordered.mapNotNull { e ->
                val h = hashes[e.number] ?: return@mapNotNull null
                teleportal.goUrl(kind, h)?.let { Triple(e, h, it) }
            }.takeIf { it.size > 1 && it.first().second == hash }.orEmpty()
            val launched = playback.play(
                PlayRequest(
                    uri = url,
                    // Телепортал віддає HLS — той самий mime, що й плейлист у Кінозалі.
                    mime = "audio/x-mpegurl",
                    packageName = settings.settings.first().videoPlayerPackage,
                    resumeMs = resumeMs,
                    // Сеанс без TMDB-id: вісь прогресу для випусків зовнішня — ключ
                    // проєкту плюс хеш випуску. Раніше тут стояв `null`, бо писати такий
                    // випуск було нікуди; тепер сервер приймає його тією самою ручкою.
                    title = null,
                    episode = playing(episode, hash).copy(queue = queued.map { (e, h, _) -> playing(e, h) }),
                    single = queued.firstOrNull()?.third.orEmpty(),
                    queue = queued.mapIndexed { i, (e, _, go) -> QueueEntry(i + 1, e.title, go) },
                ),
            )
            if (!launched) state.update { it.copy(message = context.getString(R.string.cinema_player_open_failed)) }
        }
    }

    /**
     * Позиція сервісу: шукаємо українську в наших джерелах за назвою — той самий живий
     * пошук, що на сторінці тайтла. Не знайшли — кажемо про це; кнопка сервісу поруч.
     */
    private fun watchOurs(current: RealityItem) {
        val details = state.value.details ?: return
        viewModelScope.launch {
            state.update { it.copy(searching = true, message = null) }
            val result = runCatching {
                streams.playByName(details.item.copy(tmdbId = 0), 1, 1, unscripted = true)
            }.getOrNull()
            state.update { it.copy(searching = false) }
            if (result == null || !result.ok) {
                state.update { it.copy(message = context.getString(R.string.cinema_reality_no_ua_source)) }
                return@launch
            }
            val launched = playback.play(
                PlayRequest(
                    uri = result.url,
                    mime = "video/*",
                    packageName = settings.settings.first().videoPlayerPackage,
                    resumeMs = 0,
                    title = null,
                    episode = PlayingEpisode(
                        extKey = extKeyOf(current), episode = "1", season = "1",
                        title = details.item.title, episodeTitle = result.name,
                    ),
                    ref = result.ref,
                    source = result.source,
                ),
            )
            if (!launched) state.update { it.copy(message = context.getString(R.string.cinema_player_open_failed)) }
        }
    }

    private fun openLink(current: RealityItem) {
        viewModelScope.launch {
            if (!apps.openLink(current.url, current.packageName)) {
                state.update { it.copy(message = context.getString(R.string.cinema_app_not_installed)) }
            }
        }
    }

    private fun reasonText(reason: TeleportalReason?): String = when (reason) {
        TeleportalReason.GEO -> context.getString(R.string.cinema_reason_geo)
        TeleportalReason.NO_STREAM -> context.getString(R.string.cinema_reason_no_stream)
        TeleportalReason.SERVER -> context.getString(R.string.cinema_reason_server)
        else -> context.getString(R.string.cinema_reason_other)
    }

    companion object {
        fun route(id: String): RealityShowRoute = RealityShowRoute(id)

        private val YOUTUBE_TV = com.likhtar.home.core.model.StreamingService.YOUTUBE.packages[0]
        private const val NEXT_OFFER_MS = 5 * 60_000L
        private const val STREAM_CHECK_MS = 6_000
        private val YOUTUBE = com.likhtar.home.core.model.StreamingService.YOUTUBE.packages[1]

        /**
         * Ext-ключ прогресу → id позиції розділу.
         *
         * Сервер адресує проєкт як `teleportal:<channel>/<slug>`, а в нас той самий проєкт
         * зветься `show/<channel>/<slug>` — різниця лише в префіксі, бо в «Реаліті» бувають
         * тільки не-серіали. `1plus1:<id>` повертаємо як є: своїх позицій 1+1 у нас ще немає,
         * і вигадувати для них шлях означало б обіцяти екран, якого не існує.
         */
        fun idOfExtKey(extKey: String, serial: Boolean = false): String? =
            com.likhtar.home.core.catalog.realityRouteId(extKey, serial)

        /** Ключ прогресу для позиції: проєкт, а не випуск. */
        internal fun extKeyOf(item: RealityItem): String {
            // Локальні копії, а не smart cast: поля живуть в іншому модулі, і компілятор
            // не має права вважати їх незмінними — той самий випадок, що з `programme.image`
            // у ТВ Ефірі.
            val plus1 = item.plus1
            val teleportal = item.teleportal
            return when {
                plus1 != null -> "1plus1:${plus1.id}"
                teleportal != null -> "teleportal:${teleportal.channelSlug}/${teleportal.projectSlug}"
                item.id.startsWith("youtube/") -> "nashe:" + item.id.removePrefix("youtube/")
                item.id.startsWith("service/") -> item.id.removePrefix("service/")
                else -> item.id
            }
        }

        /**
         * Назва випуску без назви самого шоу.
         *
         * Телепортал віддає її повним рядком — «Україна має талант. 2026. Випуск 4 від
         * 13.09.2026», — і в списку, де назва шоу вже стоїть угорі, вона повторюється в
         * кожному рядку. Відрізаємо все до «Випуск», а коли такого слова немає — лишаємо
         * як є: вигадувати номер із нічого гірше за довгий рядок.
         */
        internal fun episodeTitle(raw: String, show: String): String =
            com.likhtar.home.core.catalog.showEpisodeTitle(raw, show)
    }
}
