package com.likhtar.home.core.catalog

import kotlinx.coroutines.flow.first
import android.content.Intent
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Що саме зараз грає — щоб на поверненні було кому записати таймкод.
 *
 * Тримаємо копію потрібних полів, а не сам [CatalogItem]: між запуском плеєра й поверненням
 * може пройти година, екран за цей час перестворюється, і жива посилання на модель екрана
 * тут була б витоком.
 */
data class PlayingTitle(
    val item: CatalogItem,
    val season: Int,
    val episode: Int,
)

/**
 * Випуск порталу, який зараз грає.
 *
 * Окремо від [PlayingTitle], бо в нього немає TMDB-id за природою: вісь зовнішня, ключ —
 * проєкт плюс хеш випуску. Саме через це раніше «Реаліті» грало без сеансу взагалі:
 * писати такий випуск було нікуди, і синтетичний id засмітив би список фільмів.
 */
data class PlayingEpisode(
    val extKey: String,
    val episode: String,
    val season: String,
    val title: String,
    val episodeTitle: String,
    /**
     * Випуски черги плеєра за індексом [PlayRequest.queue] (перший — стартовий): за адресою
     * з результату видно, на якому вийшли, і прогрес пишеться саме йому.
     */
    val queue: List<PlayingEpisode> = emptyList(),
)

/** Прохання відтворити: що, чим і з якої секунди. */
data class PlayRequest(
    val uri: String,
    val mime: String,
    val packageName: String,
    /** Звідки продовжити, мс. 0 — з початку. */
    val resumeMs: Long,
    val title: PlayingTitle?,
    /** Заповнене замість [title] для випуску порталу: у нього інша вісь прогресу. */
    val episode: PlayingEpisode? = null,
    /**
     * Джерело, яке віддав сервер у `/api/play`. Порожньо — грали не через нього
     * (кнопка «Дивитись» бере потік прямо з агрегатора, там `ref` немає).
     */
    val ref: String = "",
    val source: String = "",
    /** Назва в плеєрі, коли [title] немає (файл медіатеки поза TMDB): без неї — ім'я з адреси. */
    val displayName: String = "",
    /**
     * Роздільність потоку. `0` — не знаємо.
     *
     * Окремо від [ref], бо `ref` є лише на двох шляхах із трьох: кнопка «Дивитись» бере
     * потік прямо з агрегатора. Якість же відома на всіх — ми її показуємо людині, — і
     * сервер бере спершу її, а `ref` лишає як спосіб дізнатися, ЯКА саме роздача.
     */
    val quality: Int = 0,
    /** Субтитри від сервера: підпис → адреса. Плеєр покаже їх доріжками. */
    val subs: List<Pair<String, String>> = emptyList(),
    /**
     * Сама серія, коли [uri] — плейлист сезону. Плеєр, що m3u не бере (Just+), отримає
     * її разом із [queue] і сам перемикатиме серії.
     */
    val single: String = "",
    val queue: List<QueueEntry> = emptyList(),
    /** Інші озвучки й джерела цієї ж серії: підпис → адреса. Just+ перемикає їх у плеєрі. */
    val variants: List<Pair<String, String>> = emptyList(),
    /** Те саме для кожної серії [queue], за індексом: у Just+ панель «Якість» — на кожній серії. */
    val queueVariants: List<List<Pair<String, String>>> = emptyList(),
)

/**
 * Запуск зовнішнього плеєра «на результат».
 *
 * Навіщо окрема деталь: позицію відтворення може віддати лише сама Activity — через
 * `startActivityForResult`. ViewModel цього не вміє, а репозиторій застосунків тримає
 * `ApplicationContext` і Activity не має взагалі. Тому ViewModel кладе сюди прохання,
 * `LikhtarApp` його підхоплює своїм launcher'ом, а результат повертається сюди ж.
 *
 * Що вміють плеєри: Just Player, MX Player і VLC приймають стартову позицію в extras і
 * повертають кінцеву при виході. Netflix, MEGOGO чи YouTube не повернуть нічого — і не
 * повернуть ніколи, це закриті чужі застосунки. Тобто таймкоди працюють рівно для тих
 * потоків, посилання на які даємо ми самі.
 */
