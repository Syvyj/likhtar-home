package com.likhtar.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.graphics.toArgb
import com.likhtar.home.core.data.KeyAction
import com.likhtar.home.core.data.KeyPress
import com.likhtar.home.core.data.RemoteKeyCatcher
import com.likhtar.home.core.data.RemoteKeys
import com.likhtar.home.core.data.RemoteKeysRepository
import com.likhtar.home.core.data.RemotePressTracker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Кнопки пульта: HOME → LIKHTAR і кнопки, які людина «зловила» в Налаштуваннях.
 *
 * На Google TV системний лаунчер виграє резолвінг HOME (`priority=2`), тож саму кнопку
 * досі перехоплював сторонній застосунок. Він читає пульт через `getevent` у потоках, які
 * накопичуються (15 за 3 доби, 351 % CPU на 7-й день аптайму), і підписаний на всі
 * події доступності — через це Compose тримає дерево семантики живим.
 *
 * Тут — лише фільтр клавіш: жодних подій, жодного вмісту вікон. Кнопки без дії йдуть далі
 * без змін.
 */
@AndroidEntryPoint
class HomeKeyService : AccessibilityService() {

    @Inject lateinit var keys: RemoteKeysRepository
    @Inject lateinit var catcher: RemoteKeyCatcher

    private val scope = MainScope()
    @Volatile private var bindings: Map<Pair<Int, KeyPress>, KeyAction> = emptyMap()
    private val tracker = RemotePressTracker()

    override fun onServiceConnected() {
        instance = this
        scope.launch { keys.bindings.collect { list -> bindings = list.associate { (it.keyCode to it.press) to it.action } } }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_HOME) {
            // Пару DOWN/UP забираємо цілою: віддати системі лише половину — це «утримання» HOME.
            if (event.action == KeyEvent.ACTION_UP) {
                Log.i(TAG, "HOME → LIKHTAR")
                openLikhtar()
            }
            return true
        }
        if (code in RemoteKeys.RESERVED) return false
        if (catcher.listening.value) {
            if (event.action == KeyEvent.ACTION_UP) catcher.catch(code)
            return true
        }
        val short = bindings[code to KeyPress.SHORT]
        val long = bindings[code to KeyPress.LONG]
        if (short == null && long == null) return false
        tracker.onEvent(event.action == KeyEvent.ACTION_DOWN, event.repeatCount, event.eventTime, short, long)
            ?.let(::perform)
        return true
    }

    private fun perform(action: KeyAction) {
        Log.i(TAG, "кнопка → $action")
        when (action) {
            is KeyAction.App -> {
                val intent = packageManager.getLeanbackLaunchIntentForPackage(action.packageName)
                    ?: packageManager.getLaunchIntentForPackage(action.packageName)
                    ?: return
                runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            is KeyAction.Section -> openLikhtar { putExtra(MainActivity.EXTRA_SECTION, action.route) }
            is KeyAction.Streaming -> openLikhtar { putExtra(MainActivity.EXTRA_STREAMING, action.brand) }
            KeyAction.Assistant -> openLikhtar { putExtra(MainActivity.EXTRA_ASSISTANT, true) }
        }
    }

    private fun openLikhtar(extras: Intent.() -> Unit = {}) {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply(extras),
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Нагадування асистента поверх будь-якого застосунку — вікном служби доступності (без окремого дозволу). */
    private fun reminder(text: String) {
        val wm = getSystemService(android.view.WindowManager::class.java) ?: return
        val density = resources.displayMetrics.density
        val view = android.widget.TextView(this).apply {
            this.text = getString(R.string.assistant_reminder, text)
            textSize = 22f
            setTextColor(com.likhtar.home.core.designsystem.LikhtarColors.Paper.toArgb())
            setPadding((28 * density).toInt(), (18 * density).toInt(), (28 * density).toInt(), (18 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(com.likhtar.home.core.designsystem.LikhtarColors.Background.copy(alpha = 0.95f).toArgb())
                setStroke((2 * density).toInt(), com.likhtar.home.core.designsystem.LikhtarColors.Amber.toArgb())
            }
        }
        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            y = (48 * density).toInt()
        }
        runCatching { wm.addView(view, params) }.onFailure { Log.w(TAG, "нагадування не показалось", it); return }
        view.postDelayed({ runCatching { wm.removeView(view) } }, REMINDER_MS)
    }

    companion object {
        private const val TAG = "LikhtarHomeKey"
        private const val REMINDER_MS = 20_000L

        @Volatile private var instance: HomeKeyService? = null

        /** Приспати приставку (і через HDMI-CEC — телевізор): «заблокувати екран» служби доступності. */
        fun sleepNow(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        fun showReminder(text: String): Boolean = instance?.let { it.reminder(text); true } ?: false
    }
}
