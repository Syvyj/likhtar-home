package com.likhtar.home.feature.smarthome

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.smarthome.SmartDevice
import com.likhtar.home.core.smarthome.SmartDeviceKind
import com.likhtar.home.core.smarthome.SmartHomeRepository
import com.likhtar.home.core.smarthome.SmartHomeState
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.smarthome.SmartHomeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SmartHomeUiState(
    val home: SmartHomeState = SmartHomeState(),
    val configFilePath: String = "",
    val voiceEnabled: Boolean = true,
    val message: String? = null,
) {
    val status: SmartHomeStatus get() = home.status
    val devices: List<SmartDevice> get() = home.devices
}

@HiltViewModel
class SmartHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SmartHomeRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    /** Світло, яке чекає другого OK, і коли його «зведено». */
    private var armed: Pair<String, Long>? = null

    val uiState: StateFlow<SmartHomeUiState> =
        combine(repository.state, message, settingsRepository.settings) { home, currentMessage, settings ->
            SmartHomeUiState(
                home = home,
                configFilePath = repository.configFilePath(),
                voiceEnabled = settings.voiceCommandsEnabled,
                message = currentMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SmartHomeUiState(configFilePath = repository.configFilePath()),
        )

    fun toggle(device: SmartDevice) {
        if (!device.isControllable) {
            message.value = context.getString(R.string.fsmarthome_read_only, device.name)
            return
        }
        // У спальні й дитячій сплять діти: світло вмикається лише другим OK, щоб випадкове
        // натискання пульта не засвітило кімнату. Вимкнення — одразу.
        if (device.kind == SmartDeviceKind.LIGHT && !device.isOn) {
            val now = System.currentTimeMillis()
            val pending = armed
            if (pending == null || pending.first != device.entityId || now - pending.second > CONFIRM_MS) {
                armed = device.entityId to now
                message.value = context.getString(R.string.fsmarthome_confirm_on, device.name)
                return
            }
        }
        armed = null
        viewModelScope.launch {
            repository.toggle(device)
                .onSuccess { message.value = context.getString(R.string.fsmarthome_command_sent, device.name) }
                .onFailure { message.value = it.message ?: context.getString(R.string.fsmarthome_command_send_failed) }
        }
    }

    /** Фраза вже розпізнана системою; сюди приходить текст. Порожній — нічого не почули. */
    fun onVoicePhrase(phrase: String) {
        if (phrase.isBlank()) {
            // Мовчати тут не можна: користувач натиснув кнопку й мусить бачити результат.
            message.value = context.getString(R.string.fsmarthome_voice_not_heard)
            return
        }
        viewModelScope.launch {
            message.value = context.getString(R.string.fsmarthome_voice_running, phrase)
            repository.voiceCommand(phrase)
                .onSuccess { message.value = "«$phrase» → $it" }
                .onFailure { message.value = it.message ?: context.getString(R.string.fsmarthome_command_failed) }
        }
    }

    private val _link = MutableStateFlow<String?>(null)
    /** Відкритий QR «Підключити з телефону»; "" — мережі не видно. */
    val link: StateFlow<String?> = _link
    val linkStatus get() = repository.phoneLinkStatus

    fun startPhoneLink() {
        viewModelScope.launch { _link.value = repository.startPhoneLink().orEmpty() }
    }

    fun closePhoneLink() {
        _link.value = null
    }

    fun clearMessage() {
        message.value = null
    }

    fun onVoiceUnavailable() {
        message.value = context.getString(R.string.fsmarthome_voice_unavailable)
    }

    fun testConnection() {
        viewModelScope.launch {
            message.value = context.getString(R.string.fsmarthome_checking)
            repository.ping()
                .onSuccess { message.value = context.getString(R.string.fsmarthome_ping_ok, it) }
                .onFailure { message.value = it.message ?: context.getString(R.string.fsmarthome_no_connection) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val CONFIRM_MS = 4_000L
    }
}
