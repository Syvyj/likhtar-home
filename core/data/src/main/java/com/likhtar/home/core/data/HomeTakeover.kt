package com.likhtar.home.core.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.likhtar.home.core.adb.LocalAdb
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ліхтар — справжній домашній екран, без комп'ютера: через ADB самого телевізора ([LocalAdb])
 * `cmd package set-home-activity` на себе і `pm disable-user` стокового лаунчера.
 *
 * Чому не роль HOME: на Google TV при завантаженні виграє HOME-фільтр із вищим `priority`
 * (`launcherx` — 2, наш — 0), а не тримач ролі; вимкнений сам `launcherx` віддавав HOME
 * майстрові `setupwraith` (RecoveryActivity, цикл перезапуску, 08.2026). Тому, як у Free My TV,
 * спершу вимикаємо весь пакет `setupwraith`, і лише потім лаунчер. Більше нічого не чіпаємо.
 *
 * Запобіжники: якщо збережену мережу Wi-Fi записав пакет, який вимикаємо (`dumpsys wifi`,
 * `cname=`), — нічого не робимо: вимкнення стерло б мережі. Повернення — у зворотному
 * порядку: лаунчер, домашній екран, майстер. Що вимкнено — пам'ятаємо, щоб повернути саме це.
 */
@Singleton
class HomeTakeover @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Problem { DEBUGGING_OFF, PORT_CLOSED, NOT_APPROVED, NO_RESPONSE, WIRELESS_TLS, WIFI_OWNED, WIFI_UNKNOWN, REFUSED, OTHER }

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ліхтар зробили домашнім цим способом (і ще не повернули). */
    val isActive: Boolean get() = prefs.getBoolean(KEY_ACTIVE, false)

    /** Зробити Ліхтар домашнім. `null` — вийшло. Блокує: викликати не з головного потоку. */
    fun takeOver(onApproval: () -> Unit): Problem? = withAdb(APPROVAL_MS, onApproval) { adb ->
        val homes = homeComponents(adb)
        val ours = ourComponent() ?: return@withAdb Problem.OTHER
        val original = prefs.getString(KEY_ORIGINAL, null)?.takeIf(::safe) ?: originalHome(adb, homes)
        val setupHome = homes.any { it.startsWith("$SETUP/") }
        val homePkg = original.substringBefore('/').takeIf { it.isNotBlank() && it != context.packageName }
        val owners = wifiOwners(adb)
        if (owners == null && adb.shell("settings get global wifi_on").trim() != "0") return@withAdb Problem.WIFI_UNKNOWN
        if ((setupHome && SETUP in owners.orEmpty()) || (homePkg != null && homePkg in owners.orEmpty())) {
            return@withAdb Problem.WIFI_OWNED
        }
        if (!adb.shell("cmd package set-home-activity '$ours'").contains("Success")) return@withAdb Problem.REFUSED
        prefs.edit().putBoolean(KEY_ACTIVE, true).putString(KEY_ORIGINAL, original).apply()

        val disabled = prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty().toMutableSet()
        fun disable(pkg: String) {
            if (adb.shell("pm disable-user --user 0 $pkg").contains("disabled-user")) disabled += pkg
            prefs.edit().putStringSet(KEY_DISABLED, disabled).apply()
        }
        if (setupHome) disable(SETUP)
        // Лаунчер — лише коли майстра вже немає: інакше HOME забрав би його RecoveryActivity.
        if (homePkg != null && (!setupHome || SETUP in disabled)) disable(homePkg)
        Log.i(TAG, "домашній — Ліхтар; вимкнено: $disabled, був: $original")
        null
    }

    /** Повернути як було: лаунчер, його домашній екран, майстер. `null` — вийшло. */
    fun revert(): Problem? = withAdb(APPROVAL_MS, {}) { adb -> restore(adb) }

    /**
     * Після завантаження: якщо домашнім знову став не Ліхтар (оновлення телевізора вмикає
     * пакети назад) — повторити. Ключ уже дозволено, тож без питань на екрані.
     */
    fun reapplyIfNeeded() {
        if (!isActive || resolvesToUs()) return
        withAdb(QUIET_MS, {}) { adb ->
            prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty().forEach { adb.shell("pm disable-user --user 0 $it") }
            ourComponent()?.let { adb.shell("cmd package set-home-activity '$it'") }
            Log.i(TAG, "після завантаження: домашній — знову Ліхтар")
            null
        }
    }

    /**
     * Сторож: деякі виробники вмикають стоковий лаунчер своїм сервісом уже після завантаження
     * (на проєкторах XGIMI — `xrmservice`, ~20 с після старту), коли [reapplyIfNeeded] давно
     * минув. Слухаємо «пакет змінено» для того, що ми вимкнули, і вимикаємо знову. Реєструється
     * з процесу лаунчера, а він живий, поки він — домашній екран.
     */
    fun watch() {
        val filter = android.content.IntentFilter(Intent.ACTION_PACKAGE_CHANGED).apply { addDataScheme("package") }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val pkg = intent.data?.schemeSpecificPart ?: return
                if (!isActive || pkg !in prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty()) return
                val state = runCatching { context.packageManager.getApplicationEnabledSetting(pkg) }.getOrNull()
                if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) return
                Log.i(TAG, "$pkg знову ввімкнено ззовні — забираємо домашній екран назад")
                val pending = goAsync()
                Thread {
                    try { reapplyIfNeeded() } finally { pending.finish() }
                }.start()
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    /** Аварійне повернення (запобіжник падінь): коротко, без питань на екрані. */
    fun revertQuietly(): Problem? = withAdb(QUIET_MS, {}) { adb -> restore(adb) }

    private fun restore(adb: LocalAdb): Problem? {
        val disabled = prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty()
        val original = prefs.getString(KEY_ORIGINAL, "").orEmpty().takeIf(::safe)
        // Спершу знімаємо прапорець: інакше сторож ([watch]) побачив би ввімкнений лаунчер
        // і вимкнув би його назад посеред повернення.
        prefs.edit().putBoolean(KEY_ACTIVE, false).commit()
        disabled.filter { it != SETUP }.forEach { adb.shell("pm enable $it") }
        if (original != null) {
            adb.shell("cmd package set-home-activity '$original'")
        } else {
            adb.shell("cmd role remove-role-holder --user 0 android.app.role.HOME ${context.packageName}")
        }
        if (SETUP in disabled) adb.shell("pm enable $SETUP")
        prefs.edit().clear().apply()
        Log.i(TAG, "повернуто: $original, увімкнено: $disabled")
        return null
    }

    private fun withAdb(approvalMs: Int, onApproval: () -> Unit, block: (LocalAdb) -> Problem?): Problem? = try {
        LocalAdb.connect(context, approvalMs, onApproval).use(block)
    } catch (e: LocalAdb.Failure) {
        Log.w(TAG, "ADB: ${e.reason}")
        when (e.reason) {
            LocalAdb.Reason.DEBUGGING_OFF -> Problem.DEBUGGING_OFF
            LocalAdb.Reason.PORT_CLOSED -> Problem.PORT_CLOSED
            LocalAdb.Reason.NOT_APPROVED, LocalAdb.Reason.REJECTED -> Problem.NOT_APPROVED
            LocalAdb.Reason.WIRELESS_TLS -> Problem.WIRELESS_TLS
            else -> Problem.NO_RESPONSE
        }
    } catch (e: Exception) {
        Log.w(TAG, "ADB", e)
        Problem.OTHER
    }

    private fun homeComponents(adb: LocalAdb): List<String> =
        adb.shell("cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().map(String::trim).filter(::safe)

    /** Чинний домашній екран: тримач ролі HOME серед домашніх; майстер — це насправді Google TV. */
    private fun originalHome(adb: LocalAdb, homes: List<String>): String {
        val holder = adb.shell("cmd role get-role-holders --user 0 android.app.role.HOME").trim()
        val found = homes.firstOrNull { holder.isNotBlank() && it.startsWith("$holder/") }
            ?.takeUnless { it.startsWith("${context.packageName}/") || it.startsWith("$SETUP/") }
        return found ?: homes.firstOrNull { it.startsWith("$GOOGLE_TV/") }.orEmpty()
    }

    /** Застосунки, що зберегли мережу Wi-Fi; `null` — служба Wi-Fi не відповіла. */
    private fun wifiOwners(adb: LocalAdb): Set<String>? {
        val out = adb.shell("dumpsys wifi | grep -o -E 'cname=[^ ]*|^Wi-Fi is [a-z]+'")
        if (!out.contains("cname=") && !out.contains("Wi-Fi is")) return null
        return Regex("cname=([\\w.]+)").findAll(out).map { it.groupValues[1] }.toSet()
    }

    private fun ourComponent(): String? = context.packageManager
        .queryIntentActivities(HOME_INTENT, PackageManager.MATCH_DEFAULT_ONLY)
        .firstOrNull { it.activityInfo.packageName == context.packageName }
        ?.activityInfo?.let { "${it.packageName}/${it.name}" }

    private fun resolvesToUs(): Boolean = context.packageManager
        .resolveActivity(HOME_INTENT, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName

    private companion object {
        const val TAG = "LikhtarHome"
        const val PREFS = "home_takeover"
        const val KEY_ACTIVE = "active"
        const val KEY_ORIGINAL = "original"
        const val KEY_DISABLED = "disabled"
        const val SETUP = "com.google.android.tungsten.setupwraith"
        const val GOOGLE_TV = "com.google.android.apps.tv.launcherx"
        const val APPROVAL_MS = 60_000
        const val QUIET_MS = 5_000
        val HOME_INTENT = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        /** Лише «пакет/клас» — у shell іде в лапках, тож `$` вкладених класів лишається буквою. */
        val COMPONENT = Regex("[A-Za-z][\\w.]*/[\\w.$]+")
        fun safe(component: String) = COMPONENT.matches(component)
    }
}
