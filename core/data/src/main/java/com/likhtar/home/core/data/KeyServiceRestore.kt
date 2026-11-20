package com.likhtar.home.core.data

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import com.likhtar.home.core.adb.LocalAdb
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Служба кнопок (HomeKeyService) злітає: Android прибирає службу доступності пакета, який
 * примусово зупинили (`am force-stop`, «Зупинити» в налаштуваннях, запуск зі Studio) — на
 * приставці саме так, а не встановлення; на інших прошивках — і при оновленні. HOME тоді
 * перестає вести в Ліхтар, і це помічали лише наступного дня. Повертаємо на старті
 * лаунчера й після оновлення, але тільки якщо її не вимикали свідомо ([wanted] знімає
 * `HomeKeyService.onUnbind`; примусова зупинка вбиває процес без нього). Ідея — free-my-tv
 * (`HomeReceiver`, MY_PACKAGE_REPLACED).
 */
@Singleton
class KeyServiceRestore @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Служба була увімкнена й ніхто її не вимикав. */
    var wanted: Boolean
        get() = prefs.getBoolean(KEY_WANTED, false)
        set(value) = prefs.edit { putBoolean(KEY_WANTED, value) }

    /**
     * Лише з фонового потоку. Список служб читаємо самі (без ADB): є наша — нічого не робимо.
     * Писати: дозвіл `WRITE_SECURE_SETTINGS` (видається раз через adb і переживає оновлення, як
     * у Nottilo на Fire TV) — напряму; немає — через ADB телевізора, якщо ключ уже схвалено.
     */
    @Synchronized
    fun restoreIfDropped() {
        if (!wanted) {
            Log.i(TAG, "службу кнопок вимикали свідомо — не чіпаю")
            return
        }
        val resolver = context.contentResolver
        val next = withService(Settings.Secure.getString(resolver, SETTING), "${context.packageName}/$SERVICE") ?: return
        if (!next.split(':').all(COMPONENT::matches)) {
            Log.w(TAG, "дивний список служб — не чіпаю")
            return
        }
        runCatching {
            if (context.checkSelfPermission(WRITE_SECURE) == PackageManager.PERMISSION_GRANTED) {
                Settings.Secure.putString(resolver, SETTING, next)
                Settings.Secure.putString(resolver, ENABLED, "1")
            } else {
                // Лише вже схвалений ключ: питання «Дозволити налагодження?» на кожному старті — гірше за злетілу службу.
                LocalAdb.connect(context, QUIET_MS, offerKey = false).use { adb ->
                    adb.shell("settings put secure $SETTING '$next'")
                    adb.shell("settings put secure $ENABLED 1")
                }
            }
            Log.i(TAG, "служба кнопок — знову увімкнена")
        }.onFailure { Log.w(TAG, "служба кнопок не повернулась: $it") }
    }

    private companion object {
        const val TAG = "LikhtarKeys"
        const val PREFS = "key_service"
        const val KEY_WANTED = "wanted"
        const val SETTING = "enabled_accessibility_services"
        const val ENABLED = "accessibility_enabled"
        const val WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"
        const val SERVICE = "com.likhtar.home.HomeKeyService"
        const val QUIET_MS = 5_000

        /** Лише «пакет/клас» — у shell іде в лапках. */
        val COMPONENT = Regex("[A-Za-z][\\w.]*/[\\w.$]+")
    }
}

/**
 * Новий вміст `enabled_accessibility_services` з нашою службою [component] у кінці, чужі — як були;
 * `null` — наша вже там, писати нічого.
 */
fun withService(current: String?, component: String): String? {
    val parts = current.orEmpty().split(':').map(String::trim).filter { it.isNotEmpty() && it != "null" }
    // «pkg/.Клас» — те саме, що «pkg/pkg.Клас»: інакше дописували б себе вдруге щостарту.
    return if (parts.any { full(it) == full(component) }) null else (parts + component).joinToString(":")
}

private fun full(component: String): String {
    val pkg = component.substringBefore('/')
    val cls = component.substringAfter('/')
    return if (cls.startsWith('.')) "$pkg/$pkg$cls" else component
}
