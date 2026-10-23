package com.likhtar.home.core.tv

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/** Проєкт Телепорталу: шоу або серіал. */
data class TeleportalItem(
    val name: String,
    val channelSlug: String,
    val projectSlug: String,
    val imagePath: String,
    /** Банер 1440×550. Порожньо буває лише перші хвилини після перезапуску сервера. */
    val imageBigPath: String = "",
    /**
     * Чистий кадр 1440×810 із самого відео — без промо-напису.
     *
     * Банер малює редакція, і на ньому вмонтований текст («19:00 щосуботи»), який на
     * картці 16:9 ріжеться посеред слова. Цей кадр — автоматичний знімок приблизно на 20%
     * тривалості, тобто просто сцена. Є у 85 шоу зі 104; де немає — падаємо на банер.
     */
    val framePath: String = "",
    val serial: Boolean,
    /**
     * Жанри проєкту. У самого Телепорталу їх немає — таблицю веде наш сервер руками, бо
     * автоматика по описах давала фальшиве покриття: SEO-тексти забиті словами «шоу» й
     * «зірки», і вікторина ставала «Талантами». Побачив на екрані дурницю — це рядок
     * таблиці, а не поломка коду.
     */
    val genres: List<String> = emptyList(),
    /** Дата останнього випуску, `2026-09-06`. Порожньо — свіжий сезон порожній (12 із 275). */
    val lastEpisode: String = "",
) {
    val id: String get() = "${if (serial) "serials" else "show"}/$channelSlug/$projectSlug"
}

/** Сезон разом із випусками — сторінка сезону віддає і те, і те одним запитом. */
data class TeleportalSeason(
    val title: String,
    val slug: String,
    val episodes: List<TeleportalEpisode> = emptyList(),
    /** Порядок створення (id Телепорталу): за ним сезони й спецвипуски стають за часом. 0 — невідомо. */
    val order: Int = 0,
)

data class TeleportalEpisode(
    val title: String,
    val hash: String,
    val imagePath: String,
    /**
     * Кадр 1440×810 з CDN Старлайту, не через наш проксі.
     *
     * З'являється не одразу: великий кадр знає лише плеєрна ручка, тож сервер опитує
     * перші тридцять випусків на місці, а решту доганяє у фон. Порожньо — беремо [imagePath]
     * і за кілька секунд великий приїде сам.
     */
    val imageBigPath: String = "",
    /** Дата ефіру, `2026-09-06`. Її має кожен випуск — на відміну від назви, де вона в 4%. */
    val date: String = "",
    /** Тривалість у секундах: контейнер віддає мілісекунди, ділить сервер. */
    val durationSeconds: Int = 0,
)

/** Сторінка проєкту: опис і сезони одним запитом. */
data class TeleportalProject(
    val title: String = "",
    val description: String = "",
    val seasons: List<TeleportalSeason> = emptyList(),
    /** Банер 1440×550 — той самий, що на картці. */
    val imageBig: String = "",
    /**
     * Широка обкладинка 1920×730, а НЕ знак назви.
     *
     * Ліва третина в ній навмисно порожня — під текст, — тому вона й іде кадром сторінки,
     * а не в смугу логотипа: у смузі 82dp із неї вийшла б пласка стрічка з обличчями.
     */
    val logo: String = "",
    /** Ведучі й судді, самі імена. Посилань немає: сторінки людини в нас не існує. */
    val persons: List<String> = emptyList(),
    val channelTitle: String = "",
    /** Рядок («12»), а не число — так його віддає Телепортал. */
    val age: String = "",
)

/**
 * Чому потік не дістався. Різниця не косметична: `GEO` означає «не буде», `SERVER` — «спробуйте
 * пізніше», і одна відповідь на обидва випадки змусила б людину чекати того, що не настане.
 */
enum class TeleportalReason {
    /** Телепортал відмовив звідусіль — це географія, чекати марно. */
    GEO,

    /** Не відповів наш контейнер — оце і є «спробуйте пізніше». */
    SERVER,

    /** Запис є, відео немає — та сама природа, що `hash = "0"`. */
    NO_STREAM,

    /** Відмовив з іншої причини. */
    REFUSED,

    ;

    companion object {
        fun of(raw: String?): TeleportalReason = when (raw) {
            "geo" -> GEO
            "server" -> SERVER
            "no_stream" -> NO_STREAM
            else -> REFUSED
        }
    }
}

/** Результат плеєрної ручки: або посилання, або названа причина відмови. */
data class TeleportalPlayback(
    val url: String? = null,
    val poster: String? = null,
    val reason: TeleportalReason? = null,
)

