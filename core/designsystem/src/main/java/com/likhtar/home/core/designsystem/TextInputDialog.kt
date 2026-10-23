package com.likhtar.home.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/**
 * Введення тексту з пульта: поле з системною екранною клавіатурою Android TV.
 *
 * Довгі значення (наприклад, long-lived token Home Assistant) набирати пультом справді
 * незручно, але **можливість мусить бути в самому лаунчері**: інакше єдиний шлях —
 * класти файл на пристрій з комп'ютера, а це вимога, яку звичайний користувач просто
 * не виконає. Хто має Bluetooth-клавіатуру або застосунок-пульт із телефона — вставить
 * значення за секунду; решта скористається екранною клавіатурою або голосовим введенням,
 * яке ця ж клавіатура пропонує.
 */
@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    placeholder: String = "",
    allowClear: Boolean = false,
) {
    // Back у діалозі закриває діалог, а не веде на Головну.
    BackHandler(enabled = true) { onDismiss() }

    var value by remember(title) {
        mutableStateOf(TextFieldValue(initialValue, TextRange(initialValue.length)))
    }
    val fieldFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(title) {
        // Поле мусить отримати фокус саме тут: користувач не має ще раз «доїжджати» до нього
        // пультом, а без фокуса не з'явиться й екранна клавіатура.
        runCatching { fieldFocusRequester.requestFocus() }
    }

    LikhtarDialog(onDismiss = onDismiss, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, style = LikhtarType.Label, color = LikhtarColors.TextPrimary)
            description?.let {
                Text(text = it, style = LikhtarType.Body2, color = LikhtarColors.TextTertiary)
            }

            LikhtarInputField(
                value = value,
                onValueChange = { value = it },
                placeholder = placeholder,
                focusRequester = fieldFocusRequester,
                onDone = { onSubmit(value.text.trim()) },
                // ↓ із поля — на «Зберегти». Без цього поле саме ковтало ↓ (курсор), і
                // після закриття клавіатури до кнопок пультом було не дістатись.
                onDown = { runCatching { saveFocusRequester.requestFocus() } },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton(text = stringResource(R.string.ds_save), primary = true, modifier = Modifier.focusRequester(saveFocusRequester)) {
                    keyboard?.hide()
                    onSubmit(value.text.trim())
                }
                DialogButton(text = stringResource(R.string.ds_cancel)) {
                    keyboard?.hide()
                    onDismiss()
                }
                if (allowClear && initialValue.isNotBlank()) {
                    DialogButton(text = stringResource(R.string.ds_clear)) {
                        keyboard?.hide()
                        onSubmit("")
                    }
                }
            }
        }
    }
}

/** Поле введення LIKHTAR: використовується і в цьому діалозі, і у виборі країн у налаштуваннях. */
@Composable
fun LikhtarInputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    onDone: () -> Unit,
    onDown: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = LikhtarType.Body1.copy(color = LikhtarColors.TextPrimary),
        cursorBrush = SolidColor(LikhtarColors.AccentBright),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val down = onDown != null && event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown
                if (down) onDown?.invoke()
                down
            }
            .clip(RoundedCornerShape(LikhtarDimens.CornerMedium))
            .background(LikhtarColors.BackgroundDeep)
            .border(
                width = LikhtarDimens.FocusRingWidth,
                color = if (focused) LikhtarColors.Accent else LikhtarColors.Border,
                shape = RoundedCornerShape(LikhtarDimens.CornerMedium),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        decorationBox = { field ->
            if (value.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = LikhtarType.Body1,
                    color = LikhtarColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            field()
        },
    )
}

@Composable
private fun DialogButton(
    text: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = LikhtarButton(label = text, onClick = onClick, modifier = modifier, primary = primary)
