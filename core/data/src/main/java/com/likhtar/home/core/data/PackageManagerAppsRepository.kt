package com.likhtar.home.core.data

import com.likhtar.home.core.model.StreamingService
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.likhtar.home.core.model.AppCategory
import com.likhtar.home.core.model.LauncherApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * Реальний список застосунків.
 *
 * Беремо і `LEANBACK_LAUNCHER` (нормальні TV-застосунки), і `LAUNCHER` (сайдлоад телефонних
 * застосунків — на приставках це звична практика, і саме їх стоковий лаунчер ховає).
 * Перевидаємо список на кожну зміну пакетів, тому UI не потребує ручного оновлення.
 */
@Singleton
class PackageManagerAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppsRepository {

    private val packageManager: PackageManager get() = context.packageManager

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Сигнали «пакети змінилися». onReceive нічого не рахує — він приходить на main
     * thread, а повний обхід PackageManager там підвішував би ввід.
     */
    private val packageEvents: Flow<Unit> = callbackFlow {
        trySend(Unit)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    // shareIn: кілька колекторів (Головна, «Застосунки», Кінозал) ділять один receiver і
    // один обхід PackageManager замість того, щоб кожен реєстрував і рахував своє.
    override val apps: Flow<List<LauncherApp>> = packageEvents
        .map { queryApps() }
        .flowOn(Dispatchers.Default)
        .shareIn(repositoryScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override fun launch(app: LauncherApp): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Не вдалося запустити ${app.id}", e)
            false
        }
    }

