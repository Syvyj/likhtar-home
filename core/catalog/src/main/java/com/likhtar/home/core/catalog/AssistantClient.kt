package com.likhtar.home.core.catalog

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Відповідь асистента: що сказати й, можливо, що зробити (`section`, `app`, `device`, `home`,
 * `watch`). [actionState] — для `device`: `on` | `off` | `toggle`.
 */
data class AssistantAnswer(
    val say: String,
    val actionType: String? = null,
    val actionValue: String? = null,
    val actionState: String? = null,
)

/** Репліка розмови: [fromUser] — людина, інакше асистент. */
data class AssistantTurn(val fromUser: Boolean, val text: String)

/**
 * Голосовий асистент — `/api/assistant` нашого сервера (локальна Gemma, свіже з мережі —
 * Codex). Тут лише запит: як слухати й що робити з дією, вирішує екран.
 */
@Singleton
class AssistantClient @Inject constructor(
    private val config: CatalogConfig,
) {
    /** Пристрій дому для асистента: назва як у HA, чи увімкнено, готовий підпис стану («12 мкг/м³»). */
    data class Device(val name: String, val on: Boolean, val state: String?)

    /**
     * [devices] — з них Gemma вибирає, що перемкнути, і за ними відповідає про стан дому;
     * [profile] — чий прогрес перегляду; часовий пояс — приставки, щоб «о 17:24» було її годинником.
     */
    suspend fun ask(
        text: String,
        city: String,
        lat: Double,
        lon: Double,
        history: List<AssistantTurn>,
        devices: List<Device> = emptyList(),
        profile: String = "",
    ): AssistantAnswer? =
        withContext(Dispatchers.IO) {
            // Лише домашній сервер: шлюз збірки з сайту асистента не пропускає, а текст, місто й
            // пристрої розумного дому туди не мають іти навіть на 405.
            val base = config.homeOnlyUrl() ?: return@withContext null
            val body = JSONObject()
                .put("text", text)
                .put("city", city)
                .put("lat", lat)
                .put("lon", lon)
                .put("history", JSONArray(history.takeLast(6).map { JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("text", it.text) }))
                .put("devices", JSONArray(devices.map { JSONObject().put("name", it.name).put("on", it.on).put("state", it.state.orEmpty()) }))
                .put("profile", profile)
                .put("tz", java.util.TimeZone.getDefault().id)
            // Пошук у мережі триває до 40 с — і чекаємо стільки, а не звичні 20.
            runCatching { httpPostJson("$base/api/assistant", body.toString(), CONNECT_MS, READ_MS) }
                .onFailure { Log.w(TAG, "асистент не відповів", it) }
                .getOrNull()?.takeIf { it.ok }?.body
                ?.let { raw ->
                    val json = JSONObject(raw)
                    val action = json.optJSONObject("action")
                    AssistantAnswer(
                        say = json.optString("say"),
                        actionType = action?.optString("type")?.takeIf(String::isNotBlank),
                        actionValue = action?.optString("value")?.takeIf(String::isNotBlank),
                        actionState = action?.optString("state")?.takeIf(String::isNotBlank),
                    )
                }
        }

    private companion object {
        const val TAG = "LikhtarAssistant"
        const val CONNECT_MS = 5_000
        const val READ_MS = 75_000
    }
}