/**
 * Клієнт Телепорталу — через НАШ сервер, а не в контейнер напряму.
 *
 * Піднятий із історії (`0e3b2a2^`, 16.08.2026), коли розділ «Наше» переїхав на YouTube і
 * Телепортал прибрали. Відтоді змінилось одне, але головне: адреса. Раніше вона бралася з
 * `BuildConfig.TELEPORTAL_URL`, тобто була зашита у збірку; тепер приходить тим самим швом,
 * що й телепрограма, — з адреси, яку лаунчер знаходить обходом підмережі. Причина та сама,
 * що з MEGOGO-EPG: Телепорталу потрібні українські проксі, а чужа географія не має жити на
 * приставці. Зашиті IP швидко мертвіють.
 *
 * Сервер робить усе важке: обхід гео, розбір плеєра, проксі для зображень і HLS. Лаунчеру
 * лишається каталог і посилання на потік, яке він віддає системному плеєру.
 *
 * Шляхи — з контракту сервера під префіксом `/api/teleportal`. Три речі він зробив інакше, ніж
 * контейнер, і всі три спрощують клієнт: `hls` приходить повним URL (базу дописувати не
 * треба), картинки теж повними, а «Іноземні» сервер відсіює сам — наш фільтр за
 * `channelSlug == "international"` знятий, бо це властивість каталогу, а не екрана.
 */
