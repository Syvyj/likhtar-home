package com.likhtar.home.core.catalog

import android.content.Context
import com.likhtar.home.core.data.PersonalAi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

/**
 * Ліхтарик публічної збірки — на AI самої людини ([PersonalAi]), без нашого сервера: вільний
 * запит, поради за переглянутим і переказ сезону. Промпти — порт `ask.go` і `recap.go` з
 * сервера мовою інтерфейсу. Кожну назву від моделі звіряємо з TMDB (назва збігається
 * дослівно, спершу з роком) — вигаданий фільм людина не побачить.
 */
@Singleton
class OwnAiAdvisor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ai: PersonalAi,
    private val tmdb: TmdbClient,
    private val config: CatalogConfig,
) {
    suspend fun ready(): Boolean = ai.ready()

    fun ask(query: String): Flow<AskEvent> = flow {
        val (thought, found) = advise(ASK_PROMPT, query.trim()) ?: return@flow
        if (thought.isNotBlank()) emit(AskEvent.Thought(thought))
        found.forEachIndexed { rank, (item, why) -> emit(AskEvent.Found(rank, item, why)) }
    }

    suspend fun advise(like: List<String>): Advice {
        val (thought, found) = advise(ADVISE_PROMPT, like.joinToString("\n")) ?: return Advice()
        return Advice(thought, items = found.map { it.first }, why = found.associate { it.first.id to it.second })
    }

    /** Переказ сезону за описами серій з TMDB — як `/api/recap`, але моделлю людини. */
    suspend fun recap(tmdbId: Int, season: Int): Pair<String, String> {
        if (!ai.ready()) return "" to context.getString(R.string.catalog_recap_no_ai)
        val show = tmdb.card(tmdbId, TitleKind.TV) ?: return "" to context.getString(R.string.catalog_recap_failed)
        val lines = tmdb.seasonEpisodes(show, season)
            .filter { it.overview.isNotBlank() }
            .map { "Episode ${it.number} «${it.title}»: ${it.overview}" }
        if (lines.size < 2) return "" to context.getString(R.string.catalog_recap_no_episodes)
        val prompt = "Retell season $season of the series «${show.title}» (${show.originalTitle}) in ${language()} for someone " +
            "who wants to remember it before the next season. Use ONLY the episode summaries below; invent nothing and add " +
            "nothing from later seasons. Lively and coherent, like telling a friend: 180–260 words, 2–4 paragraphs, the main " +
            "characters, the twists and how the season ended. No headings or lists. Reply with the retelling only."
        val text = runCatching { ai.chat(prompt, lines.joinToString("\n"), maxTokens = 3_000) }.getOrNull()
            ?: return "" to context.getString(R.string.catalog_recap_no_reply)
        return text.trim() to context.getString(R.string.catalog_recap_failed)
    }

    /** Думка й підтверджені TMDB картки з «чому»; `null` — AI не налаштовано чи не відповів. */
    private suspend fun advise(prompt: String, input: String): Pair<String, List<Pair<CatalogItem, String>>>? {
        if (input.isBlank()) return null
        val reply = runCatching { ai.chat(prompt.replace("{language}", language()), input) }.getOrNull() ?: return null
        val json = PersonalAi.jsonIn(reply) ?: return null
        val more = json.optJSONArray("more")
        val titles = listOfNotNull(json.optJSONObject("best")) +
            (0 until (more?.length() ?: 0)).mapNotNull { more?.optJSONObject(it) }
        val found = coroutineScope { titles.map { async { confirm(it) } }.awaitAll() }
            .filterNotNull()
            .distinctBy { it.first.id }
        return json.optString("thought") to found
    }

    private suspend fun confirm(t: JSONObject): Pair<CatalogItem, String>? {
        val title = t.optString("title").takeIf { it.isNotBlank() } ?: return null
        val kind = if (t.optString("kind") == "tv") TitleKind.TV else TitleKind.MOVIE
        val want = alnumLower(title)
        for (year in listOf(t.optInt("year"), 0).distinct()) {
            val hit = runCatching { tmdb.searchKind(kind, title, year) }.getOrDefault(emptyList())
                .firstOrNull { alnumLower(it.title) == want || alnumLower(it.originalTitle) == want }
            if (hit != null) return hit to t.optString("why")
        }
        return null
    }

    private suspend fun language(): String =
        Locale(config.locale().language).getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }

    companion object {
        /** Лише літери й цифри в нижньому регістрі: «Spider-Man: No Way Home» = «spidermannowayhome». */
        fun alnumLower(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

        // Порядок полів важливий (ask.go): коли модель спершу пише думку, вона вигадує назви.
        private const val ASK_PROMPT = "You are Likhtaryk, a film advisor inside a family's TV. The person writes a title, a plot " +
            "description, a mood or a request. A DESCRIPTION of a specific work (plot, characters, a detail) — like a quiz: which " +
            "single best-known work is exactly about this? That is best, and more are sequels or very similar ones. A TITLE — best " +
            "is that very work, more are its other parts and similar ones. A REQUEST for advice (\"a comedy for tonight\", " +
            "\"something like …\", \"with kids\") — best and more: the best different options for the mood, both time-tested and " +
            "fresh. \"Classic\" without details means acclaimed films people still enjoy today, from the 1970s to now (Scorsese, " +
            "Nolan, Titanic count too); black-and-white or pre-1970 only when the person asks for old films. Only real films or " +
            "series, nothing Russian, up to 5 in more. Reply with JSON only, fields in exactly this order: " +
            "{\"best\":{\"title\":\"original title in English\",\"year\":2009,\"kind\":\"movie|tv\"}," +
            "\"thought\":\"1–2 short lively sentences in {language}, first person: how you understood the request and why best is the best pick\"," +
            "\"more\":[{\"title\":\"…\",\"year\":2009,\"kind\":\"movie|tv\",\"why\":\"up to 10 words in {language}: why this one\"}]}"

        private const val ADVISE_PROMPT = "You are Likhtaryk, a film advisor inside a family's TV. Here is what the person has " +
            "watched and saved for later. Suggest 8 other real films or series they will most likely enjoy (same mood and genres), " +
            "not repeating the list, nothing Russian. Reply with JSON only, fields in exactly this order: " +
            "{\"thought\":\"one short lively sentence in {language} about what you noticed in their taste, addressing the viewer " +
            "politely as \\\"you\\\", never \\\"he\\\" or \\\"she\\\" — who is watching is unknown\"," +
            "\"more\":[{\"title\":\"original title in English\",\"year\":2009,\"kind\":\"movie|tv\",\"why\":\"up to 10 words in {language}: why this one\"}]}"
    }
}
