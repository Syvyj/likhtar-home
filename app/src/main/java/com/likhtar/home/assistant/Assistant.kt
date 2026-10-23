package com.likhtar.home.assistant

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarVoice
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.likhtar.home.R
import com.likhtar.home.core.catalog.AssistantAnswer
import com.likhtar.home.core.catalog.AssistantClient
import com.likhtar.home.core.catalog.AssistantTurn
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.data.SettingsRepository
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.ProtoButton
import com.likhtar.home.core.designsystem.requestWithRetry
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.smarthome.SmartHomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Куди асистент веде лаунчер після відповіді. */
sealed interface AssistantNav {
    data class Section(val section: LikhtarSection) : AssistantNav
    data class Streaming(val brand: String) : AssistantNav
    data class Watch(val query: String) : AssistantNav
}

data class AssistantUiState(
    val turns: List<AssistantTurn> = emptyList(),
    val thinking: Boolean = false,
)

/**
 * Голосовий асистент: фраза → сервер (локальна Gemma, свіже з мережі — Codex) →
 * відповідь на екрані й уголос → дія. Розумний дім — через Assist Home Assistant цієї
 * приставки (токен тут, не на сервері), застосунок — серед встановлених за назвою.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: AssistantClient,
    private val settings: SettingsRepository,
    private val apps: AppsRepository,
    private val smartHome: SmartHomeRepository,
    private val resumeRows: com.likhtar.home.core.catalog.ResumeRows,
    private val continuePlayback: com.likhtar.home.core.catalog.ContinuePlayback,
    private val catalog: com.likhtar.home.core.catalog.CatalogRepository,
    private val watchlist: com.likhtar.home.core.catalog.WatchlistRepository,
) : ViewModel() {
    private val state = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = state.asStateFlow()
    private val navFlow = MutableSharedFlow<AssistantNav>(extraBufferCapacity = 1)
    val navigation: SharedFlow<AssistantNav> = navFlow

    private var ttsReady = false
    private val tts: TextToSpeech

    init {
        // Мову ставимо, лише коли рушій готовий: до onInit setLanguage мовчки не діє.
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS &&
                tts.setLanguage(Locale.forLanguageTag("uk-UA")) >= TextToSpeech.LANG_AVAILABLE
        }
    }

    /** Нова розмова на кожне відкриття: учорашні репліки тут ні до чого. */
    fun reset() {
        tts.stop()
        state.value = AssistantUiState()
    }

    /** [speak] — відповідати вголос: на голос — голосом, на текст (з телефона) — лише на екрані. */
    fun ask(text: String, speak: Boolean = true) {
        val history = state.value.turns
        state.update { it.copy(turns = it.turns + AssistantTurn(true, text), thinking = true) }
        viewModelScope.launch {
            val s = settings.settings.first()
            val devices = homeDevices()
            android.util.Log.i("LikhtarAssistant", "пристроїв дому: ${devices.size}")
            val answer = client.ask(
                text, s.weatherCity, s.weatherLatitude, s.weatherLongitude, history,
                devices.map { AssistantClient.Device(it.name, it.isOn, it.readableState) },
                profile = s.activeProfileId,
            )
                ?: AssistantAnswer(context.getString(R.string.assistant_offline))
            say(answer.say, speak)
            state.update { it.copy(thinking = false) }
            perform(answer, speak)
        }
    }

    private fun say(text: String, speak: Boolean) {
        if (text.isBlank()) return
        state.update { it.copy(turns = it.turns + AssistantTurn(false, text)) }
        if (speak && ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant")
    }

    private suspend fun perform(answer: AssistantAnswer, speak: Boolean) {
        val value = answer.actionValue ?: return
        android.util.Log.i("LikhtarAssistant", "дія ${answer.actionType} «$value» ${answer.actionState.orEmpty()}")
        when (answer.actionType) {
            "section" -> LikhtarSection.fromRoute(value)?.let { navFlow.emit(AssistantNav.Section(it)) }
            "watch" -> navFlow.emit(AssistantNav.Watch(value))
            "streaming" -> navFlow.emit(AssistantNav.Streaming(value))
            "resume" -> {
                // Та сама картка, що в «Продовжити»: Кінозал і Реаліті разом.
                val card = (resumeRows.cinema() + resumeRows.reality()).firstOrNull { it.continueItem != null && sameTitle(value, it.title) }
                if (card == null) say(context.getString(R.string.assistant_not_found, value), speak)
                else continuePlayback.play(card.continueItem!!)
            }
            "bookmark" -> {
                val item = catalog.search(value).firstOrNull()
                when {
                    item == null -> say(context.getString(R.string.assistant_not_found, value), speak)
                    watchlist.contains(item) -> say(context.getString(R.string.assistant_bookmarked, item.title), speak)
                    else -> if (watchlist.toggle(item)) say(context.getString(R.string.assistant_bookmarked, item.title), speak)
                }
            }
            "sleep" -> if (!serviceOn()) say(context.getString(R.string.assistant_service_off), speak) else AssistantTimers.sleepIn(value.toIntOrNull() ?: 0)
            "remind" -> when {
                !serviceOn() -> say(context.getString(R.string.assistant_service_off), speak)
                !AssistantTimers.remind(value, answer.actionState ?: "") -> say(context.getString(R.string.assistant_no_time), speak)
            }
            "volume" -> AssistantTimers.volume(context, value)
            "device" -> {
                val device = homeDevices().firstOrNull { it.name == value }
                val wantOn = when (answer.actionState) {
                    "on" -> true
                    "off" -> false
                    else -> device?.isOn?.not()
                }
                when {
                    device == null || wantOn == null -> say(context.getString(R.string.assistant_home_failed), speak)
                    // Прямо «увімкни»/«вимкни», а не перемикання за відомим станом: той може
                    // відставати на кілька секунд.
                    else -> smartHome.setPower(device, wantOn).onFailure { say(it.message ?: context.getString(R.string.assistant_home_failed), speak) }
                }
            }
            "home" -> smartHome.voiceCommand(value)
                .onSuccess { say(it, speak) }
                .onFailure { say(it.message ?: context.getString(R.string.assistant_home_failed), speak) }
            "app" -> {
                val wanted = value.normalized()
                val app = apps.apps.first().firstOrNull { it.label.normalized().let { l -> l.contains(wanted) || wanted.contains(l) } }
                if (app == null || !apps.launch(app)) say(context.getString(R.string.assistant_app_missing, value), speak)
            }
        }
    }

    /**
     * Керовані пристрої дому — щоб асистент перемикав їх за точною назвою: Assist Home
     * Assistant «підсвітку» замість «Підсвітка в спальні» не впізнає. Не дочекались
     * за 5 с — без списку, тоді лишається Assist.
     */
    private suspend fun homeDevices(): List<com.likhtar.home.core.smarthome.SmartDevice> =
        kotlinx.coroutines.withTimeoutOrNull(5_000) {
            smartHome.state.first { it.status !is com.likhtar.home.core.smarthome.SmartHomeStatus.Connecting }
        }?.devices.orEmpty().filter { it.isControllable }

    private fun serviceOn(): Boolean =
        context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            ?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty().any { it.id.startsWith("${context.packageName}/") }

    /** Та сама назва, хоч і в іншому відмінку: усі основи (4 літери) назви є в сказаному. */
    private fun sameTitle(said: String, title: String): Boolean {
        fun stems(s: String) = s.lowercase().split(Regex("""[^\p{L}\p{Nd}+]+""")).filter { it.isNotBlank() }.map { it.take(4) }
        val have = stems(said).toSet()
        val need = stems(title)
        return need.isNotEmpty() && need.all { it in have }
    }

    fun stopSpeaking() = tts.stop()

    override fun onCleared() {
        tts.shutdown()
        super.onCleared()
    }

    private fun String.normalized() = lowercase().filter(Char::isLetterOrDigit)
}

