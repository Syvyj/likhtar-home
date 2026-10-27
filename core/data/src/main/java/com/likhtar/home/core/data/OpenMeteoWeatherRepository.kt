package com.likhtar.home.core.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.likhtar.home.core.model.DayForecast
import com.likhtar.home.core.model.DayPart
import com.likhtar.home.core.model.HourForecast
import com.likhtar.home.core.model.Sky
import com.likhtar.home.core.model.WeatherSnapshot
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Погода з Open-Meteo — без API-ключа та реєстрації, тому нічого не треба зберігати в застосунку.
 *
 * Мережа на приставці буває недоступна саме в момент старту лаунчера, тому будь-яка помилка
 * тихо віддає попередній успішний знімок, а якщо його ще немає — mock. Домашній екран не має
 * права показати помилку замість погоди.
 */
@Singleton
class OpenMeteoWeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val geocoding: GeocodingRepository,
) : WeatherRepository {

    /** Кеш одним полем: місто, час і знімок пишуться разом, тож і розсинхронитись не можуть. */
    @Volatile private var cached: Cached? = null

    private data class Cached(val place: String, val atMs: Long, val snapshot: WeatherSnapshot)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun weather(): Flow<WeatherSnapshot> = settingsRepository.settings
        .map { Place(it.weatherCity, it.weatherLatitude, it.weatherLongitude) }
        .distinctUntilChanged()
        .flatMapLatest { place -> forPlace(place) }
        .flowOn(Dispatchers.IO)

    private fun forPlace(place: Place): Flow<WeatherSnapshot> = flow {
        // Місто ще не вибрано — беремо з часового поясу пристрою («Europe/Warsaw» → Warsaw) і
        // запам'ятовуємо: без заводського Києва й без таблиць країн. Запис у
        // налаштування поверне сюди вже з містом.
        if (place.city.isBlank()) {
            timeZoneCity()?.let { geocoding.search(it).firstOrNull() }
                ?.let { settingsRepository.setWeatherPlace(it.name, it.latitude, it.longitude) }
            return@flow
        }
        val hit = cached?.takeIf { it.place == place.city }
        // Перша емісія — кеш, якщо він є. Вигаданої погоди-заглушки більше не показуємо
        //: Головна вміє чекати без неї.
        hit?.let { emit(it.snapshot) }

        val now = System.currentTimeMillis()
        if (hit != null && now - hit.atMs < CACHE_MS) return@flow

        // Без мережі (приставка стартувала раніше за Wi-Fi) — дочекатися її й спитати ще раз,
        // а не мовчати до наступного оновлення.
        val fetched = runCatching { fetch(place) }
            .onFailure { Log.w(TAG, "Не вдалося отримати погоду", it) }
            .getOrNull()
            ?: run {
                awaitOnline(context)
                runCatching { fetch(place) }.getOrNull()
            }
            ?: return@flow

        cached = Cached(place = place.city, atMs = now, snapshot = fetched)
        emit(fetched)
    }

    private fun fetch(place: Place): WeatherSnapshot {
        val endpoint = "$FORECAST?latitude=${place.latitude}&longitude=${place.longitude}$FORECAST_TAIL"
        val body = http(endpoint, TIMEOUT_MS) { it.text() }

        val json = JSONObject(body)
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val days = daily.getJSONArray("time")
        val maxTemps = daily.getJSONArray("temperature_2m_max")
        val minTemps = daily.optJSONArray("temperature_2m_min")
        val dayCodes = daily.optJSONArray("weather_code")
        val dayChances = daily.optJSONArray("precipitation_probability_max")
        val uvMax = daily.optJSONArray("uv_index_max")
        val dayWinds = daily.optJSONArray("wind_speed_10m_max")
        val sunrises = daily.optJSONArray("sunrise")
        val sunsets = daily.optJSONArray("sunset")
        val hourlyJson = json.getJSONObject("hourly")
        val allHours = Hours(hourlyJson)

        val dayAt = { index: Int ->
            val date = days.getString(index)
            DayForecast(
                day = weekday(date),
                temperature = formatTemperature(maxTemps.getDouble(index)),
                minTemperature = minTemps?.let { formatTemperature(it.optDouble(index, 0.0)) }.orEmpty(),
                sky = skyOf(dayCodes?.optInt(index, 0) ?: 0),
                precipitationPercent = dayChances?.optInt(index, 0) ?: 0,
                windMetersPerSecond = dayWinds?.optDouble(index, 0.0)?.roundToInt() ?: 0,
                uvIndex = uvMax?.optDouble(index, 0.0)?.roundToInt() ?: 0,
                parts = dayParts(allHours.times, allHours.temperatures, allHours.codes, date).map(::localized),
                hours = dayHours(allHours, date),
            )
        }
        val forecast = (1 until minOf(days.length(), FORECAST_DAYS + 1)).map(dayAt)
        return WeatherSnapshot(
            hourly = nextHours(
                times = allHours.times,
                temperatures = allHours.temperatures,
                codes = allHours.codes,
                chances = allHours.chances,
                winds = allHours.winds,
                apparents = allHours.apparents,
                humidities = allHours.humidities,
                currentTime = current.getString("time"),
                count = HOURLY_HOURS,
                days = allHours.days,
            ),
            upcomingParts = upcomingParts(
                times = allHours.times,
                temperatures = allHours.temperatures,
                codes = allHours.codes,
                days = (0 until days.length()).map(days::getString),
                currentTime = current.getString("time"),
            ).map(::localized),
            city = place.city,
            temperature = formatTemperature(current.getDouble("temperature_2m")),
            condition = context.getString(
                R.string.data_weather_condition_wind,
                conditionText(current.getInt("weather_code")),
                current.getDouble("wind_speed_10m").roundToInt(),
            ),
            sky = skyOf(current.getInt("weather_code")),
            windMetersPerSecond = current.getDouble("wind_speed_10m").roundToInt(),
            forecast = forecast,
            today = dayAt(0),
            apparentTemperature = formatTemperature(
                current.optDouble("apparent_temperature", current.getDouble("temperature_2m")),
            ),
            humidityPercent = current.optInt("relative_humidity_2m", 0),
            pressureHpa = current.optDouble("surface_pressure", 0.0).roundToInt(),
            cloudCoverPercent = current.optInt("cloud_cover", 0),
            windGustMetersPerSecond = current.optDouble("wind_gusts_10m", 0.0).roundToInt(),
            // Поточний УФ, а не денний максимум: о третій ночі стояло «УФ-індекс 4».
            uvIndex = current.optDouble("uv_index", uvMax?.optDouble(0, 0.0) ?: 0.0).roundToInt(),
            sunrise = sunrises?.optString(0).orEmpty().substringAfter('T', ""),
            sunset = sunsets?.optString(0).orEmpty().substringAfter('T', ""),
        )
    }

    /** JSON лише розпаковуємо — вибірки живуть у чистих функціях, бо саме там можна помилитися. */
    private class Hours(json: JSONObject) {
        private val timeArray = json.getJSONArray("time")
        private val tempArray = json.getJSONArray("temperature_2m")
        private val codeArray = json.getJSONArray("weather_code")
        private val chanceArray = json.getJSONArray("precipitation_probability")
        private val windArray = json.getJSONArray("wind_speed_10m")
        private val apparentArray = json.optJSONArray("apparent_temperature")
        private val humidityArray = json.optJSONArray("relative_humidity_2m")
        private val dayArray = json.optJSONArray("is_day")

        val times = (0 until timeArray.length()).map(timeArray::getString)
        val temperatures = (0 until tempArray.length()).map(tempArray::getDouble)
        val codes = (0 until codeArray.length()).map(codeArray::getInt)
        val chances = (0 until chanceArray.length()).map { chanceArray.optInt(it, 0) }
        val winds = (0 until windArray.length()).map { windArray.optDouble(it, 0.0) }
        val apparents = (0 until timeArray.length()).map { apparentArray?.optDouble(it, 0.0) ?: 0.0 }
        val humidities = (0 until timeArray.length()).map { humidityArray?.optInt(it, 0) ?: 0 }
        /** 1 — день, 0 — ніч; порожньо, якщо поля немає (тоді ніч угадується за годиною). */
        val days = dayArray?.let { a -> (0 until a.length()).map { a.optInt(it, 1) } }.orEmpty()
    }

    private fun formatTemperature(celsius: Double): String = formatCelsius(celsius)

    /** Години доби [date] — та сама розкладка, що й «найближчі години», лише без зсуву від зараз. */
    private fun dayHours(h: Hours, date: String): List<HourForecast> {
        val first = h.times.indexOfFirst { it.startsWith(date) }.takeIf { it >= 0 } ?: return emptyList()
        return nextHours(
            times = h.times, temperatures = h.temperatures, codes = h.codes, chances = h.chances,
            winds = h.winds, apparents = h.apparents, humidities = h.humidities,
            currentTime = h.times.getOrNull(first - 1) ?: "", count = 24, days = h.days,
        )
    }

    private fun weekday(isoDate: String): String {
        // Локальні екземпляри: SimpleDateFormat не потокобезпечний, а прогнози
        // будуються з паралельних корутин.
        val date = runCatching { SimpleDateFormat("yyyy-MM-dd", UA).parse(isoDate) }.getOrNull() ?: return ""
        return SimpleDateFormat("EEE", Locale.getDefault()).format(date).replaceFirstChar { it.uppercase() }.take(2)
    }

    /** Коди WMO, які реально трапляються в Україні; решта — узагальнено. */
    private fun conditionText(code: Int): String = context.getString(
        when (code) {
            0 -> R.string.data_weather_clear
            1, 2 -> R.string.data_weather_partly_cloudy
            3 -> R.string.data_weather_cloudy
            in 45..48 -> R.string.data_weather_fog
            in 51..57 -> R.string.data_weather_drizzle
            in 61..67 -> R.string.data_weather_rain
            in 71..77 -> R.string.data_weather_snow
            in 80..82 -> R.string.data_weather_showers
            in 85..86 -> R.string.data_weather_snowfall
            in 95..99 -> R.string.data_weather_thunderstorm
            else -> R.string.data_weather_partly_cloudy
        },
    )

    /** Частина доби мовою інтерфейсу: [dayParts] і [upcomingParts] говорять українськими ключами. */
    private fun localized(part: DayPart): DayPart = part.copy(
        title = when (DAY_WINDOWS.indexOfFirst { it.first == part.title }) {
            0 -> context.getString(R.string.data_daypart_morning)
            1 -> context.getString(R.string.data_daypart_day)
            2 -> context.getString(R.string.data_daypart_evening)
            else -> part.title
        },
        day = when (part.day) {
            TODAY -> context.getString(R.string.data_today)
            TOMORROW -> context.getString(R.string.data_tomorrow)
            else -> part.day
        },
    )

    private data class Place(val city: String, val latitude: Double, val longitude: Double)

    private companion object {
        const val TAG = "LikhtarWeather"
        const val CACHE_MS = 15 * 60 * 1000L
        const val TIMEOUT_MS = 5_000
        const val FORECAST_DAYS = 7
        // Картка Головної показує 3 години, сторінка погоди — добу вперед.
        const val HOURLY_HOURS = 24

        const val FORECAST = "https://api.open-meteo.com/v1/forecast"
        /**
         * Один запит на все: віджету вистачає перших трьох полів, решту показує сторінка
         * погоди. Open-Meteo не бере грошей за додаткові змінні й не сповільнюється від
         * них помітно, тому другого запиту заради сторінки не робимо.
         */
        const val FORECAST_TAIL = "&current=temperature_2m,apparent_temperature,weather_code," +
            "wind_speed_10m,wind_gusts_10m,relative_humidity_2m,surface_pressure,cloud_cover,uv_index" +
            "&hourly=temperature_2m,apparent_temperature,weather_code," +
            "precipitation_probability,wind_speed_10m,relative_humidity_2m,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min,weather_code," +
            "precipitation_probability_max,uv_index_max,wind_speed_10m_max,sunrise,sunset" +
            "&timezone=auto&forecast_days=8"

        val UA = Locale("uk", "UA")
    }
}

