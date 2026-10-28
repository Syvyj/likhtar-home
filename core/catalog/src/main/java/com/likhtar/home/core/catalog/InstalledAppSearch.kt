package com.likhtar.home.core.catalog

import android.app.SearchManager
import android.app.SearchableInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Пошук матеріалу у **встановлених** застосунках через стандартний контракт Android TV.
 *
 * Ніяких інтеграцій з MEGOGO чи SWEET.TV окремо: застосунок сам оголошує
 * `android.app.searchable` із suggestion-провайдером, ми питаємо його назвою фільму, а він
 * у колонках курсора сам каже, яким інтентом його відкрити на цьому матеріалі. Тобто
 * список сервісів у коді не потрібен — що встановлено, те й відповідає.
 *
 * Частина застосунків провайдера не має — у них є лише пошукова активність без
 * `CATEGORY_DEFAULT`, тому неявний `ACTION_SEARCH` до них навіть не резолвиться. Для таких лишається [searchFallback]: запуск їхньої ж пошукової
 * активності за іменем компонента, з підставленою назвою.
 */
@Singleton
class InstalledAppSearch @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Що з встановленого відкриє саме цей фільм. Порожньо — жоден застосунок не відповів.
     *
     * Кожен провайдер опитується паралельно і зі своїм таймаутом: один повільний
     * застосунок не має тримати картку.
     */
    suspend fun find(item: CatalogItem): List<WatchOption> = coroutineScope {
        val manager = context.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
            ?: return@coroutineScope emptyList()
        val searchables = searchables(manager)
        Log.i(
            TAG,
            "searchable: " + searchables.joinToString {
                "${it.searchActivity.packageName}→${it.suggestAuthority ?: "без провайдера"}"
            },
        )

        // 1) Хто відкриває саму картку за посиланням TMDB — точніше за будь-який пошук.
        // 2) Провайдери пошуку. 3) Пошукова активність без провайдера.
        // Без списку застосунків у коді: що встановлено й що оголосило, те й відповідає.
        val cards = tmdbCards(item)
        val covered = cards.mapNotNull { it.launch?.packageName }.toSet()
        val bare = bareSearch(item, skip = covered + searchables.map { it.searchActivity.packageName })
        cards + searchables.filter { it.searchActivity.packageName !in covered }.map { info ->
            async(Dispatchers.IO) {
                // ponytail: таймаут відпускає нас, але сам блокуючий query() дотикає в IO-пулі —
                // скасувати чужий ContentProvider нічим. Якщо пул почне забиватись, тут
                // потрібен окремий обмежений диспетчер.
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    if (info.suggestAuthority.isNullOrBlank()) searchFallback(info, item)
                    else suggestion(info, item)
                }
            }
        }.awaitAll().filterNotNull().distinctBy { it.launch?.packageName } + bare
    }

    /**
     * Застосунки, що відкривають `https://www.themoviedb.org/movie/<id>` своєю карткою.
     * Браузери посилання теж беруть, але відкрили б сайт, а не картку: їх відсіюємо тим, що
     * вони так само беруть будь-яку адресу.
     */
    private fun tmdbCards(item: CatalogItem): List<WatchOption> {
        if (item.tmdbId <= 0 || item.opens.isNotBlank()) return emptyList()
        val manager = context.packageManager
        val link = Uri.parse("https://www.themoviedb.org/${item.kind.tmdbPath}/${item.tmdbId}")
        val browsers = manager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(ANY_SITE)), 0)
            .map { it.activityInfo.packageName }.toSet()
        return manager.queryIntentActivities(Intent(Intent.ACTION_VIEW, link).addCategory(Intent.CATEGORY_BROWSABLE), 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && it.packageName !in IGNORED && it.packageName in tvApps && it.packageName !in browsers }
            .distinctBy { it.packageName }
            .map { activity ->
                val label = labelOf(activity.packageName)
                val intent = Intent(Intent.ACTION_VIEW, link).setComponent(ComponentName(activity.packageName, activity.name))
                WatchOption(
                    label = context.getString(R.string.catalog_open_in, label),
                    service = label,
                    kind = WatchOption.OptionKind.EXTERNAL_PLAYER,
                    launch = LaunchAction(packageName = activity.packageName, uri = intent.toUri(Intent.URI_INTENT_SCHEME)),
                )
            }
            .toList()
    }

    /**
     * Застосунки з пошуковою активністю, але без `android.app.searchable`: система їх
     * у [searchables] не віддає, тож «Шукати в …» будуємо самі — тим самим явним інтентом.
     */
    private fun bareSearch(item: CatalogItem, skip: Set<String>): List<WatchOption> =
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SEARCH), 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && it.packageName !in IGNORED && it.packageName in tvApps && it.packageName !in skip }
            .distinctBy { it.packageName }
            .map { activity ->
                val label = labelOf(activity.packageName)
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    component = ComponentName(activity.packageName, activity.name)
                    putExtra(SearchManager.QUERY, item.searchQuery)
                    putExtra("query", item.searchQuery)
                }
                WatchOption(
                    label = context.getString(R.string.catalog_search_in, label),
                    service = label,
                    kind = WatchOption.OptionKind.SEARCH,
                    launch = LaunchAction(packageName = activity.packageName, uri = intent.toUri(Intent.URI_INTENT_SCHEME)),
                )
            }
            .toList()

    /**
     * Застосунки з плиткою на екрані телевізора (`LEANBACK_LAUNCHER`). Пошук і посилання TMDB
     * мають і телефонні системні застосунки — «Контакти», «Календар», «Пошта» — і на звичайному
     * Android вони лізли в кнопки сторінки фільму. Фільм відкриваємо лише в тому, що для ТБ.
     */
    private val tvApps: Set<String>
        get() {
            val now = System.currentTimeMillis()
            // Фільтр питає на кожному кандидаті — список тримаємо хвилину, а не перепитуємо систему.
            if (now - tvAppsAt > TV_APPS_TTL_MS) {
                tvAppsCache = context.packageManager
                    .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
                    .mapTo(HashSet()) { it.activityInfo.packageName }
                // Плеєри (Nova, ViMu) мають пошук, але лише по своїх файлах — фільму там немає.
                // Пізнаємо їх за `video/*`: жоден застосунок-джерело файли не відкриває.
                tvAppsCache -= context.packageManager
                    .queryIntentActivities(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///x.mkv"), "video/*"), 0)
                    .map { it.activityInfo.packageName }.toSet()
                tvAppsAt = now
            }
            return tvAppsCache
        }
    @Volatile private var tvAppsCache: Set<String> = emptySet()
    @Volatile private var tvAppsAt = 0L

    private fun labelOf(packageName: String): String = runCatching {
        val manager = context.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /**
     * Застосунки, які оголосили `android.app.searchable`.
     *
     * Системні відсіюємо: «шукати в Google Play» на сторінці фільму — це шум, а не варіант.
     */
    private fun searchables(manager: SearchManager): List<SearchableInfo> =
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SEARCH), 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && it.packageName !in IGNORED && it.packageName in tvApps }
            .mapNotNull {
                runCatching { manager.getSearchableInfo(ComponentName(it.packageName, it.name)) }.getOrNull()
            }
            .distinctBy { it.searchActivity.packageName }
            .toList()

    /**
     * Запит до suggestion-провайдера застосунку.
     *
     * Питаємо двічі — українською назвою й оригінальною: українські каталоги індексують
     * переклад, а сторонні медіа-застосунки й локальні бібліотеки — оригінал.
     */
    private fun suggestion(info: SearchableInfo, item: CatalogItem): WatchOption? {
        val queries = listOf(item.title, item.originalTitle).filter { it.isNotBlank() }.distinct()
        for (query in queries) {
            val result = runCatching { ask(info, query, item) }
            // Провайдер закритий для сторонніх (вимагає GLOBAL_SEARCH або не експортований):
            // спитати не можемо, але пошук у застосунку відкрити можемо.
            if (result.exceptionOrNull() is SecurityException) {
                Log.i(TAG, "${info.suggestAuthority} закритий — лише «Шукати в»")
                return searchFallback(info, item)
            }
            result.onFailure { Log.w(TAG, "${info.suggestAuthority} не відповів", it) }
            result.getOrNull()?.let { return it }
        }
        return null
    }

    private fun ask(info: SearchableInfo, query: String, item: CatalogItem): WatchOption? {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(info.suggestAuthority)
            .apply {
                info.suggestPath?.let { appendEncodedPath(it) }
                appendEncodedPath(SearchManager.SUGGEST_URI_PATH_QUERY)
                // Провайдер або читає запит зі шляху, або чекає на нього в selectionArgs —
                // це визначає сам застосунок своїм `searchSuggestSelection`.
                if (info.suggestSelection == null) appendPath(query)
            }
            .build()
        val selectionArgs = info.suggestSelection?.let { arrayOf(query) }

        context.contentResolver.query(uri, null, info.suggestSelection, selectionArgs, null)
            .use { cursor ->
                if (cursor == null) return null
                while (cursor.moveToNext()) {
                    val text = cursor.string(SearchManager.SUGGEST_COLUMN_TEXT_1) ?: continue
                    if (!titleMatches(text, item.title, item.originalTitle)) continue
                    // Матеріал у застосунку є, але відкрити його рядком не вдається
                    // (дія `GLOBALSEARCH` без обробника) — тоді хоча б пошук із
                    // підставленою назвою. Це краще, ніж мовчки сховати те, що там є.
                    val launch = intentFor(cursor, info, query) ?: return searchFallback(info, item)
                    return WatchOption(
                        label = context.getString(R.string.catalog_watch_in, appLabel(info)),
                        service = appLabel(info),
                        kind = WatchOption.OptionKind.DIRECT,
                        launch = launch,
                    )
                }
            }
        return null
    }

    /**
     * Інтент будує сам застосунок: ми лише складаємо його з колонок курсора за
     * документованим контрактом і серіалізуємо в `intent:`-URI, який уже вміє відкривати
     * [com.likhtar.home.core.data.AppsRepository.launchIntentUri].
     */
    private fun intentFor(cursor: Cursor, info: SearchableInfo, query: String): LaunchAction? {
        val packageName = info.searchActivity.packageName
        val action = cursor.string(SearchManager.SUGGEST_COLUMN_INTENT_ACTION)
            ?: info.suggestIntentAction
            ?: Intent.ACTION_VIEW
        // База адреси або з рядка, або з манфеста застосунку; id рядка — хвіст до неї.
        // Без бази посилання не буває: SWEET.TV, наприклад, віддає лише id `9728`, а
        // `content://tv.sweet.tvplayer/video_database_leanback` бере з `searchable.xml`.
        // Немає ні того, ні того — пункт не показуємо, бо відкривати нема що.
        val data = cursor.string(SearchManager.SUGGEST_COLUMN_INTENT_DATA)
            ?: info.suggestIntentData
            ?: return null
        val dataId = cursor.string(SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID)
        val target = listOfNotNull(data, dataId).joinToString("/")

        val intent = Intent(action).apply {
            setPackage(packageName)
            setData(Uri.parse(target))
            cursor.string(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA)?.let {
                putExtra(SearchManager.EXTRA_DATA_KEY, it)
            }
            putExtra(SearchManager.USER_QUERY, query)
        }
        // Рядок може попросити дію, якої в манфесті застосунку
        // немає (`GLOBALSEARCH` без обробника), або активність без `CATEGORY_DEFAULT`
        // — і те, й те падає з ActivityNotFoundException. Тому: спершу як
        // просить застосунок, потім те саме, але прямо на його пошукову активність за
        // іменем компонента — явний інтент категорій не питає. Перевіряємо тим самим
        // MATCH_DEFAULT_ONLY, яким користується сам startActivity, інакше перевірка
        // сказала б «є», а запуск упав би.
        val launchable = listOf(intent, Intent(intent).setComponent(info.searchActivity))
            .firstOrNull {
                context.packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) != null
            }
            ?: return null
        return LaunchAction(packageName = packageName, uri = launchable.toUri(Intent.URI_INTENT_SCHEME))
    }

    /**
     * Застосунок без провайдера: відкриваємо його ж пошукову активність із підставленою
     * назвою. Саме за іменем компонента — неявний `ACTION_SEARCH` тут не резолвиться,
     * бо такі активності не оголошують `CATEGORY_DEFAULT`.
     *
     * Один зайвий дотик замість нуля, зате працює там, де контракту немає.
     */
    private fun searchFallback(info: SearchableInfo, item: CatalogItem): WatchOption {
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            component = info.searchActivity
            putExtra(SearchManager.QUERY, item.searchQuery)
            putExtra("query", item.searchQuery)
        }
        return WatchOption(
            label = context.getString(R.string.catalog_search_in, appLabel(info)),
            service = appLabel(info),
            kind = WatchOption.OptionKind.SEARCH,
            launch = LaunchAction(
                packageName = info.searchActivity.packageName,
                uri = intent.toUri(Intent.URI_INTENT_SCHEME),
            ),
        )
    }

    /**
     * Чи це той самий матеріал.
     *
     * Провайдер віддає все, що схоже на запит, — без перевірки на картці зʼявився б
     * «Дивитися в …» стороннього застосунку для чужого фільму. Порівнюємо нормалізовано з обома назвами.
     */
    private fun appLabel(info: SearchableInfo): String = runCatching {
        val manager = context.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(info.searchActivity.packageName, 0)).toString()
    }.getOrDefault(info.searchActivity.packageName)

    private fun Cursor.string(column: String): String? {
        val index = getColumnIndex(column)
        if (index < 0) return null
        return getString(index)?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "LikhtarAppSearch"

        /** Живий провайдер відповідає за десятки мілісекунд; решта — не наша проблема. */
        const val PROVIDER_TIMEOUT_MS = 1_500L

        /** Системне й магазинне: пошук по фільму в них сенсу не має. */
        /** Будь-яка адреса: хто її бере — браузер, а не застосунок із карткою фільму. */
        const val ANY_SITE = "https://example.com/"

        const val TV_APPS_TTL_MS = 60_000L
        val IGNORED = setOf(
            "com.android.vending",
            "com.google.android.gms",
            "com.google.android.katniss",
            "com.android.tv.frameworkpackagestubs",
            "com.android.cts.ctsshim",
            "com.android.cts.priv.ctsshim",
        )
    }
}