@Singleton
class PlaybackController @Inject constructor(
    private val progress: WatchProgressRepository,
    private val showProgress: ShowProgressRepository,
    private val ownTorrentServer: OwnTorrentServer,
    private val config: CatalogConfig,
    private val settings: com.likhtar.home.core.data.SettingsRepository,
    private val history: com.likhtar.home.core.data.RecentHistoryRepository,
) {
    /**
     * Що плеєр має знати про нас для «Паузи, що знає»: адреса сервера й профіль. Just+ сам
     * просить `/api/pause` із тими `id`/`season`/`episode`, які вже отримує.
     */
    suspend fun companionExtras(): Map<String, String> = buildMap {
        // Лише домашня збірка: публічна віддавала будь-якому плеєру адресу шлюзу й профіль
        //. `homeOnlyUrl` у публічній — null, і тоді нічого не передаємо.
        val server = config.homeOnlyUrl() ?: return@buildMap
        put("likhtar.server", server)
        put("likhtar.profile", settings.settings.first().activeProfileId)
    }

    /**
     * Прогрес сеансу записано на сервер. Сторінка тайтла слухає й перечитує «продовжити»:
     * ON_RESUME приходить раніше, ніж записи доходять, і сторінка лишалась на старій серії.
     */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val _requests = MutableSharedFlow<PlayRequest>(extraBufferCapacity = 1)
    val requests: SharedFlow<PlayRequest> = _requests.asSharedFlow()

    /**
     * Що зараз відкрито в плеєрі.
     *
     * Переживає смерть екрана, але не смерть процесу: якщо систему змусить вивантажити
     * лаунчер, поки триває фільм, таймкод цього сеансу втрачається. Ловити цей випадок
     * можна лише власним плеєром, тому просто визнаємо його.
     */
    private var playing: PlayingTitle? = null

    /** Випуск порталу цього сеансу. Живе поруч із [playing], бо вісь прогресу інша. */
    private var playingEpisode: PlayingEpisode? = null

    /**
     * Яким плеєром грає цей сеанс.
     *
     * Потрібно у звіті на сервер: він упорядковує плеєри за широтою claim'ів, а який із
     * них справді тягне цей файл — видно лише тут. Порожньо означає системний вибір: ми
     * не знаємо, що людина там натиснула.
     */
    private var playingWith: String = ""

    /** Джерело сеансу — сервер бере за ним якість роздачі у звіті про спробу. */
    private var playingRef: String = ""
    private var playingSource: String = ""
    private var playingQuality: Int = 0

    /** Черга серій сеансу й варіанти кожної — щоб за адресою з результату знайти серію. */
    private var playingQueue: List<QueueEntry> = emptyList()
    private var playingQueueVariants: List<List<Pair<String, String>>> = emptyList()

    fun play(request: PlayRequest): Boolean {
        playing = request.title
        playingEpisode = request.episode
        playingWith = request.packageName
        playingRef = request.ref
        playingSource = request.source
        playingQuality = request.quality
        val ts = ownTorrentServer.url
        val fix = { u: String -> retargetTorrent(u, ts) }
        playingQueue = request.queue.map { it.copy(url = fix(it.url)) }
        playingQueueVariants = request.queueVariants.map { v -> v.map { (label, u) -> label to fix(u) } }
        return _requests.tryEmit(
            if (ts == null) request else request.copy(
                uri = fix(request.uri),
                single = fix(request.single),
                queue = request.queue.map { it.copy(url = fix(it.url)) },
                variants = request.variants.map { (label, u) -> label to fix(u) },
                queueVariants = request.queueVariants.map { v -> v.map { (label, u) -> label to fix(u) } },
            ),
        )
    }

    /** Плеєр закрився. Повідомляє [LikhtarApp], розібравши extras результату. */
    suspend fun onPlayerClosed(positionMs: Long, durationMs: Long, endedUri: String? = null) {
        try {
            recordClosed(positionMs, durationMs, endedUri)
        } finally {
            _saved.tryEmit(Unit)
        }
    }

    private suspend fun recordClosed(positionMs: Long, durationMs: Long, endedUri: String?) {
        // Випуск порталу пишемо в зовнішню вісь: ключ проєкту плюс хеш випуску, секунди.
        // Нуль позиції на сервері відоме місце не затирає, тому окремої сторожі тут не
        // треба — досить не вигадувати позицію самим.
        playingEpisode?.let { start ->
            playingEpisode = null
            // Черга випусків (Just+ перемикає сам): пройдені — переглянуті, позиція — тому,
            // на якому вийшли. Номери в черзі — порядкові, від 1 (стартовий).
            val reached = reachedEpisode(playingQueue, playingQueueVariants, 1, endedUri)
            playingQueue = emptyList()
            playingQueueVariants = emptyList()
            reached?.passed?.forEach { n ->
                val e = start.queue.getOrNull(n - 1) ?: return@forEach
                showProgress.save(
                    extKey = e.extKey, episode = e.episode, season = e.season, title = e.title,
                    episodeTitle = e.episodeTitle, positionSeconds = 0, durationSeconds = 0, finished = true,
                )
            }
            val episode = reached?.current?.let { start.queue.getOrNull(it - 1) } ?: start
            showProgress.save(
                extKey = episode.extKey,
                episode = episode.episode,
                season = episode.season,
                title = episode.title,
                episodeTitle = episode.episodeTitle,
                positionSeconds = (positionMs / 1000).toInt(),
                durationSeconds = (durationMs / 1000).toInt(),
            )
            return
        }
        val started = playing ?: run {
            // Результат прийшов, а згадки про сеанс немає: процес лаунчера встиг померти,
            // поки грав плеєр. Мовчати тут не можна — це рівно та поломка, яку в логах
            // не відрізнити від «плеєр не відповів».
            Log.i(TAG, "результат без сеансу: pos=$positionMs dur=$durationMs")
            return
        }
        playing = null
        // Серії, які плеєр перемкнув сам (черга Just+): результат каже, на якій зупинились, —
        // адресою. Усі серії від стартової до неї — переглянуті, позиція — тієї, на якій
        // вийшли. Досі все писалось у стартову серію: людина додивилась 5 серій поспіль,
        // а на сторінці не було жодної позначки.
        // Джерело, на якому вийшли, — у пам'ять: людина могла перемкнути озвучку в самому
        // плеєрі, і наступна серія з лаунчера має стартувати з нього ж, а не з давнього.
        // Свої файли (медіатека) — не джерело сервера: 127.0.0.1 чи NAS у пам'ять не кладемо.
        endedUri?.takeIf { playingSource != LOCAL_SOURCE }?.let { uri -> runCatching { java.net.URL(uri).authority }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?.let { history.rememberSource(it, settings.settings.first().activeProfileId) }
        val reached = reachedEpisode(playingQueue, playingQueueVariants, started.episode, endedUri)
        playingQueue = emptyList()
        playingQueueVariants = emptyList()
        reached?.passed?.forEach { episode -> progress.finished(started.item, started.season, episode) }
        val title = if (reached != null) started.copy(episode = reached.current) else started
        if (positionMs < MIN_POSITION_MS) {
            // Нуль — плеєр промовчав; кілька секунд — людина ввімкнула й передумала. Записати
            // і те, і те однаково шкідливо: такий запис стер чесні 7:32 і поставив
            // замість них 3 секунди, бо плеєр проігнорував стартову позицію й почав з нуля.
            Log.i(TAG, "позиція не годиться в закладку (${positionMs}мс) для ${title.item.title}")
            return
        }
        progress.position(
            item = title.item,
            season = title.season,
            episode = title.episode,
            positionMs = positionMs,
            durationMs = durationMs,
        )
        // Сервер сам пише кожен ЗАПУСК, але не знає, чи з нього щось вийшло. Ось той
        // єдиний момент, коли ми це знаємо напевно: плеєр повернувся з позицією.
        progress.reportAttempt(
            item = title.item,
            playedMs = positionMs,
            result = "player_returned player=${playingWith.ifBlank { "system_choice" }}",
            player = playingWith,
            ref = playingRef,
            source = playingSource,
            quality = playingQuality,
        )
    }

    companion object {
        /** [PlayRequest.source] для файлів медіатеки. */
        const val LOCAL_SOURCE = "local"
        private const val TAG = "LikhtarPlayback"

        /** Нижче цього закладка не має сенсу: це не «зупинився», а «зазирнув». */
        private const val MIN_POSITION_MS = 60_000L
    }
}

/**
 * Дописує в намір стартову позицію в тих ключах, які розуміють поширені плеєри.
 *
 * Ключі різні й несумісні: MX Player (і Just Player за ним) читає `position` у
 * мілісекундах, VLC — `extra_position`. Кладемо обидва: зайвий extra плеєр просто
 * проігнорує, а вгадувати за іменем пакета — це список, який застаріє з першим оновленням.
 */
fun Intent.putResumePosition(resumeMs: Long): Intent {
    if (resumeMs <= 0L) return this
    putExtra("position", resumeMs.toInt())
    putExtra("extra_position", resumeMs)
    // ViMu не розуміє жодного з двох попередніх ключів — запуск із
    // resume=452272 починався з нуля, поки тут не було `startfrom`. Він же гасить питання
    // «продовжити?»: рішення вже прийняте нами, і зайвий діалог на пульті лише заважає.
    putExtra("startfrom", resumeMs.toInt())
    return this
}

/** Позиція й тривалість із результату плеєра, мс. Нулі означають «плеєр не сказав». */
fun Intent?.playbackResult(resultCode: Int): Pair<Long, Long> {
    val extras = this?.extras ?: return 0L to 0L
    return playbackResultOf(extras.keySet().associateWith { extras.get(it) }, resultCode)
}

/**
 * Розбір відповіді плеєра — окремо від `Intent`, щоб його можна було перевірити тестом.
 *
 * Тут живуть усі три способи помилитися: ключі в різних плеєрів різні
 * (`position` проти `extra_position`), число приходить то `Int`, то `Long`, а повний
 * перегляд MX-сумісні плеєри позначають окремим полем — і тоді позиція має дорівнювати
 * тривалості, інакше «додивився до кінця» виглядало б як «зупинився на першій секунді».
 */
fun playbackResultOf(extras: Map<String, Any?>, resultCode: Int = 0): Pair<Long, Long> {
    val position = extras.millis("position", "extra_position")
    val duration = extras.millis("duration", "extra_duration")
    // «Додивився до кінця» кожен плеєр каже по-своєму: MX-сумісні кладуть в extras `end_by`,
    // ViMu не кладе нічого й повідомляє це власним кодом результату.
    val completed = extras["end_by"] == "playback_completion" || resultCode == VIMU_COMPLETED
    return if (completed && duration > 0L) duration to duration else position to duration
}

/** Код результату ViMu для «дограв до кінця». Штатні RESULT_OK і RESULT_CANCELED — це -1 і 0. */
const val VIMU_COMPLETED = 1

private fun Map<String, Any?>.millis(vararg keys: String): Long {
    for (key in keys) {
        val millis = (this[key] as? Number)?.toLong() ?: continue
        if (millis > 0L) return millis
    }
    return 0L
}

/** Де зупинився плеєр у черзі: [passed] — переглянуті серії до [current]. */
data class QueueReach(val passed: List<Int>, val current: Int)

/**
 * Серія, на якій плеєр вийшов, за адресою з результату ([endedUri] — `data` результату
 * Just+). Шукаємо серед адрес черги й усіх варіантів кожної серії: людина могла перемкнути
 * озвучку, і тоді адреса — варіанта. `null` — не знайшли або вийшли на стартовій серії.
 */
fun reachedEpisode(
    queue: List<QueueEntry>,
    queueVariants: List<List<Pair<String, String>>>,
    startEpisode: Int,
    endedUri: String?,
): QueueReach? {
    if (endedUri.isNullOrBlank() || queue.isEmpty()) return null
    val at = queue.indices.firstOrNull { i ->
        queue[i].url == endedUri || queueVariants.getOrNull(i).orEmpty().any { it.second == endedUri }
    } ?: return null
    val start = queue.indexOfFirst { it.episode == startEpisode }.takeIf { it >= 0 } ?: return null
    if (at <= start) return null
    return QueueReach(passed = (start until at).map { queue[it].episode }, current = queue[at].episode)
}
