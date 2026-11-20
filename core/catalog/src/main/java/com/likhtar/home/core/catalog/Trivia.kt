package com.likhtar.home.core.catalog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import androidx.core.content.edit

/**
 * Гра «Правда чи брехня». Твердження — файлом у застосунку (`assets/trivia_uk.json`, збирає
 * `tools/trivia/build.py`): однаково для обох збірок, без сервера. Тайтл уже з кадром TMDB.
 */
data class TriviaStatement(
    /** «tmdbId:номер» — стабільний, поки файл не переставили. */
    val id: String,
    val item: CatalogItem,
    val text: String,
    val truth: Boolean,
    val why: String,
)

fun parseTrivia(json: String): List<TriviaStatement> {
    val titles = JSONArray(json)
    return (0 until titles.length()).flatMap { i ->
        val t = titles.getJSONObject(i)
        val id = t.getInt("id")
        val item = CatalogItem(
            tmdbId = id,
            kind = if (t.optString("kind") == "tv") TitleKind.TV else TitleKind.MOVIE,
            title = t.optString("title"),
            year = t.optString("year"),
            backdropPath = t.optString("backdrop").takeIf { !t.isNull("backdrop") && it.isNotBlank() },
            posterPath = t.optString("poster").takeIf { !t.isNull("poster") && it.isNotBlank() },
        )
        val q = t.getJSONArray("q")
        (0 until q.length()).map { n ->
            val s = q.getJSONArray(n)
            TriviaStatement("$id:$n", item, s.getString(0), s.getBoolean(1), s.getString(2))
        }
    }
}

/** Раунд дня: однаковий для всіх, детермінований від дати; з тайтлу — одне твердження (пара себе видає). */
fun dailyRound(all: List<TriviaStatement>, date: String, size: Int = ROUND_SIZE): List<TriviaStatement> {
    val random = Random(date.hashCode())
    return all.groupBy { it.item.tmdbId to it.item.kind }.values
        .shuffled(random).take(size)
        .map { it.random(random) }
}

/** Ще питання: не бачене й не з сьогоднішнього раунду. Бачене все — коло спочатку. */
fun randomStatement(all: List<TriviaStatement>, seen: Set<String>, round: Set<String>, random: Random = Random): TriviaStatement =
    all.filter { it.id !in seen && it.id !in round }.ifEmpty { all.filter { it.id !in round }.ifEmpty { all } }.random(random)

/** Серія днів: раунд зіграно вчора — +1, сьогодні вже — без змін, інакше з одиниці. */
fun nextStreak(lastDate: String, streak: Int, today: String): Int = when (lastDate) {
    today -> streak
    shiftDay(today, -1) -> streak + 1
    else -> 1
}

private fun shiftDay(iso: String, days: Int): String = runCatching {
    java.time.LocalDate.parse(iso).plusDays(days.toLong()).toString()
}.getOrDefault("")

const val ROUND_SIZE = 10

/** Сьогоднішній раунд і що в ньому вже відповіли: `answers[i]` — чи вгадав i-те. */
data class TriviaDay(
    val date: String = "",
    val round: List<TriviaStatement> = emptyList(),
    val answers: List<Boolean> = emptyList(),
    val streak: Int = 0,
) {
    val current: TriviaStatement? get() = round.getOrNull(answers.size)
    val done: Boolean get() = round.isNotEmpty() && answers.size >= round.size
    val score: Int get() = answers.count { it }
}

/**
 * Стан гри — на приставці (SharedPreferences): відповіді раунду, бачені твердження й серія днів.
 * Таблиць лідерів і лічильників немає (спека: «не робимо»).
 */
@Singleton
class TriviaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs get() = context.getSharedPreferences("trivia", Context.MODE_PRIVATE)
    @Volatile private var all: List<TriviaStatement>? = null

    private val dayState = MutableStateFlow(TriviaDay())
    val day: StateFlow<TriviaDay> = dayState.asStateFlow()

    /** Твердження лише українською: в іншій мові інтерфейсу гри немає (порожньо — секція й слайд ховаються). */
    suspend fun statements(): List<TriviaStatement> = if (java.util.Locale.getDefault().language != "uk") emptyList() else all ?: withContext(Dispatchers.IO) {
        runCatching { parseTrivia(context.assets.open(ASSET).bufferedReader().use { it.readText() }) }
            .getOrDefault(emptyList()).also { all = it }
    }

    /** Перечитати день: опівночі раунд новий, а відповіді вчорашнього — не в рахунок. */
    suspend fun load(today: String = today()): TriviaDay {
        val round = dailyRound(statements(), today)
        val answers = if (prefs.getString(KEY_DATE, "") == today) {
            prefs.getString(KEY_ANSWERS, "").orEmpty().map { it == '1' }
        } else {
            emptyList()
        }
        val streakDate = prefs.getString(KEY_STREAK_DATE, "").orEmpty()
        // Серія жива, якщо раунд зіграно сьогодні або вчора; інакше показуємо нуль.
        val streak = if (streakDate == today || streakDate == shiftDay(today, -1)) prefs.getInt(KEY_STREAK, 0) else 0
        return TriviaDay(today, round, answers, streak).also { dayState.value = it }
    }

    /** Відповідь на питання раунду дня. */
    suspend fun answerRound(correct: Boolean) {
        val d = dayState.value.takeIf { it.date == today() && !it.done } ?: load()
        if (d.done) return
        val answers = d.answers + correct
        val edit = prefs.edit()
            .putString(KEY_DATE, d.date)
            .putString(KEY_ANSWERS, answers.joinToString("") { if (it) "1" else "0" })
        d.current?.let { edit.putStringSet(KEY_SEEN, seen() + it.id) }
        var streak = d.streak
        if (answers.size >= d.round.size) {
            streak = nextStreak(prefs.getString(KEY_STREAK_DATE, "").orEmpty(), prefs.getInt(KEY_STREAK, 0), d.date)
            edit.putInt(KEY_STREAK, streak).putString(KEY_STREAK_DATE, d.date)
        }
        edit.apply()
        dayState.update { it.copy(answers = answers, streak = streak) }
    }

    /** Ще питання поза раундом (герой Кінозалу, «Ще питання»). */
    suspend fun random(): TriviaStatement? {
        val list = statements().ifEmpty { return null }
        val round = dailyRound(list, today()).map { it.id }.toSet()
        return randomStatement(list, seen(), round)
    }

    fun markSeen(statement: TriviaStatement) {
        val seen = seen()
        val all = all.orEmpty()
        // Коло пройдено — починаємо знову, щоб набір не ріс без кінця.
        val next = if (all.isNotEmpty() && seen.size + 1 >= all.size) setOf(statement.id) else seen + statement.id
        prefs.edit { putStringSet(KEY_SEEN, next) }
    }

    private fun seen(): Set<String> = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toSet()

    private companion object {
        const val ASSET = "trivia_uk.json"
        const val KEY_DATE = "date"
        const val KEY_ANSWERS = "answers"
        const val KEY_SEEN = "seen"
        const val KEY_STREAK = "streak"
        const val KEY_STREAK_DATE = "streak_date"

        fun today(): String = java.time.LocalDate.now().toString()
    }
}
