package com.likhtar.home.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDimens
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.focusDirections

/** Куди веде ← з рядка правої панелі. Роздається згори, бо рядків тут кілька десятків. */
internal val LocalDetailExit = compositionLocalOf<FocusRequester?> { null }

/**
 * Спільна оболонка всіх рядків панелі: суцільна поверхня, назва ліворуч, довільний
 * «хвіст» праворуч.
 *
 * Назва бере всю вільну ширину й обрізається сама. Раніше рядок був `SpaceBetween` без
 * `weight`, і довгий текст підпирав слово «Видалити» аж до злипання — а ще
 * довший виштовхнув би його за край.
 */
@Composable
private fun RowShell(
    text: String,
    subtitle: String?,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    trailing: @Composable (focused: Boolean) -> Unit,
) {
    // .r прототипу: список без коробок — рядок 76 px, поле 12×28 px, між рядками волосяна
    // лінія; фокус — тепла біла пігулка, 1.02. Назва 26 px / 600, підпис 19 px приглушено.
    val exit = LocalDetailExit.current
    var focusedNow by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        if (!focusedNow) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(LikhtarColors.Faint),
            )
        }
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .onFocusChanged { focusedNow = it.isFocused }
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .then(exit?.let { target -> Modifier.focusDirections { left = target } } ?: Modifier),
            shape = RoundedCornerShape(11.dp),
            background = Color.Transparent,
            focusedBackground = LikhtarColors.Paper,
            borderColor = Color.Transparent,
            focusRingColor = Color.Transparent,
            focusedScale = 1.02f,
            instantFill = true,
        ) { focused ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.25.sp),
                        color = if (focused) LikhtarColors.Background else LikhtarColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 9.5.sp, lineHeight = 12.8.sp),
                            color = if (focused) LikhtarColors.InkOnPaper else LikhtarColors.TextDim,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.5.dp),
                        )
                    }
                }
                trailing(focused)
            }
        }
    }
}

/**
 * Перемикач: увімкнено або вимкнено, і більше нічого.
 *
 * Чотири типи рядків замість одного `ActionRow` на все — тип дії видно з правого краю ще до
 * натискання. .sw прототипу: пігулка 40 px з вогником; «Увімк.» — бурштином.
 */
@Composable
internal fun ToggleRow(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
) {
    RowShell(text = text, subtitle = subtitle, onClick = onToggle, focusRequester = null) { focused ->
        val fill = when {
            focused && checked -> LikhtarColors.Background
            focused -> LikhtarColors.Background.copy(alpha = 0.08f)
            checked -> LikhtarColors.Amber.copy(alpha = 0.16f)
            else -> LikhtarColors.Paper.copy(alpha = 0.07f)
        }
        val ink = when {
            focused && checked -> LikhtarColors.Paper
            focused -> LikhtarColors.InkOnPaper
            checked -> LikhtarColors.Glow
            else -> LikhtarColors.TextDim
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .height(20.dp)
                .clip(RoundedCornerShape(LikhtarDimens.CornerPill))
                .background(fill)
                .padding(horizontal = 8.dp),
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .drawBehind {
                        if (checked) {
                            if (!focused || true) drawCircle(LikhtarColors.Amber.copy(alpha = 0.6f), radius = size.minDimension)
                            drawCircle(LikhtarColors.Glow)
                        } else {
                            drawCircle(ink, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        }
                    },
            )
            Text(
                text = stringResource(if (checked) R.string.settings_on else R.string.settings_off),
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp),
                color = ink,
            )
        }
    }
}

/**
 * Вибір зі списку: шпалери, теми, країни, стартовий розділ. .dot прототипу: кружок 26 px,
 * вибраний — бурштинова обвідка й вогник усередині.
 */
@Composable
internal fun ChoiceRow(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    RowShell(text = text, subtitle = null, onClick = onSelect, focusRequester = focusRequester) { focused ->
        Box(
            Modifier
                .size(13.dp)
                .drawBehind {
                    val ring = when {
                        focused -> LikhtarColors.InkOnPaper
                        selected -> LikhtarColors.Glow
                        else -> LikhtarColors.TextPrimary.copy(alpha = 0.4f)
                    }
                    drawCircle(ring, radius = size.minDimension / 2 - 0.6.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.25.dp.toPx()))
                    if (selected) {
                        if (!focused) drawCircle(LikhtarColors.Amber.copy(alpha = 0.55f), radius = 5.dp.toPx())
                        drawCircle(if (focused) LikhtarColors.Background else LikhtarColors.Glow, radius = 3.dp.toPx())
                    }
                },
        )
    }
}