/**
 * Наступні [count] годин після [currentTime].
 *
 * Час порівнюється рядками: ISO-мітки Open-Meteo (`2026-08-15T18:00`) уже відсортовані й
 * лежать у часовому поясі точки (`timezone=auto`), тому лексикографічне порівняння дає той
 * самий порядок, що й хронологічне, і не тягне розбір дати.
 *
 * Функція чиста й окрема від JSON саме тому, що помилка тут тиха: зсув на годину виглядає
 * як робочий прогноз, просто не той.
 */
internal fun nextHours(
    times: List<String>,
    temperatures: List<Double>,
    codes: List<Int>,
    chances: List<Int>,
    winds: List<Double> = emptyList(),
    apparents: List<Double> = emptyList(),
    humidities: List<Int> = emptyList(),
    currentTime: String,
    count: Int,
    days: List<Int> = emptyList(),
): List<HourForecast> {
    val start = times.indexOfFirst { it > currentTime }.takeIf { it >= 0 } ?: return emptyList()
    // Масиви Open-Meteo однакової довжини, але коротший за інші обрізає вибірку, а не падає:
    // домашній екран не має права впасти через неповну відповідь погоди.
    val last = minOf(times.size, temperatures.size, codes.size, chances.size)

    return (start until minOf(last, start + count)).map { index ->
        HourForecast(
            time = times[index].substringAfter('T').take(5),
            temperature = formatCelsius(temperatures[index]),
            sky = skyOf(codes[index]),
            precipitationPercent = chances[index],
            windMetersPerSecond = winds.getOrNull(index)?.roundToInt() ?: 0,
            apparentTemperature = apparents.getOrNull(index)?.let(::formatCelsius).orEmpty(),
            humidityPercent = humidities.getOrNull(index) ?: 0,
            night = days.getOrNull(index)?.let { it == 0 },
        )
    }
}

