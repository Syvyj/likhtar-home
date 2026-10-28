package com.likhtar.home.core.data

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Хто зараз обробляє кнопку HOME і що з цим можна зробити. */
data class HomeLauncherStatus(
    val isDefault: Boolean,
    /** Пакет чинного домашнього екрана — `null`, якщо система не дала відповіді. */
    val currentPackage: String?,
    val currentLabel: String?,
) {
    /**
     * Яку інструкцію показувати, якщо система не дала замінити лаунчер.
     *
     * Google TV і AOSP-збірки поводяться по-різному: перша взагалі не має екрана вибору
     * домашнього застосунку, друга зазвичай має.
     */
    val blocker: HomeBlocker
        get() = when (currentPackage) {
            null -> HomeBlocker.UNKNOWN
            in GOOGLE_TV_LAUNCHERS -> HomeBlocker.GOOGLE_TV
            else -> HomeBlocker.AOSP
        }

    /**
     * Тип пристрою для зовнішньої інструкції: сторінка відкривається одразу на потрібному
     * розділі, бо кроки для Google TV і для AOSP різні.
     */
    val deviceKind: String
        get() = when {
            currentPackage in GOOGLE_TV_LAUNCHERS -> "google-tv"
            currentPackage in AOSP_LAUNCHERS -> "android-tv"
            else -> "aosp"
        }

    companion object {
        /**
         * Мапа передвстановлених лаунчерів — одне місце на весь проєкт.
         *
         * `launcherx` — Google TV (рекомендації й реклама на головній), `tvlauncher` —
         * класичний Android TV. Вендорські оболонки сюди не вносимо: вони поводяться як
         * AOSP, а перелічити їх усі однаково неможливо.
         */
        val GOOGLE_TV_LAUNCHERS = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.apps.mediashell",
        )

        /**
         * Класичний Android TV. Вендорських оболонок тут немає навмисно: перелічити їх усі
         * неможливо, а поводяться вони як AOSP — для них працює той самий текст.
         *
         * Системних застосунків, які лише оголошують `CATEGORY_HOME` (налаштування,
         * майстри відновлення), у мапі бути не повинно: вимкнути їх порадою з екрана —
         * найкращий спосіб зробити користувачеві гірше, ніж було.
         */
        val AOSP_LAUNCHERS = setOf("com.google.android.tvlauncher")
    }
}

enum class HomeBlocker { GOOGLE_TV, AOSP, UNKNOWN }

/** Сторонній застосунок, який уміє перепризначити кнопку Home. */
data class RemapperApp(
    val packageName: String,
    val title: String,
    /** Українською; для екрана — [noteRes] (мова інтерфейсу). */
    val note: String,
    val noteRes: Int = 0,
)

/**
 * Закріплення лаунчера на кнопку HOME.
 *
 * Лаунчер нічого не робить із системою сам: він лише відкриває системні екрани вибору.
 * Accessibility-сервісу тут свідомо немає — перехоплення кнопок заради обходу вибору
 * користувача це і ризик для рев'ю в сторі, і поведінка, якої від домашнього екрана
 * ніхто не чекає.
 */