@Singleton
class TeleportalClient @Inject constructor(
    private val server: LikhtarServer,
    private val http: TvHttp,
) {

    /**
     * Чи останній каталог прийшов зі збереженого зрізу.
     *
     * `stale: true` означає «показуємо вчорашнє, свіже ще будується»: каталог живе в
     * пам'яті сервера й наповнюється фоновим обходом три-чотири хвилини, а перезапуск
     * агрегатора для приставки виглядав як «розділ зник». Дані в такому разі показуємо —
     * порожнім ряд не робимо, — але кеш на них тримаємо короткий: свіже мусить прийти
     * саме, а не за півгодини.
     */
    @Volatile
    var catalogueStale: Boolean = false
        private set

    /** Каталог: шоу й серіали одним списком. «Іноземні» сервер уже відсіяв. */
    suspend fun catalogue(): List<TeleportalItem> {
        val base = server.homeOnlyUrl() ?: return emptyList()
        val body = get("$base/api/teleportal/catalog") ?: return emptyList()
        catalogueStale = runCatching { JSONObject(body).optBoolean("stale") }.getOrDefault(false)
        return parseCatalogue(body)
    }

    internal fun parseCatalogue(body: String): List<TeleportalItem> = runCatching {
        val array = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
        (0 until array.length()).mapNotNull { index ->
            val raw = array.optJSONObject(index) ?: return@mapNotNull null
            val channel = raw.optString("channel")
            val project = raw.optString("slug")
            val name = raw.optString("name")
            if (channel.isBlank() || project.isBlank() || name.isBlank()) return@mapNotNull null
            val genres = raw.optJSONArray("genres")
            TeleportalItem(
                name = name,
                channelSlug = channel,
                projectSlug = project,
                imagePath = raw.optString("image"),
                imageBigPath = raw.optString("image_big"),
                framePath = raw.optString("frame"),
                serial = raw.optBoolean("serial"),
                genres = (0 until (genres?.length() ?: 0)).mapNotNull { position ->
                    genres?.optString(position)?.takeIf { it.isNotBlank() }
                },
                lastEpisode = raw.optString("last_episode"),
            )
        }
    }.onFailure { Log.w(TAG, "Каталог Телепорталу не розібрався", it) }.getOrDefault(emptyList())

    /**
     * Сторінка проєкту: опис і сезони одним запитом.
     *
     * Сезон адресується `season` (це `seasonSlug`), і числового id у відповіді немає взагалі —
     * сервер прибрав його навмисно, бо сам двічі спіткнувся: з id Телепортал віддає `Not Found`.
     */
    suspend fun project(item: TeleportalItem): TeleportalProject? {
        val base = server.homeOnlyUrl() ?: return null
        val body = get(
            "$base/api/teleportal/seasons?channel=${enc(item.channelSlug)}" +
                "&slug=${enc(item.projectSlug)}&serial=${if (item.serial) 1 else 0}",
        ) ?: return null
        return parseProject(body)
    }

    internal fun parseProject(body: String): TeleportalProject = runCatching {
        val json = JSONObject(body)
        val array = json.optJSONArray("results")
        val seasons = (0 until (array?.length() ?: 0)).mapNotNull { index ->
            val raw = array?.optJSONObject(index) ?: return@mapNotNull null
            val slug = raw.optString("season")
            if (slug.isBlank()) return@mapNotNull null
            TeleportalSeason(title = raw.optString("title").ifBlank { slug }, slug = slug, order = raw.optInt("order"))
        }
        val persons = json.optJSONArray("persons")
        TeleportalProject(
            title = json.optString("title"),
            description = json.optString("description"),
            seasons = seasons,
            imageBig = json.optString("image_big"),
            logo = json.optString("logo"),
            persons = (0 until (persons?.length() ?: 0)).mapNotNull { index ->
                persons?.optString(index)?.takeIf { it.isNotBlank() }
            },
            channelTitle = json.optString("channel_title"),
            age = json.optString("age"),
        )
    }.getOrDefault(TeleportalProject())

    /** Випуски сезону. Скільки сервер відкинув (порожні або дублікати) — у полі `dropped`. */
    suspend fun episodes(item: TeleportalItem, season: TeleportalSeason): List<TeleportalEpisode> {
        val base = server.homeOnlyUrl() ?: return emptyList()
        val body = get(
            "$base/api/teleportal/episodes?channel=${enc(item.channelSlug)}" +
                "&slug=${enc(item.projectSlug)}&season=${enc(season.slug)}&serial=${if (item.serial) 1 else 0}",
        ) ?: return emptyList()
        return parseEpisodes(body)
    }

    internal fun parseEpisodes(body: String): List<TeleportalEpisode> {
        return runCatching {
            val array = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
            (0 until array.length()).mapNotNull { index ->
                val raw = array.optJSONObject(index) ?: return@mapNotNull null
                val hash = raw.optString("hash")
                // `hash = "0"` — випуск без запису (анонс, вилучене відео). Таких у сезоні
                // буває кілька, і саме вони роняли екран: сітка ключується хешем, а два
                // однакові ключі для Compose — виняток, а не дублікат.
                if (hash.isBlank() || hash == NO_STREAM) return@mapNotNull null
                TeleportalEpisode(
                    // `title` — повна назва випуску, `seriesTitle` — його номер у сезоні.
                    title = raw.optString("title").ifBlank { "Випуск ${raw.optString("seriesTitle")}" },
                    hash = hash,
                    imagePath = raw.optString("image"),
                    imageBigPath = raw.optString("image_big"),
                    date = raw.optString("date"),
                    durationSeconds = raw.optInt("duration"),
                )
            }.distinctBy { it.hash }
        }.onFailure { Log.w(TAG, "Випуски не розібралися", it) }.getOrDefault(emptyList())
    }

    /**
     * Посилання на потік — або причина відмови.
     *
     * Причину РОЗРІЗНЯЄМО навмисно: `geo` і `server` вимагають протилежних слів людині.
     * Побачивши голе «не вдалось», ми сказали б «спробуйте пізніше» — і людина чекала б
     * того, що не настане. Це та сама пастка, що з `readers:0` у торентах: сигнал у
     * відповіді був, бракувало лише його назвати.
     */
    /**
     * Адреса випуску для черги плеєра: сервер на неї відповідає 302 на свіже HLS у ту мить,
     * коли плеєр до неї дійшов (посилання порталів живуть недовго). [kind] — teleportal | plus1.
     */
    suspend fun goUrl(kind: String, id: String): String? =
        server.homeOnlyUrl()?.let { "$it/api/portal/go/$kind/${enc(id)}.m3u8" }

    suspend fun player(hash: String): TeleportalPlayback {
        val base = server.homeOnlyUrl() ?: return TeleportalPlayback(reason = TeleportalReason.SERVER)
        val body = get("$base/api/teleportal/player?hash=${enc(hash)}")
            ?: return TeleportalPlayback(reason = TeleportalReason.SERVER)
        return parsePlayer(body)
    }

    internal fun parsePlayer(body: String): TeleportalPlayback = runCatching {
        val json = JSONObject(body)
        if (!json.optBoolean("ok")) {
            return@runCatching TeleportalPlayback(reason = TeleportalReason.of(json.optString("reason")))
        }
        val hls = json.optString("hls")
        // Порожній `hls` при `ok:true` — той самий випадок, що «запис є, відео немає»:
        // вигадувати для нього окреме слово нема потреби.
        if (hls.isBlank()) {
            TeleportalPlayback(reason = TeleportalReason.NO_STREAM)
        } else {
            TeleportalPlayback(url = hls, poster = json.optString("poster").takeIf { it.isNotBlank() })
        }
    }.getOrDefault(TeleportalPlayback(reason = TeleportalReason.SERVER))

    private suspend fun get(url: String): String? = http.get(url)

    private companion object {
        const val TAG = "LikhtarTeleportal"

        const val NO_STREAM = "0"
    }
}

/** Значення параметра запиту: слаги й хеші порталу приходять ззовні. */
private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
