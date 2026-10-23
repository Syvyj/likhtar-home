package com.likhtar.home.core.smarthome

import android.content.Context
import android.util.Log
import com.likhtar.home.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import org.json.JSONObject

/**
 * Дані «Смарт дому» для екрана: стан підключення та список пристроїв, що оновлюється опитуванням.
 *
 * Конфігурація шукається у трьох місцях по черзі: налаштування лаунчера → файл у власній
 * зовнішній теці застосунку → `BuildConfig`. Основний шлях — введення адреси й токена прямо
 * в налаштуваннях лаунчера. Файл лишається запасним варіантом для тих, хто не хоче набирати
 * довгий токен на екранній клавіатурі: `Android/data/com.likhtar.home/files/homeassistant.json`,
 * вміст `{"url": "...", "token": "..."}`.
 */
@Singleton
class SmartHomeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HomeAssistantClient,
    private val settingsRepository: SettingsRepository,
    private val auth: HaAuth,
    private val phoneLink: HaPhoneLink,
) {
    /** Змінюється після кожного перемикання, щоб одразу перечитати стани. */
    private val refreshTrigger = MutableStateFlow(0)

    /** Останній успішно прочитаний список — щоб сітка не блимала на перепідключеннях. */
    @Volatile
    private var lastDevices: List<SmartDevice> = emptyList()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val state: Flow<SmartHomeState> = combine(
        settingsRepository.settings.map { settings ->
            // Ключ конфігурації — саме хеш: він потрібен лише для порівняння «змінилося чи ні»,
            // і так адреса з токеном не потрапляють у стан, який легко надрукувати в лог.
            Triple(
                settings.smartHomeEnabled,
                settings.smartHomeRefreshSeconds,
                (settings.smartHomeUrl + settings.smartHomeToken).hashCode(),
            )
        },
        refreshTrigger,
        auth.revision,
    ) { (enabled, refreshSeconds, settingsKey), tick, linked ->
        // linked — підключення з телефону (чи відключення) перезапускає опитування одразу.
        val configKey = settingsKey * 31 + linked
        // tick входить у ключ навмисно: без нього distinctUntilChanged глушив би позачергове
        // оновлення після перемикання, і картка змінювалася б лише на наступному опитуванні.
        // configKey — щоб опитування перезапустилося одразу після введення адреси й токена,
        // інакше розділ показував би «Не налаштовано» до перезапуску лаунчера.
        Params(enabled, refreshSeconds, tick, configKey)
    }
        .distinctUntilChanged()
        .flatMapLatest { params -> poll(params) }
        // Один полер на всіх: стан збирають Головна, Смарт дім і Ліхтарик, і кожен запускав
        // власне опитування HA кожні 15 с. 5 с запасу — на перехід
        // між екранами, щоб опитування не рвалось і не стартувало заново.
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private fun poll(params: Params): Flow<SmartHomeState> = flow {
        if (!params.enabled) {
            lastDevices = emptyList()
            emit(SmartHomeState())
            return@flow
        }

        val config = config()
        if (!config.isComplete) {
            lastDevices = emptyList()
            emit(SmartHomeState(SmartHomeStatus.NotConfigured))
            return@flow
        }

        // Список пристроїв переживає перепідключення навмисно: опитування перезапускається
        // після кожного перемикання, і якби на час «Підключаюся…» сітка порожніла, картки
        // зникали б і з'являлися заново, а фокус щоразу відкидало на першу з них.
        emit(SmartHomeState(SmartHomeStatus.Connecting, lastDevices))

        while (true) {
            val result = client.states(config)
            val state = result.fold(
                onSuccess = { devices ->
                    lastDevices = devices
                    SmartHomeState(SmartHomeStatus.Connected(devices.size), devices)
                },
                onFailure = { error ->
                    SmartHomeState(
                        SmartHomeStatus.Error(error.message ?: context.getString(R.string.smarthome_no_connection)),
                        lastDevices,
                    )
                },
            )
            emit(state)
            delay(params.refreshSeconds.coerceAtLeast(5) * 1_000L)
        }
    }

    suspend fun toggle(device: SmartDevice): Result<Unit> {
        val (domain, service) = HomeAssistantMapper.toggleService(device)
            ?: return Result.failure(IllegalArgumentException(context.getString(R.string.smarthome_not_controllable, device.name)))

        return client.callService(config(), domain, service, device.entityId)
            .onSuccess { refreshTrigger.value += 1 }
    }

    /**
     * Увімкнути чи вимкнути прямо (`turn_on` / `turn_off`), не дивлячись на відомий стан: він
     * оновлюється раз на кілька секунд, і «вимкни» за застарілим станом могло б увімкнути.
     */
    suspend fun setPower(device: SmartDevice, on: Boolean): Result<Unit> {
        val (domain, service) = HomeAssistantMapper.powerService(device, on)
            ?: return Result.failure(IllegalArgumentException(context.getString(R.string.smarthome_not_controllable, device.name)))
        return client.callService(config(), domain, service, device.entityId)
            .onSuccess { refreshTrigger.value += 1 }
    }

    suspend fun ping(): Result<String> = client.ping(config())

    /**
     * Голосова команда цілком віддається Assist у Home Assistant: розпізнавання мовлення робить
     * система, а розуміння фрази — сервер користувача. Свого NLU лаунчер не має і не потребує:
     * все, що розуміє ваш HA (у тому числі власні sentence-тригери), працює тут без змін.
     */
    suspend fun voiceCommand(phrase: String): Result<String> {
        val config = config()
        if (!config.isComplete) {
            return Result.failure(IllegalStateException(context.getString(R.string.smarthome_not_configured)))
        }

        return client.conversation(config, phrase)
            // Команда могла щось змінити в домі — перечитуємо стани одразу.
            .onSuccess { refreshTrigger.value += 1 }
    }

    suspend fun config(): SmartHomeConfig {
        // Вхід з телефону — першим: його людина зробила останнім і свідомо, а короткий
        // access-токен оновлюється сам. Не оновився — порожній токен дає «Токен відхилено».
        auth.grant()?.let { grant -> return SmartHomeConfig(grant.url, auth.accessToken() ?: REVOKED) }
        val settings = settingsRepository.settings.first()
        if (settings.smartHomeUrl.isNotBlank() && settings.smartHomeToken.isNotBlank()) {
            return SmartHomeConfig(settings.smartHomeUrl, settings.smartHomeToken)
        }

        // Адреса з поля важить і для токена з файлу: HA переїхав (.241 → .242), а
        // файл досі вів на стару адресу, і правка поля нічого не давала.
        fileConfig()?.let { return if (settings.smartHomeUrl.isNotBlank()) it.copy(baseUrl = settings.smartHomeUrl) else it }

        return SmartHomeConfig(BuildConfig.DEFAULT_HA_URL, BuildConfig.DEFAULT_HA_TOKEN)
    }

    /** Підключено входом з телефону (а не токеном у полі чи файлі). */
    fun linkedFromPhone(): Boolean = auth.grant() != null

    /** Забути вхід з телефону; доступ у HA лишається, доки його не відкличуть у профілі HA. */
    fun forgetPhoneLink() = auth.clear()

    /** Нова адреса HA — і для входу з телефону, якщо він є. */
    fun movePhoneLink(url: String) = auth.moveTo(url)

    val phoneLinkStatus get() = phoneLink.status

    /** QR для «Підключити з телефону»; у формі вже стоїть відома адреса HA. */
    suspend fun startPhoneLink(): String? {
        val known = runCatching { config().normalizedBaseUrl() }.getOrDefault("")
        return phoneLink.start(known)
    }

    /** Де саме лежить файл — показуємо в налаштуваннях, щоб користувач знав, куди його класти. */
    fun configFilePath(): String = File(context.getExternalFilesDir(null), CONFIG_FILE).absolutePath

    private fun fileConfig(): SmartHomeConfig? {
        val file = File(context.getExternalFilesDir(null), CONFIG_FILE)
        if (!file.exists()) return null

        return runCatching {
            val json = JSONObject(file.readText())
            SmartHomeConfig(json.optString("url"), json.optString("token"))
        }.onFailure {
            Log.w(TAG, "Файл конфігурації Home Assistant не читається")
        }.getOrNull()?.takeIf { it.isComplete }
    }

    private data class Params(
        val enabled: Boolean,
        val refreshSeconds: Int,
        val tick: Int,
        val configKey: Int,
    )

    private companion object {
        const val TAG = "LikhtarSmartHome"
        const val CONFIG_FILE = "homeassistant.json"
        /** Refresh-токен відкликано чи HA недоступний: явно хибний токен дає зрозумілу помилку 401. */
        const val REVOKED = "revoked"
    }
}