    private fun queryApps(): List<LauncherApp> {
        val leanback = resolve(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val classic = resolve(Intent.CATEGORY_LAUNCHER)

        // Якщо застосунок має і TV-, і телефонну точку входу, лишаємо TV-версію.
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        (leanback + classic).forEach { info ->
            val packageName = info.activityInfo.packageName
            if (packageName != context.packageName && packageName !in byPackage) {
                byPackage[packageName] = info
            }
        }

        return byPackage.values
            .map { it.toLauncherApp() }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Відкрити конкретну картку в чужому застосунку.
     *
     * `CLEAR_TASK` тут обов'язковий, і це заміряно на приставці. З одним `NEW_TASK` система
     * для вже запущеного застосунку відповідає «Activity not started, its current task has
     * been brought to the front» — тобто піднімає його **попередній стан** і наш URI
     * викидає. Виглядало це як «діплінк не працює, кидає в застосунок»: після першого ж
     * запуску Sweet.TV будь-яка наступна картка відкривала її головний екран. `CLEAR_TOP`
     * і `SINGLE_TOP` не допомагають — застосунок мусив би обробити `onNewIntent`, а
     * Sweet.TV цього не робить.
     *
     * Ціна: стан застосунку скидається. Для «відкрий саме цей фільм» це те, чого й хочемо.
     */
    override fun launchIntentUri(intentUri: String, packageName: String?): Boolean = runCatching {
        val parsed = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        // Sweet.TV: канал — через крок (див. openLink), решта посилань — без CLEAR_TASK.
        // Живий процес Sweet.TV губить посилання, поки активність перестворюється, а через
        // onNewIntent сторінку фільму відкриває й з екрана ТБ.
        if (parsed.data?.scheme == "sweettv") {
            if (parsed.data?.host == "channel") return openLink(parsed.dataString.orEmpty(), SWEET_TV)
            parsed.setPackage(packageName?.ifBlank { null } ?: SWEET_TV)
            context.startActivity(parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
        // Ролик YouTube — без CLEAR_TASK і з прапорцями «лише відео»: холодний YouTube TV
        // вибудовував вибір профілю й головну під роликом, і BACK вів туди, а не назад до
        // нас (три натискання). Так — один BACK, і ми на сторінці.
        if (parsed.data?.host?.endsWith("youtube.com") == true && parsed.data?.path == "/watch") {
            context.startActivity(
                parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setPackage(
                        packageName?.ifBlank { null }
                            ?: YOUTUBE_PACKAGES.firstOrNull { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess },
                    )
                    .putExtra("finish_on_ended", true)
                    .putExtra("force_fullscreen", true),
            )
            return true
        }
        val intent = parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        // Порожній рядок означає «конкретного застосунку не знаємо» — хай вибирає система.
        if (!packageName.isNullOrBlank()) intent.setPackage(packageName)
        context.startActivity(intent)
        // MEGOGO з холодного старту йде через вибір профілю й губить посилання — лишається
        // головна. Те саме посилання за 4 с відкриває фільм; живому MEGOGO повтор лише
        // перевідкриває ту саму сторінку (CLEAR_TASK). Перевірено на приставці 24.09.
        if (intent.data?.scheme == "megogo" && intent.data?.host == "video") {
            val again = Intent(intent)
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { context.startActivity(again) }.onFailure { Log.w(TAG, "MEGOGO: повтор $intentUri", it) } },
                MEGOGO_RETRY_MS,
            )
        }

        true
    }.onFailure { Log.w(TAG, "Не вдалося відкрити картку зі стрічки застосунку", it) }
        .getOrDefault(false)

    override fun videoPlayers(): List<LauncherApp> = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(SAMPLE_VIDEO, "video/*") }
        context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            // Заглушка Google TV (`frameworkpackagestubs`) бере video/* і одразу закривається —
            // це не плеєр: з нею OK на каналі плейлиста мовчки нічого не грав.
            .filterNot { it.packageName.startsWith("com.android.tv.frameworkpackagestubs") }
            .map { info ->
                LauncherApp(
                    packageName = info.packageName,
                    activityName = "",
                    label = context.packageManager.getApplicationLabel(info).toString(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }.onFailure { Log.w(TAG, "Не вдалося перелічити плеєри", it) }.getOrDefault(emptyList())

    override fun openVideo(uri: String, packageName: String?, mime: String): Boolean = runCatching {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Обраний плеєр може не приймати саме цей mime, і тоді пінити його — це гарантований
        // «Не вдалося відкрити плеєр». Заміряно `pm query-activities` на приставці: плейлист
        // серій ідемо як `audio/x-mpegurl`, і Just Player (`com.brouken.player`) його не
        // claim'ить, хоч `video/*` бере. Тому перед пініттям питаємо систему, а не вгадуємо.
        val handles = packageName != null && !packageName.isBlank() && runCatching {
            context.packageManager
                .queryIntentActivities(Intent(view).setPackage(packageName), 0)
                .isNotEmpty()
        }.getOrDefault(false)
        if (!handles && !packageName.isNullOrBlank()) {
            Log.i(TAG, "$packageName не приймає $mime — питаємо, чим відкрити")
        }
        val intent = if (!handles) {
            // createChooser ігнорує типовий застосунок системи — саме те, що треба і коли
            // користувач лишив «Питати щоразу», і коли обраний плеєр цей потік не візьме.
            Intent.createChooser(view, context.getString(R.string.data_open_with)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            view.setPackage(packageName)
        }
        context.startActivity(intent)
        true
    }.onFailure { Log.w(TAG, "Не вдалося відкрити потік", it) }.getOrDefault(false)

    override fun searchInApp(packageName: String, query: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(packageName)
            // Обидва ключі: `SearchManager.QUERY` — стандарт, а частина TV-застосунків читає
            // саме рядок "query" з extras, бо історично копіювала приклад Leanback.
            putExtra(SearchManager.QUERY, query)
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.onFailure { Log.w(TAG, "Пошук у $packageName не вдався", it) }.getOrDefault(false)

    override fun openLink(url: String, packageName: String): Boolean {
        val uri = Uri.parse(url)
        // Sweet.TV (3.57.3): посилання каналу, що приходить у вже відкритий
        // екран ТБ, він ігнорує — навігація в той самий екран нічого не робить, і лишається
        // попередній канал. А з будь-якого іншого екрана перемикає. Тож спершу крок на картку
        // фільму (`sweettv://movie/1`), за секунду — канал. На холодному старті це теж
        // працює: друге посилання просто заміщує перше в черзі старту. Гасити процес з
        // Android 14 не можна (killBackgroundProcesses — лише свій пакет), CLEAR_TASK теж
        // не рятує: живий процес губить посилання, поки активність перестворюється.
        // Перевірено на приставці: і живий Sweet.TV на іншому каналі, і вбитий.
        if (packageName == SWEET_TV && uri.scheme == "sweettv" && uri.host == "channel") {
            val hop = Intent(Intent.ACTION_VIEW, Uri.parse("sweettv://movie/1"))
                .setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val channel = Intent(Intent.ACTION_VIEW, uri)
                .setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(hop); true }.getOrDefault(false)) {
                Handler(Looper.getMainLooper()).postDelayed(
                    { runCatching { context.startActivity(channel) }.onFailure { Log.w(TAG, "Sweet.TV: канал $url", it) } },
                    SWEET_HOP_MS,
                )
                return true
            }
        }
        // CLEAR_TOP обов'язковий: без нього другий канал поспіль просто виносив уже
        // запущений застосунок наперед, і той лишався на попередньому каналі.
        //
        // CLEAR_TASK — Sweet.TV 3.57.3 (оновився того ранку) і MEGOGO, коли вже
        // запущені, отримують посилання в onNewIntent (ActivityTaskManager: result code=2,
        // «delivered to top») і ігнорують його — просто відкривався застосунок. Свіже
        // завдання дає активності посилання в onCreate, як на холодному старті.
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val byPackage = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            addFlags(flags)
        }
        // Власна схема застосунку (`sweettv://`, `megogo://`) веде рівно в його ж активність,
        // тому пакета досить. А от http/https перехоплює `tv.sweet.browser.BrowserActivity`
        // (оголошена без хоста) — там і тільки там б'ємо явним компонентом.
        val isWeb = uri.scheme == "http" || uri.scheme == "https"
        if (!isWeb && runCatching { context.startActivity(byPackage); true }.getOrDefault(false)) return true

        val main = packageManager.getLeanbackLaunchIntentForPackage(packageName)?.component
            ?: packageManager.getLaunchIntentForPackage(packageName)?.component
        if (main != null) {
            val direct = Intent(Intent.ACTION_VIEW, uri).apply {
                component = main
                addFlags(flags)
            }
            if (runCatching { context.startActivity(direct); true }.getOrDefault(false)) return true
        }

        if (runCatching { context.startActivity(byPackage); true }.getOrDefault(false)) return true

        Log.w(TAG, "$packageName не взяв $url — пробуємо системний вибір")
        val open = Intent(Intent.ACTION_VIEW, uri).addFlags(flags)
        return runCatching { context.startActivity(open); true }
            .onFailure { Log.w(TAG, "Посилання $url не відкрив ніхто", it) }
            .getOrDefault(false)
    }

    override fun isInstalled(packageName: String): Boolean =
        // Вимкнений застосунок посилання не відкриє — для ТВ Ефіру його немає.
        runCatching { packageManager.getApplicationInfo(packageName, 0).enabled }.getOrDefault(false)

    override fun launchPackage(packageName: String): Boolean = runCatching {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.onFailure { Log.w(TAG, "Не вдалося запустити $packageName", it) }.getOrDefault(false)

    override fun requestUninstall(packageName: String): Boolean = runCatching {
        // На Google TV ACTION_DELETE часто резолвиться в нікуди, хоча Package Installer
        // приймає саме ACTION_UNINSTALL_PACKAGE. Явно перевіряємо обробник до запуску,
        // тому UI більше не показує успіх, коли системний діалог навіть не відкрився.
        val uri = Uri.parse("package:$packageName")
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        checkNotNull(intent.resolveActivity(packageManager)) { "Package Installer недоступний" }
        context.startActivity(intent)
        true
    }.onFailure { Log.w(TAG, "Не вдалося відкрити видалення $packageName", it) }.getOrDefault(false)

    override fun openAppInfo(packageName: String): Boolean = runCatching {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        checkNotNull(intent.resolveActivity(packageManager)) { "Сторінки застосунку в системі немає" }
        context.startActivity(intent)
        true
    }.onFailure { Log.w(TAG, "Не вдалося відкрити сторінку $packageName", it) }.getOrDefault(false)

    override fun apkSizeBytes(packageName: String): Long = runCatching {
        val source = packageManager.getApplicationInfo(packageName, 0).sourceDir
        java.io.File(source).length()
    }.getOrDefault(0L)

    private fun resolve(category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return runCatching { packageManager.queryIntentActivities(intent, 0) }
            .getOrElse {
                Log.w(TAG, "Запит застосунків для $category не вдався", it)
                emptyList()
            }
    }

    private fun ResolveInfo.toLauncherApp(): LauncherApp {
        val activity = activityInfo
        val application = activity.applicationInfo
        val label = runCatching { loadLabel(packageManager).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: activity.packageName

        val bannerRes = activity.banner.takeIf { it != 0 } ?: application.banner.takeIf { it != 0 }
        val iconRes = activity.icon.takeIf { it != 0 } ?: application.icon.takeIf { it != 0 }

        return LauncherApp(
            packageName = activity.packageName,
            activityName = activity.name,
            label = label,
            bannerUri = bannerRes?.let { resourceUri(activity.packageName, it) },
            iconUri = iconRes?.let { resourceUri(activity.packageName, it) },
            glyph = label.take(1).uppercase(),
            isVideo = isVideoApp(application, activity.packageName),
            category = categoryOf(application, activity.packageName),
        )
    }

    /**
     * Категорія для сітки «Застосунків».
     *
     * Спершу те, що застосунок оголосив сам (`ApplicationInfo.category`, з API 26) і старий
     * прапорець `FLAG_IS_GAME` — це найнадійніше джерело. Далі — короткий список ключових
     * слів: багато TV-застосунків категорію не оголошують взагалі, і без цього половина
     * опинилася б в «Іншому». Останнє слово завжди за користувачем: ручна категорія
     * перекриває обидва шляхи.
     */
    private fun categoryOf(application: ApplicationInfo, packageName: String): AppCategory {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (application.category) {
                ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.MEDIA
                ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
                ApplicationInfo.CATEGORY_PRODUCTIVITY,
                ApplicationInfo.CATEGORY_SOCIAL,
                ApplicationInfo.CATEGORY_NEWS,
                ApplicationInfo.CATEGORY_MAPS,
                -> return AppCategory.TOOLS
            }
        }
        @Suppress("DEPRECATION")
        if (application.flags and ApplicationInfo.FLAG_IS_GAME != 0) return AppCategory.GAMES

        val lower = packageName.lowercase()
        return when {
            // Системні налаштування (com.android.tv.settings) ловили «tv.» і ставали відео.
            SETTINGS_KEYWORD in lower -> AppCategory.TOOLS
            StreamingService.byPackage(packageName) != null || VIDEO_KEYWORDS.any { it in lower } -> AppCategory.MEDIA
            GAME_KEYWORDS.any { it in lower } -> AppCategory.GAMES
            TOOL_KEYWORDS.any { it in lower } -> AppCategory.TOOLS
            else -> AppCategory.OTHER
        }
    }

    /**
     * Чи це відео-застосунок.
     *
     * Основне джерело — `ApplicationInfo.category`: система дозволяє застосунку оголосити
     * себе `CATEGORY_VIDEO`, і саме так роблять великі сервіси. Але оголошують не всі, тому
     * є список відомих пакетів як другий шлях — свідомо короткий і чесно неповний: він потрібен
     * лише щоб «Кінозал» не був порожнім із коробки, а далі користувач керує ним сам.
     */
    private fun isVideoApp(application: ApplicationInfo, packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            application.category == ApplicationInfo.CATEGORY_VIDEO
        ) {
            return true
        }
        // Max, Apple TV, SkyShowtime жодного ключового слова в пакеті не мають.
        if (StreamingService.byPackage(packageName) != null) return true
        val lower = packageName.lowercase()
        return SETTINGS_KEYWORD !in lower && VIDEO_KEYWORDS.any { it in lower }
    }

    private fun resourceUri(packageName: String, resId: Int) = "android.resource://$packageName/$resId"

    private companion object {
        const val TAG = "LikhtarApps"
        val SWEET_TV = StreamingService.SWEET.packageName

        /** Офіційний YouTube: спершу TV-версія, далі звичайна; жодної — системний вибір. */
        val YOUTUBE_PACKAGES = StreamingService.YOUTUBE.packages
        const val SWEET_HOP_MS = 1000L

        /** Скільки MEGOGO на холодному старті проходить вибір профілю. */
        const val MEGOGO_RETRY_MS = 4000L

        /** Будь-яке відео-посилання: потрібне лише щоб система назвала своїх обробників. */
        val SAMPLE_VIDEO: Uri = Uri.parse("http://likhtar.local/sample.mp4")


        val VIDEO_KEYWORDS = listOf(
            "netflix", "youtube", "megogo", "sweet", "kino", "cinema", "hbo", "disney",
            "primevideo", "amazon.avod", "plex", "kodi", "jellyfin", "emby", "stremio",
            "player", "tv.", "iptv", "ivi", "oll", "takflix", "viju", "video",
        ) + FLAVOR_VIDEO_KEYWORDS

        const val SETTINGS_KEYWORD = "settings"

        val GAME_KEYWORDS = listOf("game", "games", "play.games", "arcade", "emulator", "retroarch")

        val TOOL_KEYWORDS = listOf(
            "filemanager", "file.manager", "explorer", "vpn", "settings", "keyboard", "remote",
            "browser", "downloader", "vending", "installer", "speedtest", "adb",
            "buttonmapper", "deviceinfo", "control", "mapper",
        ) + FLAVOR_TOOL_KEYWORDS
    }
}
