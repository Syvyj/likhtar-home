package com.likhtar.home.core.data

import com.likhtar.home.core.model.LauncherApp
import kotlinx.coroutines.flow.Flow

/**
 * Джерело встановлених застосунків.
 *
 * Реалізація — [PackageManagerAppsRepository]: запит LEANBACK_LAUNCHER-активностей +
 * підписка на broadcast'и встановлення/видалення пакетів. Контракт тримає [Flow],
 * щоб зміни джерела не торкались UI.
 */
interface AppsRepository {
    val apps: Flow<List<LauncherApp>>

    /** Запускає застосунок. Повертає false, якщо точку входу не знайдено. */
    fun launch(app: LauncherApp): Boolean

    /**
     * Відкриває системне видалення застосунку.
     *
     * Свого підтвердження не малюємо: діалог видалення — обов'язок системи, і користувач
     * має бачити рівно той, що й у решті Android TV.
     */
    fun requestUninstall(packageName: String): Boolean

    /**
     * Системна сторінка застосунку — там «Примусово зупинити» й очищення кешу. Сам лаунчер
     * зупинити чужий застосунок не може: з Android 14 `killBackgroundProcesses` діє лише на
     * власні процеси, а FORCE_STOP_PACKAGES видають тільки системним.
     */
    fun openAppInfo(packageName: String): Boolean = false

    /** Розмір APK у байтах — 0, якщо застосунок зник. Це саме файл, а не дані. */
    fun apkSizeBytes(packageName: String): Long

    /**
     * Запуск за URI: відкриває конкретну картку, а не застосунок.
     *
     * [packageName] обов'язковий, коли адресат відомий. Причина знайдена на приставці:
     * Sweet.TV оголошує себе обробником **усіх** http/https-посилань, тому «відкрити картку
     * TMDB у сторонньому застосунку» без явного пакета відкривалося у вбудованому браузері Sweet.TV.
     */
    fun launchIntentUri(intentUri: String, packageName: String? = null): Boolean

    /**
     * Системний пошук усередині застосунку: `ACTION_SEARCH` із запитом.
     *
     * Працює лише там, де застосунок оголосив `android.app.searchable` (напр. сторонні
     * медіазастосунки). Sweet.TV і MEGOGO цей інтент приймають, але запит ігнорують і просто
     * відкриваються — тому їм ми його й не шлемо.
     */
    fun searchInApp(packageName: String, query: String): Boolean

    /**
     * Веб-посилання, яке має відкрити конкретний застосунок.
     *
     * Спершу з явним пакетом, і лише якщо той його не взяв — без пакета, віддаючи вибір
     * системі (App Links). Другий крок свідомо ризикований: без пакета посилання може
     * перехопити Sweet.TV, тому він саме запасний, а не основний.
     */
    fun openLink(url: String, packageName: String): Boolean

    /** Просто відкрити застосунок за пакетом — коли глибшого посилання немає. */
    fun launchPackage(packageName: String): Boolean

    /** Чи встановлено застосунок — ТВ Ефір показує канали лише тих, що є в людини. */
    fun isInstalled(packageName: String): Boolean

    /** Установлені відеоплеєри: усе, що бере `VIEW` з відео-MIME. */
    fun videoPlayers(): List<LauncherApp>

    /**
     * Відкрити потік у плеєрі.
     *
     * [packageName] порожній — показуємо системний вибір плеєра **завжди**. Просто
     * `ACTION_VIEW` без пакета для цього не годиться: система один раз запам'ятовує
     * типовий плеєр і далі відкриває його мовчки, а на приставці замовника так і сталося —
     * вибору більше не пропонували. Чий плеєр запускати, вирішує лаунчер, а не система.
     *
     * [mime] відрізняє одиночний потік від плейлиста сезону: з відео-MIME плеєр грає один
     * файл, з `audio/x-mpegurl` бачить список і сам перемикає серії.
     */
    fun openVideo(uri: String, packageName: String?, mime: String = "video/*"): Boolean
}
