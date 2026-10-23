package com.likhtar.home

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.navigation.OpenRequest
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.likhtar.home.core.designsystem.LikhtarTheme
import com.likhtar.home.navigation.LikhtarApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Єдина активність лаунчера. Оголошена як CATEGORY_HOME, тому система може її запускати
 * як домашній екран; уся навігація всередині — Compose-маршрути, без додаткових активностей.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /** Кнопка пульта → розділ LIKHTAR (маршрут [com.likhtar.home.core.model.LikhtarSection]). */
        const val EXTRA_SECTION = "likhtar.section"

        /** Кнопка пульта → сторінка стрімінгу в Кінозалі (ключ сервера). */
        const val EXTRA_STREAMING = "likhtar.streaming"

        /** Кнопка пульта → голосовий асистент. */
        const val EXTRA_ASSISTANT = "likhtar.assistant"

        /** Запитання асистенту текстом — без слухання й без голосу у відповідь. */
        const val EXTRA_ASSISTANT_QUESTION = "likhtar.assistant.question"

        /** Поріг між двома стрілками. Менше — не рука: заміряно 250 мс мінімум. */
        private const val ArrowGapMs = 70L

        private val ARROW_KEYS = setOf(
            android.view.KeyEvent.KEYCODE_DPAD_UP,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }

    /** Кожне натискання Home при живому процесі: активність не перестворюється. */
    private val homeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Куди відкрити лаунчер з кнопки пульта. Канал, а не SharedFlow: на холодному старті
     * запит приходить у `onCreate`, ще до того, як Compose почне слухати, — і не має губитись.
     */
    private val openRequests = Channel<OpenRequest>(Channel.CONFLATED)

    /**
     * Мітка останньої дії користувача. Береться з `onUserInteraction`, бо це єдине місце,
     * куди система повідомляє про будь-яке натискання пульта — незалежно від того, який
     * елемент Compose його врешті обробив.
     */
    private val interactions = MutableStateFlow(0L)

    /**
     * Автоповтор стрілок не доходить до екранів.
     *
     * Заміряно на приставці, два логи поруч — драйвер і вхід у застосунок. На 85
     * фізичних натискань застосунок отримав 149 подій ACTION_DOWN, з них 80 — повтори.
     * Система робить їх навіть для натискання тривалістю 194 мс, а `UP` приходить ПІСЛЯ
     * них: його `eventTime` старіший за `eventTime` повторів, тобто палець кнопку вже
     * відпустив, а повтори ще йшли. Фокус через це за одне клацання пролітав кілька
     * рядів або кілька карток — на всіх екранах, не лише в Кінозалі.
     *
     * Ціна рішення: утримання стрілки більше не гортає список — один рух на одне
     * натискання. На цій прошивці утримання все одно було некерованим, а передбачуваність
     * тут важливіша. Якщо колись знадобиться прокрутка утриманням, її треба робити
     * власним таймером, а не довіряти системному повтору.
     *
     * Тут, а не в окремому екрані: `dispatchKeyEvent` — єдина точка, крізь яку проходить
     * усе, що приставка шле застосунку.
     */
    // RestrictedApi — хибний: androidx позначає власне перевизначення в ComponentActivity,
    // а сам метод публічний в Activity, і перевизначати його — штатний шлях.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val isArrow = event.keyCode in ARROW_KEYS
        if (isArrow && event.action == android.view.KeyEvent.ACTION_DOWN) {
            // ЗАМІР: лишається, доки не з'ясуємо, що саме приходить після виходу зі сну.
            android.util.Log.d(
                "LikhtarKeys",
                "код=${event.keyCode} повтор=${event.repeatCount} час=${event.eventTime} " +
                    "пристрій=${event.deviceId} джерело=${event.source}",
            )
            if (event.repeatCount > 0) return true
            // Друга межа — на випадок, коли система шле повтор як НОВЕ натискання
            // (repeatCount = 0 щоразу). Заміряно на пульті: найкоротший проміжок між
            // двома справжніми натисканнями — 250 мс, тож усе, що ближче за поріг, — не
            // рука. Рахуємо за `eventTime`, тобто за миттю самої події, а не за миттю
            // вручення: після сну вручення відстає, і годинник доставки збрехав би.
            val now = event.eventTime
            if (now - lastArrowAt in 0 until ArrowGapMs) return true
            lastArrowAt = now
        }
        // Стрілка, з якою Compose нічого не зробив (униз із доку, де нижче нічого немає),
        // далі дістається системі: ViewRootImpl сам шукає фокус, «перезаходить» у вікно
        // й ставить фокус на сусіда по ряду — ↓ їздив по доку й рядах Застосунків
        // (на приставці). Стрілки цілком наші: неопрацьована — просто нічого.
        return super.dispatchKeyEvent(event) || isArrow
    }

    /** Коли востаннє пропустили стрілку. `eventTime`, а не годинник вручення. */
    private var lastArrowAt = 0L

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.likhtar.home.core.data.AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Стан навмисно не відновлюємо (`null` замість savedInstanceState): інакше Android
        // після перезапуску повертає збережений back stack NavHost'а і відкриває розділ,
        // у якому користувач був востаннє, перебиваючи налаштування «Поведінка запуску».
        // Домашній екран не має «попереднього стану» — він завжди починається спочатку.
        super.onCreate(null)
        intent?.openRequest()?.let(openRequests::trySend)
        setContent {
            LikhtarTheme {
                LikhtarApp(homeRequests = homeRequests, openRequests = openRequests.receiveAsFlow(), interactions = interactions)
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        interactions.value = SystemClock.uptimeMillis()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode=singleTask: повторний запуск лаунчера приходить сюди, а не в onCreate,
        // тому скидання на стартовий розділ робимо звідси.
        //
        // Реагуємо на будь-який ACTION_MAIN, а не лише на CATEGORY_HOME. Причина з приставки:
        // там, де кнопку Home забрала система, її перепризначають ремапером — а той шле
        // звичайний launch-інтент (CATEGORY_LAUNCHER). З перевіркою на CATEGORY_HOME такий
        // запит мовчки губився, і кнопка «додому» зсередини лаунчера не робила нічого.
        if (intent.action == Intent.ACTION_MAIN) {
            intent.openRequest()?.let(openRequests::trySend) ?: homeRequests.tryEmit(Unit)
        }
    }
}

private fun Intent.openRequest(): OpenRequest? =
    OpenRequest.Assistant(getStringExtra(MainActivity.EXTRA_ASSISTANT_QUESTION))
        .takeIf { getBooleanExtra(MainActivity.EXTRA_ASSISTANT, false) || it.question != null }
        ?: getStringExtra(MainActivity.EXTRA_STREAMING)?.let(OpenRequest::Streaming)
        ?: getStringExtra(MainActivity.EXTRA_SECTION)?.let(LikhtarSection::fromRoute)?.let(OpenRequest::Section)
