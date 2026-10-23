package com.likhtar.home.core.smarthome

import com.likhtar.home.core.data.text
import com.likhtar.home.core.data.http
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Доступ, виданий самим HA після входу на телефоні: адреса, хто ми й refresh-токен. */
data class HaGrant(val url: String, val clientId: String, val refreshToken: String)

/**
 * Вхід у Home Assistant без довгого токена — так, як підключається мобільний застосунок HA
 * (OAuth2 «authorization code», `/auth/authorize` → `/auth/token`).
 *
 * Пароль приставка не бачить ніколи: людина вводить його на сторінці HA у своєму телефоні,
 * а сюди приходить лише одноразовий код. Обмінюємо його на refresh-токен (живе, доки його не
 * відкличуть у профілі HA) і з нього беремо короткі access-токени на 30 хв.
 *
 * Зберігається у приватній теці застосунку (`filesDir`), не на спільному сховищі, як
 * `homeassistant.json`: інші застосунки його не прочитають.
 */
@Singleton
class HaAuth @Inject constructor(@ApplicationContext private val context: Context) {
    private val file = File(context.filesDir, FILE)
    private val lock = Mutex()

    @Volatile private var access: Pair<String, Long>? = null

    /** Росте після кожного підключення чи відключення — опитування перезапускається. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    fun grant(): HaGrant? = runCatching {
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        HaGrant(json.getString("url"), json.getString("client_id"), json.getString("refresh_token"))
    }.getOrNull()?.takeIf { it.url.isNotBlank() && it.refreshToken.isNotBlank() }

    /**
     * Той самий HA за новою адресою: вхід лишається чинним, міняється лише куди ходити.
     * Доти адреса входу з телефону жила окремо від поля «Адреса», і після переїзду HA
     * (зміна IP-адреси сервера) правка поля нічого не давала — «Failed to connect».
     */
    fun moveTo(url: String) {
        val grant = grant() ?: return
        file.writeText(
            JSONObject().put("url", url).put("client_id", grant.clientId).put("refresh_token", grant.refreshToken).toString(),
        )
        access = null
        _revision.value += 1
    }

    fun clear() {
        file.delete()
        access = null
        _revision.value += 1
    }

    /** Код із `/auth/authorize` → збережений доступ. Помилка — людині зрозумілим текстом. */
    suspend fun exchange(url: String, clientId: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = tokenRequest(url, "grant_type=authorization_code&code=${enc(code)}&client_id=${enc(clientId)}")
            val refresh = json.optString("refresh_token").ifBlank { error(context.getString(R.string.smarthome_auth_no_refresh)) }
            file.writeText(
                JSONObject().put("url", url).put("client_id", clientId).put("refresh_token", refresh).toString(),
            )
            access = json.optString("access_token").takeIf { it.isNotBlank() }
                ?.let { it to System.currentTimeMillis() + json.optLong("expires_in", 1800) * 1000 }
            _revision.value += 1
        }
    }

    /** Чинний access-токен; оновлює, коли до кінця менше хвилини. `null` — оновити не вдалося. */
    suspend fun accessToken(): String? = lock.withLock {
        access?.takeIf { it.second - System.currentTimeMillis() > 60_000 }?.let { return it.first }
        val grant = grant() ?: return null
        withContext(Dispatchers.IO) {
            runCatching {
                val json = tokenRequest(
                    grant.url,
                    "grant_type=refresh_token&refresh_token=${enc(grant.refreshToken)}&client_id=${enc(grant.clientId)}",
                )
                val token = json.getString("access_token")
                access = token to System.currentTimeMillis() + json.optLong("expires_in", 1800) * 1000
                token
            }.onFailure { Log.w(TAG, "access-токен HA не оновився (${it.javaClass.simpleName})") }.getOrNull()
        }
    }

    private fun tokenRequest(url: String, form: String): JSONObject {
        return http(
            SmartHomeConfig(url, "x").normalizedBaseUrl() + "/auth/token",
            TIMEOUT_MS,
            method = "POST",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body = form,
        ) { connection ->
            val code = connection.responseCode
            // 400 тут — відкликаний доступ або прострочений код: текст HA показуємо як є.
            if (code !in 200..299) {
                val reason = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error(
                    context.getString(
                        R.string.smarthome_auth_http,
                        code,
                        JSONObject(reason.ifBlank { "{}" }).optString("error_description"),
                    ).trim(),
                )
            }
            JSONObject(connection.text())
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TAG = "LikhtarHaAuth"
        const val FILE = "ha_oauth.json"
        const val TIMEOUT_MS = 8_000
    }
}