@Singleton
class HomeLauncherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    /** Шлях без системних діалогів: ADB самого телевізора ([HomeTakeover]). */
    val takeover: HomeTakeover,
) {
    fun status(): HomeLauncherStatus {
        val rival = currentRival()
        return HomeLauncherStatus(
            isDefault = holdsHomeRole(),
            currentPackage = rival,
            currentLabel = rival?.let(::labelOf),
        )
    }

    /**
     * Чи HOME належить нам.
     *
     * Питаємо роль, а не `resolveActivity`: на приставці (Google TV, Android 14) резолвер
     * віддає `setupwraith.RecoveryActivity` — майстер налаштування теж оголошує
     * `CATEGORY_HOME`, і за пріоритетом виграє він, хоча кнопку HOME тримає лаунчер.
     * `isRoleHeld` питає рівно про себе й не потребує дозволів.
     */
    private fun holdsHomeRole(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val resolved = context.packageManager
            .resolveActivity(HOME_INTENT, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Хто саме тримає кнопку HOME замість нас — його ім'я підуть у підказку й у команду ADB,
     * тому помилитися тут не можна: вимкнути не той пакет гірше, ніж не підказати нічого.
     *
     * Тримача ролі напряму дізнатися не можна (`getRoleHolders` — системний дозвіл), тому
     * беремо кандидатів на HOME у порядку пріоритету й відкидаємо себе, діалог вибору та
     * майстрів налаштування: вони перехоплюють HOME тимчасово й домашнім екраном не є.
     */
    private fun currentRival(): String? {
        val pm = context.packageManager
        val resolved = pm.resolveActivity(HOME_INTENT, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        val candidates = (
            listOfNotNull(resolved) +
                pm.queryIntentActivities(HOME_INTENT, PackageManager.MATCH_DEFAULT_ONLY)
                    .map { it.activityInfo.packageName }
            ).filter { it != context.packageName && it != ANDROID_RESOLVER && !isTemporaryHome(it) }

        // Передвстановлений лаунчер важливіший за будь-який сторонній, навіть якщо система
        // назвала першим не його: саме він тримає роль HOME і саме його вимикають через ADB.
        // Сторонній лаунчер (Projectivy тощо) користувач поставив сам і прибере теж сам.
        return candidates.firstOrNull { it in HomeLauncherStatus.GOOGLE_TV_LAUNCHERS }
            ?: candidates.firstOrNull { it in HomeLauncherStatus.AOSP_LAUNCHERS }
            ?: candidates.firstOrNull()
    }

    /** Майстри налаштування Android TV: тримають HOME лише до кінця першого запуску. */
    private fun isTemporaryHome(packageName: String): Boolean =
        packageName.contains("setupwraith") || packageName.contains("setupwizard")

    /**
     * Способи попросити систему, у порядку спадання ввічливості. Пробувати треба саме
     * по черзі й лише після відмови попереднього: перший веде одразу в потрібний діалог,
     * останній лише відкриває вибір і покладається на «Завжди».
     */
    fun requestIntents(): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true &&
                !roles.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                add(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
            }
        }
        add(Intent(Settings.ACTION_HOME_SETTINGS))
        add(Intent(HOME_INTENT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * «Перестати бути домашнім екраном» для тих, хто обрав нас системним вибором, а не
     * перехопленням ([takeover]): забуваємо своє «Завжди» й ведемо туди, де HOME віддають
     * іншому. Роль HOME застосунок сам віддати не може — лише показати, де це зробити.
     * Без ролі (Android 9 і старші) після скидання HOME знову спитає, чим відкривати.
     */
    fun releaseIntents(): List<Intent> {
        @Suppress("DEPRECATION")
        runCatching { context.packageManager.clearPackagePreferredActivities(context.packageName) }
        return buildList {
            add(Intent(Settings.ACTION_HOME_SETTINGS))
            add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            if (!holdsHomeRole()) add(Intent(HOME_INTENT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }.map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    /**
     * Чи зможемо піднятися самі після ввімкнення приставки.
     *
     * Дозвіл «показ поверх інших додатків» тут потрібен не для оверлеїв: він єдине
     * виключення з правила про фоновий запуск активності, під яке потрапляє наш
     * `BootReceiver`. Без нього система рубає старт (`BAL_BLOCK`), із ним пускає
     * (`BAL_ALLOW_SAW_PERMISSION`) — перевірено на приставці, Android 14.
     */
    fun canAutostart(): Boolean = Settings.canDrawOverlays(context)

    /** Другий інтент без `package:` — на збірках, де сторінки конкретного застосунку немає. */
    fun autostartIntents(): List<Intent> = listOf(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
    ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /**
     * Сторінка застосунку в маркеті: спершу `market://` (відкриє сам Play), інакше https —
     * на приставках без Play Store лишається браузер, і це краще за мовчазну відмову.
     */
    fun storeIntents(packageName: String): List<Intent> = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")),
    ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** Зовнішня інструкція з ADB-кроками: в самій апці команд немає навмисно. */
    fun helpUrl(status: HomeLauncherStatus): String? = HELP_URL?.let { "$it?device=${status.deviceKind}" }

    private fun labelOf(packageName: String): String? = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrNull()

    companion object {
        private const val ANDROID_RESOLVER = "android"
        private val HOME_INTENT: Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        /**
         * Сторінка з покроковою інструкцією. Ще не опублікована (likhtar.app не існує):
         * доти сходинку «З комп'ютера» не показуємо — QR у порожнечу гірший за його відсутність.
         */
        private val HELP_URL: String? = null

        /**
         * Застосунки-ремапери — те, що працює без комп'ютера: вони перепризначають саму
         * кнопку пульта. Свого Accessibility-ремапа в лаунчері немає свідомо: перехоплення
         * кнопок домашнім екраном — окрема розмова з рев'ю Play і ризик, якого v1 не варта.
         */
        val REMAPPERS = listOf(
            RemapperApp(
                packageName = "com.flar2.homebutton",
                title = "Button Mapper",
                note = "Перепризначає будь-яку кнопку пульта, є безкоштовна версія",
                noteRes = R.string.data_remapper_button_mapper_note,
            ),
            RemapperApp(
                packageName = "com.tvquickactions",
                title = "TvQuickActions",
                note = "Зроблений саме під Android TV, уміє довгі натискання",
                noteRes = R.string.data_remapper_tvquickactions_note,
            ),
        )
    }
}