/**
 * Ранок / день / вечір для конкретної дати.
 *
 * Межі свідомо грубі — 6–11, 12–17, 18–23: людина дивиться на це, щоб вирішити, коли
 * виходити, а не щоб знати температуру о 14:00. Температура частини — середня по вікну,
 * стан неба — найгірший із трьох (дощ важливіший за те, що дві години поспіль було ясно).
 */
internal fun dayParts(
    times: List<String>,
    temperatures: List<Double>,
    codes: List<Int>,
    date: String,
): List<DayPart> {
    val last = minOf(times.size, temperatures.size, codes.size)

    return DAY_WINDOWS.mapNotNull { (title, hours) ->
        val indices = (0 until last).filter { index ->
            val stamp = times[index]
            stamp.startsWith(date) && stamp.substringAfter('T').take(2).toIntOrNull() in hours
        }
        if (indices.isEmpty()) return@mapNotNull null

        DayPart(
            title = title,
            temperature = formatCelsius(indices.sumOf { temperatures[it] } / indices.size),
            sky = indices.map { skyOf(codes[it]) }.maxByOrNull { it.ordinal } ?: Sky.CLEAR,
            evening = hours == DAY_WINDOWS.last().second,
        )
    }
}

internal val DAY_WINDOWS = listOf("Ранок" to 6..11, "День" to 12..17, "Вечір" to 18..23)
private const val TODAY = "сьогодні"
private const val TOMORROW = "завтра"

