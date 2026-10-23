package com.likhtar.home.feature.search

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.focusDirections
import kotlinx.coroutines.launch

/**
 * Поле запиту в два стани. Спокій — звичайна фокусна плашка з текстом: система відкривала
 * клавіатуру на сам фокус поля, і стрілки йшли вже в неї. OK — справжнє поле з
 * клавіатурою; «Пошук», Back чи ↓ — назад у спокій.
 */
@Composable
internal fun QueryField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    queryFocusRequester: FocusRequester,
    menuFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val editor = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val finish: (Boolean) -> Unit = { submit ->
        if (submit) onSubmit()
        keyboard?.hide()
        editing = false
        // Назад на плашку поля: видачі ще немає, і фокус падав у верхнє меню. Коли вона
        // прийде, екран сам переведе фокус на найкращий збіг.
        scope.launch {
            repeat(12) {
                withFrameNanos { }
                if (focused) return@launch
                runCatching { queryFocusRequester.requestFocus() }
            }
        }
    }
    // Плашка зникає, а поле з'являється лише на наступній розмітці: фокус між ними падав у
    // верхнє меню. Просимо поле щокадру, поки воно справді не візьме фокус.
    var editorFocused by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (!editing) return@LaunchedEffect
        repeat(12) {
            withFrameNanos { }
            if (editorFocused) return@repeat
            runCatching { editor.requestFocus() }
        }
        keyboard?.show()
    }
    // .field прототипу: пігулка 84 px, тло --deep з тонкою рамкою, лупа бурштином,
    // запит 32 px / 600; у фокусі й під час набору — тепла біла заливка з бурштиновим ореолом.
    val shape = RoundedCornerShape(LikhtarDimens.CornerPill)
    val lit = focused || editing
    Box(
        modifier = modifier
            .height(42.dp)
            .then(if (lit) Modifier.border(2.5.dp, LikhtarColors.Glow.copy(alpha = 0.18f), shape) else Modifier)
            .padding(if (lit) 0.dp else 0.dp)
            .clip(shape)
            .background(if (lit) LikhtarColors.Paper else LikhtarColors.BackgroundDeep)
            .then(if (lit) Modifier else Modifier.border(0.75.dp, LikhtarColors.Faint, shape)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 17.dp),
        ) {
            androidx.tv.material3.Icon(
                androidx.compose.ui.res.painterResource(com.likhtar.home.core.designsystem.R.drawable.ic_solar_magnifer),
                contentDescription = null,
                tint = if (lit) LikhtarColors.AmberOnPaper else LikhtarColors.Glow,
                modifier = Modifier.size(18.dp),
            )
            if (editing) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = LikhtarColors.Background, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = LikhtarType.Body),
                    cursorBrush = SolidColor(LikhtarColors.AmberOnPaper),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { finish(true) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(editor)
                        .onFocusChanged {
                            // Кінець редагування — лише коли поле ВЖЕ мало фокус і втратило його;
                            // перший виклик приходить із «не у фокусі» ще до запиту.
                            if (it.isFocused) editorFocused = true
                            else if (editorFocused) {
                                editorFocused = false
                                editing = false
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> { finish(true); true }
                                Key.Back -> { finish(false); true }
                                else -> false
                            }
                        },
                )
            } else {
                FocusableSurface(
                    onClick = { editing = true },
                    shape = shape,
                    background = Color.Transparent,
                    focusedBackground = Color.Transparent,
                    borderColor = Color.Transparent,
                    focusRingColor = Color.Transparent,
                    focusedScale = 1f,
                    focusLighten = 0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .focusRequester(queryFocusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .focusDirections {
                            up = menuFocusRequester
                            contentFocusRequester?.let { down = it }
                        },
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = query.ifBlank { placeholder },
                            style = if (query.isBlank()) TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            else TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                            color = when {
                                query.isBlank() && lit -> LikhtarColors.InkOnPaper
                                query.isBlank() -> LikhtarColors.TextDim
                                lit -> LikhtarColors.Background
                                else -> Color.White
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // .caret: 3×38 px бурштином після запиту.
                        if (query.isNotBlank()) {
                            Box(
                                Modifier
                                    .padding(start = 3.dp)
                                    .size(1.5.dp, 19.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(if (lit) LikhtarColors.AmberOnPaper else LikhtarColors.Glow),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActionPill(
    text: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Спільна кнопка лаунчера; висота — як у поля пошуку поруч.
    LikhtarButton(
        label = text,
        onClick = onClick,
        modifier = modifier,
        height = 52.dp,
        leading = { color, _ -> icon(color) },
    )
}
