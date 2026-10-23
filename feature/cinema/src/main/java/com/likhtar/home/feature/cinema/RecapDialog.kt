package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.LanternLoader
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.rememberInitialFocusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Переказ сезону: текст від Ліхтарика (за описами серій) і «Озвучити» — системний синтез
 * мовлення українською (Google TTS є і на приставці). Голос — Android, не модель: Gemma
 * текстова й говорити не вміє.
 */
@Composable
internal fun RecapDialog(recap: RecapState, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var speaking by remember { mutableStateOf(false) }
    var voiceReady by remember { mutableStateOf(false) }
    val engine = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            val lang = engine[0]?.setLanguage(Locale.forLanguageTag("uk-UA")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            voiceReady = status == TextToSpeech.SUCCESS && lang >= TextToSpeech.LANG_AVAILABLE
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { if (utteranceId == LAST_PART) speaking = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { speaking = false }
        })
        engine[0] = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }
    val speak: () -> Unit = {
        val tts = engine[0]
        if (tts != null) {
            if (speaking) {
                tts.stop()
                speaking = false
            } else {
                // Абзацами: довгий текст одним шматком двигун може не прийняти.
                val parts = recap.text.split("\n").map(String::trim).filter(String::isNotBlank)
                parts.forEachIndexed { i, part ->
                    tts.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == parts.lastIndex) LAST_PART else "p$i")
                }
                speaking = parts.isNotEmpty()
            }
        }
    }
    // Ключ і на голос: «Озвучити» з'являється пізніше за текст, коли двигун TTS готовий.
    val first = rememberInitialFocusRequester(recap.loading to voiceReady)
    LikhtarDialog(onDismiss = onDismiss, width = 900.dp) {
        Text(
            text = if (title.isNotBlank()) stringResource(R.string.cinema_recap_header_titled, recap.season, title) else stringResource(R.string.cinema_recap_header, recap.season),
            style = LikhtarType.Label,
            color = LikhtarColors.Cyan,
        )
        when {
            recap.loading -> LanternLoader(
                title = stringResource(R.string.cinema_recap_loading),
                subtitle = stringResource(R.string.cinema_recap_loading_hint),
                modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally),
            )
            recap.error.isNotBlank() -> Text(
                text = recap.error,
                style = LikhtarType.Body1,
                color = LikhtarColors.TextSecondary,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            else -> Text(
                text = recap.text,
                style = LikhtarType.Body1,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 16.dp)
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!recap.loading && recap.text.isNotBlank() && voiceReady) {
                LikhtarButton(
                    label = stringResource(if (speaking) R.string.cinema_recap_stop else R.string.cinema_recap_speak),
                    onClick = speak,
                    modifier = Modifier.focusRequester(first),
                )
                LikhtarButton(label = stringResource(R.string.cinema_close), onClick = onDismiss)
            } else {
                LikhtarButton(label = stringResource(R.string.cinema_close), onClick = onDismiss, modifier = Modifier.focusRequester(first))
            }
        }
    }
}

private const val LAST_PART = "last"
