package com.likhtar.home.core.data

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import java.util.Locale

/**
 * Мова інтерфейсу. Окремо від DataStore навмисно: її треба прочитати
 * синхронно ще в `attachBaseContext`, до першого кадру, а DataStore — асинхронний.
 *
 * Системний вибір мови застосунку є лише з Android 13, а приставки стоять і на 9–12, тому
 * мову підміняємо самі: Application і Activity обгортають свій контекст ([wrap]).
 * Порожньо — мова системи.
 */
object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY = "language"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    /** `true` — мова змінилась, екран треба перестворити. */
    fun set(context: Context, language: String): Boolean {
        if (get(context) == language) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language).commit()
        return true
    }

    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        // Увесь інтерфейс зверстано під екран 960 dp завширшки (1080p при щільності 320). На
        // приставках зі щільністю 240 (ATV9) екран «ширшав» до 1280 dp: усе дрібнішало, а що
        // стояло від верху — наїжджало на те, що від низу. Фіксуємо масштаб: 960 dp за будь-якої.
        val metrics = base.resources.displayMetrics
        val longSide = maxOf(metrics.widthPixels, metrics.heightPixels)
        if (longSide > 0) config.densityDpi = longSide * DisplayMetrics.DENSITY_DEFAULT / DESIGN_WIDTH_DP
        val language = get(base)
        if (language.isNotBlank()) {
            // Країну системи лишаємо: від неї майстер і ContentLocale беруть країну за замовчуванням.
            val locale = Locale(language, base.resources.configuration.locales[0].country)
            Locale.setDefault(locale)
            config.setLocale(locale)
        }
        return base.createConfigurationContext(config)
    }

    /** Ширина екрана в dp, під яку зверстано всі екрани. */
    private const val DESIGN_WIDTH_DP = 960

    /**
     * Нова мова — перезапуск процесу, а не лише екрана: репозиторії й ViewModel тримають
     * контекст застосунку, а він отримує мову один раз, на старті.
     */
    fun restart(activity: android.app.Activity) {
        val intent = android.content.Intent(activity, activity.javaClass)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
