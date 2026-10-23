package com.likhtar.home.core.smarthome

import com.likhtar.home.core.data.text
import com.likhtar.home.core.data.http
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Мінімальний клієнт Home Assistant поверх REST API.
 *
 * Свідомо REST, а не WebSocket: для домашнього екрана достатньо опитування раз на кілька
 * секунд, а WebSocket-підписка (`subscribe_events` на `state_changed`) вимагає тримати
 * з'єднання живим увесь час роботи лаунчера. Її додамо, коли з'явиться повноцінний дашборд.
 *
 * Токен ніколи не логується.
 */
@Singleton
class HomeAssistantClient internal constructor(private val texts: SmartHomeTexts) {

    @Inject constructor(@ApplicationContext context: Context) :
        this(SmartHomeTexts { id, args -> context.getString(id, *args) })


    suspend fun states(config: SmartHomeConfig): Result<List<SmartDevice>> {
        val states = request(config, path = "/api/states").getOrElse { return Result.failure(it) }
        // Кімнати — не обов'язкові: якщо шаблон не вдався, лишається плаский список.
        val meta = request(
            config,
            path = "/api/template",
            body = JSONObject().put("template", HomeAssistantMapper.META_TEMPLATE).toString(),
        ).map(HomeAssistantMapper::parseMeta).getOrDefault(emptyMap())
        return Result.success(HomeAssistantMapper.parseStates(states, meta, texts = texts))
    }

    /** Перевірка з'єднання: HA відповідає `{"message": "API running."}`. */
    suspend fun ping(config: SmartHomeConfig): Result<String> =
        request(config, path = "/api/").map { body ->
            runCatching { JSONObject(body).optString("message") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "OK"
        }

    /**
     * Assist Home Assistant: віддаємо розпізнану фразу самому HA, щоб не писати власний NLU.
     * Повертає репліку асистента (`response.speech.plain.speech`).
     */
    suspend fun conversation(
        config: SmartHomeConfig,
        text: String,
        language: String = Locale.getDefault().language,
    ): Result<String> = request(
        config = config,
        path = "/api/conversation/process",
        body = JSONObject()
            .put("text", text)
            .put("language", language)
            .toString(),
    ).map { body -> parseConversationReply(body) }

    private fun parseConversationReply(body: String): String =
        runCatching {
            JSONObject(body)
                .optJSONObject("response")
                ?.optJSONObject("speech")
                ?.optJSONObject("plain")
                ?.optString("speech")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: texts.get(R.string.smarthome_command_done)

    suspend fun callService(
        config: SmartHomeConfig,
        domain: String,
        service: String,
        entityId: String,
    ): Result<Unit> = request(
        config = config,
        path = "/api/services/$domain/$service",
        body = JSONObject().put("entity_id", entityId).toString(),
    ).map { }

    private suspend fun request(
        config: SmartHomeConfig,
        path: String,
        body: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isComplete) {
            return@withContext Result.failure(IllegalStateException(texts.get(R.string.smarthome_not_configured)))
        }

        runCatching {
            http(
                config.normalizedBaseUrl() + path,
                TIMEOUT_MS,
                method = if (body != null) "POST" else "GET",
                headers = mapOf("Authorization" to "Bearer ${config.token}", "Content-Type" to "application/json"),
                body = body,
            ) { connection ->
                val code = connection.responseCode
                if (code !in 200..299) {
                    error(describe(code))
                }
                connection.text()
            }
        }.onFailure { error ->
            Log.w(TAG, "Запит до Home Assistant не вдався: $path (${error.javaClass.simpleName})")
        }
    }

    /** Повідомлення для UI: користувач має розуміти, що саме не так. */
    private fun describe(code: Int): String = when (code) {
        401, 403 -> texts.get(R.string.smarthome_error_token)
        404 -> texts.get(R.string.smarthome_error_not_found)
        else -> texts.get(R.string.smarthome_error_code, code)
    }

    private companion object {
        const val TAG = "LikhtarSmartHome"
        const val TIMEOUT_MS = 6_000
    }
}
