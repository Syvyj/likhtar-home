package com.likhtar.home.core.data

import com.likhtar.home.core.model.Sky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Вибір найближчих годин для банера погоди.
 *
 * Перевіряємо саме цю функцію, бо її помилка тиха: зсув на годину виглядає як звичайний
 * прогноз, просто не той, і на екрані це нічим не видно.
 */
class WeatherHoursTest {

    private val times = listOf(
        "2026-08-15T16:00",
        "2026-08-15T17:00",
        "2026-08-15T18:00",
        "2026-08-15T19:00",
        "2026-08-15T20:00",
        "2026-08-15T21:00",
    )
    private val temperatures = listOf(27.0, 26.6, 25.5, 24.4, 22.5, 20.5)
    private val codes = listOf(0, 0, 1, 3, 61, 80)
    private val chances = listOf(0, 5, 5, 20, 60, 80)

    private fun pick(currentTime: String, count: Int = 4) = nextHours(
        times = times,
        temperatures = temperatures,
        codes = codes,
        chances = chances,
        currentTime = currentTime,
        count = count,
    )

    @Test
    fun `бере години суворо після поточної`() {
        val hours = pick("2026-08-15T17:45")

        assertEquals(listOf("18:00", "19:00", "20:00", "21:00"), hours.map { it.time })
    }

    @Test
    fun `рівно та сама мітка часу вважається минулою`() {
        // Open-Meteo віддає current.time округленим до хвилин, і воно збігається з міткою
        // години рівно о рівній годині. Показувати «18:00» о 18:00 — це вже не прогноз.
        val hours = pick("2026-08-15T18:00")

        assertEquals("19:00", hours.first().time)
    }

    @Test
    fun `температура форматується зі знаком, відсотки беруться як є`() {
        val hours = pick("2026-08-15T17:45")

        assertEquals(listOf("+26°", "+24°", "+23°", "+21°"), hours.map { it.temperature })
        assertEquals(listOf(5, 20, 60, 80), hours.map { it.precipitationPercent })
    }

    @Test
    fun `код WMO зводиться до трьох станів неба`() {
        val hours = pick("2026-08-15T17:45")

        assertEquals(listOf(Sky.CLEAR, Sky.CLOUDY, Sky.RAIN, Sky.RAIN), hours.map { it.sky })
    }

    @Test
    fun `наприкінці доби віддаємо скільки є, а не падаємо`() {
        val hours = pick("2026-08-15T20:30")

        assertEquals(listOf("21:00"), hours.map { it.time })
    }

    @Test
    fun `коли попереду годин немає — порожньо`() {
        assertTrue(pick("2026-08-15T23:00").isEmpty())
    }
}

/**
 * Ранок / день / вечір для картки дня.
 *
 * Помилка тут теж тиха: зсунуте на годину вікно дає правдоподібні числа, просто не ті,
 * а «вечір» із денною температурою ніяк не видно на екрані.
 */
class DayPartsTest {

    private val times = buildList {
        // Дві доби поспіль — щоб перевірити, що чужий день не потрапляє у вибірку.
        for (day in listOf("2026-08-16", "2026-08-17")) {
            for (hour in 0..23) add("${day}T%02d:00".format(hour))
        }
    }
    // Температура = номер години, щоб середнє легко рахувалося очима.
    private val temperatures = times.map { it.substringAfter('T').take(2).toDouble() }
    private val codes = times.map { 0 }

    @Test
    fun `три частини доби з правильними межами`() {
        val parts = dayParts(times, temperatures, codes, "2026-08-16")

        assertEquals(listOf("Ранок", "День", "Вечір"), parts.map { it.title })
        // 6..11 → середнє 8.5 → +9; 12..17 → 14.5 → +15; 18..23 → 20.5 → +21.
        assertEquals(listOf("+9°", "+15°", "+21°"), parts.map { it.temperature })
    }

    @Test
    fun `сусідній день не потрапляє у вибірку`() {
        val parts = dayParts(times, temperatures, codes, "2026-08-17")

        assertEquals(listOf("+9°", "+15°", "+21°"), parts.map { it.temperature })
    }

    @Test
    fun `стан неба береться найгірший у вікні`() {
        // Одна дощова година в ранковому вікні важливіша за п'ять ясних.
        val rainy = times.mapIndexed { index, stamp ->
            if (stamp == "2026-08-16T07:00") 61 else 0
        }
        val parts = dayParts(times, temperatures, rainy, "2026-08-16")

        assertEquals(Sky.RAIN, parts.first { it.title == "Ранок" }.sky)
        assertEquals(Sky.CLEAR, parts.first { it.title == "День" }.sky)
    }

    @Test
    fun `дня без погодинних даних просто немає`() {
        assertTrue(dayParts(times, temperatures, codes, "2026-08-20").isEmpty())
    }
}

/** Іконка години: ніч — за `is_day` Open-Meteo, а не за правилом «до 6 — ніч». */
class WeatherNightTest {
    private val times = listOf("2026-10-03T05:00", "2026-10-03T06:00", "2026-10-03T07:00")

    @Test
    fun `ніч — за is_day, а не за годинником`() {
        val hours = nextHours(
            times = times, temperatures = listOf(8.0, 8.0, 8.0), codes = listOf(0, 0, 0),
            chances = listOf(0, 0, 0), currentTime = "2026-10-03T04:00", count = 3,
            days = listOf(0, 0, 1),
        )
        // Схід о 06:40: о шостій ще ніч.
        assertEquals(listOf(true, true, false), hours.map { it.night })
    }

    @Test
    fun `без is_day ніч невідома`() {
        val hours = nextHours(
            times = times, temperatures = listOf(8.0, 8.0, 8.0), codes = listOf(0, 0, 0),
            chances = listOf(0, 0, 0), currentTime = "2026-10-03T04:00", count = 3,
        )
        assertEquals(listOf(null, null, null), hours.map { it.night })
    }
}