/** Значення, яке OK змінює: відкриває клавіатуру або перебирає варіанти по колу. */
@Composable
internal fun ValueRow(
    text: String,
    value: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
) {
    RowShell(text = text, subtitle = subtitle, onClick = onClick, focusRequester = focusRequester) { focused ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // .val: 22 px .8 до 330 px; шеврон alt-arrow-right — обіцянка, що OK щось відкриє.
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.sp),
                    color = if (focused) LikhtarColors.InkOnPaper else LikhtarColors.TextPrimary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 165.dp),
                )
            }
            androidx.tv.material3.Icon(
                androidx.compose.ui.res.painterResource(com.likhtar.home.core.designsystem.R.drawable.ic_solar_arrow_right),
                null,
                tint = if (focused) LikhtarColors.InkOnPaper else LikhtarColors.TextDim,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * Дія, а не стан. [destructive] фарбує її в тривожний колір: видалення
 * незворотне, і воно не має виглядати так само, як «Увімкнено» в сусідньому рядку.
 */
@Composable
internal fun ActionRow(
    text: String,
    action: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    destructive: Boolean = false,
) {
    RowShell(text = text, subtitle = subtitle, onClick = onClick, focusRequester = null) { focused ->
        // .act: 22 px / 600 бурштином; у фокусі — #8a6526.
        Text(
            text = action,
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = when {
                focused && destructive -> LikhtarColors.DangerOnPaper
                focused -> LikhtarColors.AmberOnPaper
                destructive -> LikhtarColors.Danger
                else -> LikhtarColors.Glow
            },
        )
    }
}

/** Підзаголовок групи всередині розділу. */
@Composable
internal fun GroupTitle(text: String) {
    // .grp: 22 px / 600 приглушено, звичайним регістром; margin 30 / 6 px, відступ 28 px.
    Text(
        text = sentenceCase(text),
        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
        color = LikhtarColors.TextDim,
        modifier = Modifier.padding(start = 14.dp, top = 9.dp, bottom = 3.dp),
    )
}

@Composable
internal fun Note(text: String) {
    // .about: 26 px / 1.5 .82, до 640 px — текстом, без коробки.
    Text(
        text = text,
        style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.sp, lineHeight = 16.5.sp),
        color = LikhtarColors.TextPrimary.copy(alpha = 0.82f),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp).widthIn(max = 320.dp),
    )
}


/**
 * «НАД ПЛИТКОЮ «LIKHTAR HOME»» → «Над плиткою «LIKHTAR HOME»»: групи в ресурсах записані
 * великими, а прототип — звичайним регістром. Назви в лапках (марки) і абревіатури
 * (ТБ, PIN) лишаються як є.
 */
internal fun sentenceCase(text: String): String {
    if (text.any { it.isLowerCase() }) return text
    val out = StringBuilder()
    var quoted = 0
    var first = true
    text.split(' ').forEachIndexed { i, word ->
        if (i > 0) out.append(' ')
        val keep = quoted > 0 || word.first() in OPEN_QUOTES || (word.length <= 3 && !first && word.all { it.isUpperCase() || !it.isLetter() } && word.any { it.isLetter() } && word !in COMMON)
        quoted += word.count { it in OPEN_QUOTES } - word.count { it in CLOSE_QUOTES }
        out.append(
            when {
                keep -> word
                first -> word.lowercase().replaceFirstChar { it.uppercase() }
                else -> word.lowercase()
            },
        )
        if (word.any { it.isLetter() }) first = false
    }
    return out.toString()
}

/**
 * Короткі звичайні слова, що не є абревіатурами: українські, польські й англійські — польською
 * «„TELEWIZJA NA ŻYWO”» виходило «NA» великими, як абревіатура.
 */
private val COMMON = setOf(
    "Й", "І", "ТА", "НА", "ДО", "В", "У", "З", "ЗІ", "ЗА", "ПО", "ВІД", "ДЛЯ", "БЕЗ", "ЧИ", "НЕ",
    "I", "W", "Z", "ZE", "NA", "DO", "OD", "PO", "ZA", "BEZ", "DLA", "NIE", "LUB", "CZY",
    "A", "AN", "THE", "AND", "OR", "OF", "IN", "ON", "TO", "FOR", "BY", "AT",
)

/** Лапки, всередині яких назви лишаються як є: українські «», польські „”, англійські “”. */
private val OPEN_QUOTES = setOf('«', '„', '“')
private val CLOSE_QUOTES = setOf('»', '”')
