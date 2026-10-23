package com.likhtar.home.core.model

/** Канал прямого ефіру. */
data class Channel(
    val id: String,
    val name: String,
    val nowPlaying: String,
    val isLive: Boolean = true,
)

/** Слот телепрограми в сітці ТВ Ефіру. */
data class ProgramSlot(
    val channelId: String,
    val title: String,
    val startLabel: String,
)

/** Рядок телепрограми: канал + його слоти на видимий часовий діапазон. */
data class GuideRow(
    val channel: Channel,
    val slots: List<ProgramSlot>,
)

/**
 * Картка контенту в рядках Кінозалу.
 * [progress] у діапазоні 0f..1f заповнено лише для «Продовжити перегляд», [badge] — для новинок,
 * [sourceApp] показується при фокусі, щоб було видно, звідки контент.
 */
data class ContentItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val progress: Float? = null,
    val badge: String? = null,
    val sourceApp: String? = null,
    /** Постер із стрічки застосунку; `null` — малюємо кольорову заглушку. */
    val posterUri: String? = null,
    /** `intent://…` від застосунку-власника: відкриває саме цю серію, а не застосунок. */
    val intentUri: String? = null,
)

/** Погода для Головної: поточні умови + короткий прогноз. */
data class WeatherSnapshot(
    val city: String,
    val temperature: String,
    val condition: String,
    /** Поточний стан неба й вітер окремими полями — панель показує їх іконками. */
    val sky: Sky = Sky.CLEAR,
    val windMetersPerSecond: Int = 0,
    /** Найближчі години — саме їх показує Головна. */
    val hourly: List<HourForecast> = emptyList(),
    /**
     * Найближчі частини доби, що ще не минули: «День · Вечір · Ранок» і так далі по колу.
     * Саме їх показує віджет Головної — три секції замість чотирьох годин.
     */
    val upcomingParts: List<DayPart> = emptyList(),
    /** Денний прогноз: віджет його не показує, він потрібен сторінці погоди. */
    val forecast: List<DayForecast> = emptyList(),
    /** Сьогоднішня доба (мін./макс., години) — перший рядок тижня на сторінці погоди. */
    val today: DayForecast? = null,

    // ── Метрики сторінки погоди ─────────────────────────────────────────────────
    // Віджету вистачає температури й неба; сторінка показує те, заради чого в погоду
    // взагалі провалюються. Усе це Open-Meteo віддає тим самим безкоштовним запитом.

    /** «Відчувається як» — те, за чим обирають куртку, а не термометр. */
    val apparentTemperature: String = "",
    val humidityPercent: Int = 0,
    /** Тиск біля поверхні, гПа. */
    val pressureHpa: Int = 0,
    val cloudCoverPercent: Int = 0,
    val windGustMetersPerSecond: Int = 0,
    val uvIndex: Int = 0,
    /** «06:12» — локальний час; порожньо, якщо джерело не дало. */
    val sunrise: String = "",
    val sunset: String = "",
)

data class DayForecast(
    val day: String,
    /** Максимум доби — його показує і компактний рядок, і сторінка. */
    val temperature: String,
    /** Мінімум доби; порожньо, поки джерело його не дає. */
    val minTemperature: String = "",
    val sky: Sky = Sky.CLEAR,
    val precipitationPercent: Int = 0,
    /** Максимальний вітер доби, м/с. */
    val windMetersPerSecond: Int = 0,
    /** Максимальний УФ-індекс доби. */
    val uvIndex: Int = 0,
    /** Ранок / день / вечір. Порожньо, якщо погодинних даних на цей день уже немає. */
    val parts: List<DayPart> = emptyList(),
    /** Усі години цієї доби — крива дня на сторінці погоди. */
    val hours: List<HourForecast> = emptyList(),
)

/** Частина доби у прогнозі на день. */
data class DayPart(
    val title: String,
    val temperature: String,
    val sky: Sky,
    /** «сьогодні» / «завтра» — потрібне віджету, коли вікна переходять через добу. */
    val day: String = "",
    /** Вечірнє вікно — для нічної іконки; [title] уже мовою інтерфейсу, порівнювати його не можна. */
    val evening: Boolean = false,
)

/**
 * Одна година прогнозу.
 *
 * [precipitationPercent] — ймовірність опадів, а не їх кількість: на екрані з дивана
 * «60 %» зрозуміліше за «0,4 мм».
 */
data class HourForecast(
    val time: String,
    val temperature: String,
    val sky: Sky,
    val precipitationPercent: Int,
    val windMetersPerSecond: Int = 0,
    /** Відчувається як — сторінка показує його поруч із фактичною температурою. */
    val apparentTemperature: String = "",
    val humidityPercent: Int = 0,
    /** Ніч за Open-Meteo (`is_day = 0`); `null` — дані старші, тоді вгадуємо за годиною. */
    val night: Boolean? = null,
)

/** Стан неба, зведений до трьох випадків — рівно стільки розрізняє іконка на екрані. */
enum class Sky { CLEAR, CLOUDY, RAIN }
