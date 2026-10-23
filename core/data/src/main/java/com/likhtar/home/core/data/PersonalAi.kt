package com.likhtar.home.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Провайдер AI: типова адреса й модель. Порожня модель — людина мусить вказати свою. */
data class AiProvider(val id: String, val title: String, val url: String, val model: String, val needsKey: Boolean = true)

object AiProviders {
    const val ANTHROPIC = "anthropic"

    val all = listOf(
        AiProvider(ANTHROPIC, "Anthropic Claude", "https://api.anthropic.com/v1", "claude-opus-5-5"),
        AiProvider("openai", "OpenAI", "https://api.openai.com/v1", "gpt-5.4-mini"),
        AiProvider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-flash-latest"),
        AiProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", ""),
        // Свій комп'ютер: Ollama й LM Studio віддають OpenAI-сумісний API; адресу вводить людина.
        AiProvider("local", "Ollama / LM Studio", "", "", needsKey = false),
    )

    fun of(id: String): AiProvider? = all.firstOrNull { it.id == id }
}

/**
 * AI самої людини: свій ключ хмарного провайдера чи модель на своєму комп'ютері. Запит іде з приставки прямо до провайдера — нашого сервера між ними немає.
 *
 * Claude — через Messages API; решта — через OpenAI-сумісний Chat Completions. Ключ лише в
 * заголовку, ніколи в адресі: [http] логує адресу збою.
 */
@Singleton
class PersonalAi @Inject constructor(private val settings: SettingsRepository) {

    private data class Setup(val provider: AiProvider, val url: String, val model: String, val key: String)

    private suspend fun setup(): Setup? {
        val s = settings.settings.first()
        val provider = AiProviders.of(s.aiProvider) ?: return null
        val url = s.aiUrl.ifBlank { provider.url }.trimEnd('/')
        val model = s.aiModel.ifBlank { provider.model }
        if (url.isBlank() || model.isBlank() || (provider.needsKey && s.aiKey.isBlank())) return null
        return Setup(provider, url, model, s.aiKey)
    }

    /** Чи налаштовано AI так, що ним можна користуватись. */
    suspend fun ready(): Boolean = setup() != null

    /** Відповідь моделі текстом; `null` — AI не налаштовано. Мережа й 4xx/5xx — виняток. */
    suspend fun chat(system: String, user: String, maxTokens: Int = 2_000): String? {
        val s = setup() ?: return null
        return withContext(Dispatchers.IO) {
            if (s.provider.id == AiProviders.ANTHROPIC) claude(s, system, user, maxTokens) else openAi(s, system, user, maxTokens)
        }
    }

    private fun claude(s: Setup, system: String, user: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", s.model)
            .put("max_tokens", maxTokens)
            // Порада й переказ — не задача на роздуми: менше чекати й платити.
            .put("output_config", JSONObject().put("effort", "low"))
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-api-key" to s.key,
            "anthropic-version" to "2026-10-21",
        )
        val json = JSONObject(http("${s.url}/messages", TIMEOUT_MS, READ_MS, "POST", headers, body.toString()) { it.text() })
        if (json.optString("stop_reason") == "refusal") return ""
        val content = json.optJSONArray("content") ?: return ""
        return (0 until content.length()).mapNotNull { content.optJSONObject(it) }
            .filter { it.optString("type") == "text" }
            .joinToString("") { it.optString("text") }
    }

    private fun openAi(s: Setup, system: String, user: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", s.model)
            .put("max_completion_tokens", maxTokens)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
        val headers = buildMap {
            put("Content-Type", "application/json")
            if (s.key.isNotBlank()) put("Authorization", "Bearer ${s.key}")
        }
        val json = JSONObject(http("${s.url}/chat/completions", TIMEOUT_MS, READ_MS, "POST", headers, body.toString()) { it.text() })
        return json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
        // Локальна модель на слабкому комп'ютері пише хвилину й довше.
        private const val READ_MS = 120_000

        /** Перший JSON-об'єкт у відповіді: моделі люблять огортати його в ```json і пояснення. */
        fun jsonIn(text: String): JSONObject? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
        }
    }
}
