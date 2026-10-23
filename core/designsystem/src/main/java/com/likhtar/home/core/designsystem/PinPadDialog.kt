package com.likhtar.home.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

private const val BACKSPACE = "⌫"
private const val CANCEL = "✕"
const val PIN_LENGTH = 4

/**
 * Введення PIN з пульта: цифрова клавіатура поверх екрана.
 *
 * Щойно набрано [PIN_LENGTH] цифр, викликається [onSubmit] і поле очищується — окремої
 * кнопки «ОК» немає, бо на пульті кожне зайве натискання відчутне. Показ помилки чи нового
 * запиту робиться зміною [prompt]/[error] зовні, і це ж скидає введене.
 */
@Composable
fun PinPadDialog(
    prompt: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    // Back у діалозі має закривати діалог, а не вести на Головну.
    BackHandler(enabled = true) { onDismiss() }

    var entered by remember(prompt) { mutableStateOf("") }
    val firstKeyFocusRequester = rememberInitialFocusRequester(prompt)

    LikhtarDialog(onDismiss = onDismiss, modifier = modifier, width = 220.dp) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = prompt, style = LikhtarType.Label, color = LikhtarColors.TextPrimary)
            Text(
                text = "●".repeat(entered.length) + "·".repeat(PIN_LENGTH - entered.length),
                style = LikhtarType.Temperature,
                color = if (entered.isEmpty()) LikhtarColors.TextMuted else LikhtarColors.AccentBright,
            )
            error?.let {
                Text(text = it, style = LikhtarType.Body2, color = LikhtarColors.Live)
            }

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", BACKSPACE, "0", CANCEL)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                keys.chunked(3).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { columnIndex, key ->
                            PinKey(
                                key = key,
                                onClick = {
                                    when (key) {
                                        BACKSPACE -> entered = entered.dropLast(1)
                                        CANCEL -> onDismiss()
                                        else -> if (entered.length < PIN_LENGTH) {
                                            entered += key
                                            if (entered.length == PIN_LENGTH) {
                                                onSubmit(entered)
                                                entered = ""
                                            }
                                        }
                                    }
                                },
                                modifier = if (rowIndex == 0 && columnIndex == 0) {
                                    Modifier.focusRequester(firstKeyFocusRequester)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(
    key: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.width(60.dp),
        shape = RoundedCornerShape(LikhtarDimens.CornerMedium),
        background = LikhtarColors.SurfaceAlt,
        focusedBackground = LikhtarColors.Accent,
        focusedScale = 1f,
    ) { focused ->
        Text(
            text = key,
            style = LikhtarType.Label,
            color = if (focused) LikhtarColors.OnAccent else LikhtarColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
    }
}