/**
 * Три найближчі частини доби: удень це «День · Вечір · Ранок (завтра)», уночі —
 * «Ранок · День · Вечір» уже сьогоднішні.
 *
 * Вікно, яке ще триває, лишається першим: людина дивиться на це, щоб зрозуміти, що буде
 * далі сьогодні, а не щоб побачити, яким був ранок.
 */
internal fun upcomingParts(
    times: List<String>,
    temperatures: List<Double>,
    codes: List<Int>,
    days: List<String>,
    currentTime: String,
    count: Int = 3,
): List<DayPart> {
    val today = currentTime.substringBefore('T')
    val hourNow = currentTime.substringAfter('T').take(2).toIntOrNull() ?: 0

    return days.flatMapIndexed { dayIndex, date ->
        dayParts(times, temperatures, codes, date)
            // Минулі вікна поточної доби відкидаємо; майбутні дні беремо цілком.
            .filter { part ->
                if (date != today) return@filter true
                val window = DAY_WINDOWS.firstOrNull { it.first == part.title }?.second
                window == null || hourNow <= window.last
            }
            .map { it.copy(day = if (dayIndex == 0 && date == today) TODAY else TOMORROW) }
    }.take(count)
}

/** Іконка розрізняє лише три стани, тому коди WMO зводимо саме до них. */
internal fun skyOf(code: Int): Sky = when (code) {
    0, 1 -> Sky.CLEAR
    2, 3, in 45..48 -> Sky.CLOUDY
    else -> Sky.RAIN
}

private fun formatCelsius(celsius: Double): String {
    val rounded = celsius.roundToInt()
    return if (rounded > 0) "+$rounded°" else "$rounded°"
}

/** Місто часового поясу: «America/New_York» → «New York»; «Etc/UTC» і подібне — null. */
internal fun timeZoneCity(zone: String = java.util.TimeZone.getDefault().id): String? =
    zone.substringAfterLast('/', "").replace('_', ' ')
        .takeIf { '/' in zone && !zone.startsWith("Etc/") && it.isNotBlank() }