/**
 * Асистент поверх екрана: одразу слухає (системне вікно розпізнавання — без власного
 * дозволу на мікрофон), показує розмову, «Запитати ще» — знову слухати. Back — закрити.
 */
@Composable
fun AssistantPanel(
    viewModel: AssistantViewModel,
    onClose: () -> Unit,
    /** Запитання текстом (з телефона) — без слухання й без голосу у відповідь. */
    question: String? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prompt = stringResource(R.string.assistant_listening)
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        when {
            result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank() -> viewModel.ask(spoken)
            // Нічого не сказали на першому ж слуханні — панель без розмови ні до чого.
            viewModel.uiState.value.turns.isEmpty() -> onClose()
        }
    }
    val startListening: () -> Unit = {
        viewModel.stopSpeaking()
        runCatching {
            listen.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt),
            )
        }
        Unit
    }
    LaunchedEffect(question) {
        viewModel.reset()
        if (question.isNullOrBlank()) startListening() else viewModel.ask(question, speak = false)
    }
    val again = remember { FocusRequester() }
    LaunchedEffect(state.thinking) { if (!state.thinking) again.requestWithRetry() }
    val asked = state.turns.lastOrNull { it.fromUser }?.text
    val answer = state.turns.lastOrNull()?.takeIf { !it.fromUser }?.text

    // Мовою Ліхтарика (його сторінка): пригашений екран, ліворуч — знак, ім'я
    // й стан, відповідь великим теплим текстом; поки думає — відсвіт і бурштиновий курсор.
    Dialog(
        onDismissRequest = {
            viewModel.stopSpeaking()
            onClose()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to LikhtarColors.Background.copy(alpha = 0.97f),
                        0.6f to LikhtarColors.Background.copy(alpha = 0.88f),
                        1f to LikhtarColors.Background.copy(alpha = 0.6f),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = LikhtarDimens.ScreenPadding + 16.dp)
                    .width(560.dp),
            ) {
                LikhtarVoice(
                    name = stringResource(R.string.assistant_name),
                    status = when {
                        state.thinking -> stringResource(R.string.assistant_thinking)
                        asked != null -> "«$asked»"
                        else -> stringResource(R.string.assistant_listening_short)
                    },
                    text = answer ?: if (state.thinking) "" else stringResource(R.string.assistant_listening),
                    thinking = state.thinking,
                    textSize = 22.sp,
                    maxLines = 9,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 26.dp),
                ) {
                    ProtoButton(
                        label = stringResource(R.string.assistant_again),
                        onClick = startListening,
                        icon = com.likhtar.home.core.designsystem.R.drawable.ic_solar_mic,
                        modifier = Modifier.focusRequester(again),
                    )
                    Text(stringResource(R.string.assistant_back_hint), style = LikhtarType.Body2, color = LikhtarColors.TextDim)
                }
            }
        }
    }
}