/**
 * Нормалізація назви для звірки: регістр, пунктуація і зайві пробіли не рахуються.
 *
 * Одна на модуль — нею користуються і звірка suggestion-провайдерів, і релевантність
 * пошуку TMDB. Локаль явна, бо типова локаль пристрою може зіпсувати lowercase (турецька І).
 */
internal fun normalize(value: String): String = value
    .lowercase(java.util.Locale.forLanguageTag("uk-UA"))
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

/**
 * Чи це той самий матеріал.
 *
 * Провайдер віддає все, що бодай схоже на запит, — без звірки на картці зʼявився б
 * «Дивитися в …» стороннього застосунку для чужого фільму. Тому приймаємо тільки точний збіг або збіг із
 * роком у хвості («Обсесія (2025)»). Будь-який інший хвіст — це вже інший матеріал:
 * «Обсесія 2» — сиквел, і відкрити його замість потрібного гірше, ніж не показати нічого.
 */
internal fun titleMatches(text: String, title: String, originalTitle: String): Boolean {
    val found = normalize(text)
    if (found.isBlank()) return false
    return listOf(title, originalTitle)
        .filter { it.isNotBlank() }
        .map(::normalize)
        .any { it.isNotBlank() && (found == it || found.matches(Regex("${Regex.escape(it)} (19|20)\\d\\d"))) }
}
