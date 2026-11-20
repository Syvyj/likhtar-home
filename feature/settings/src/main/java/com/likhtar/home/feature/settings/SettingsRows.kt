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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Спільна оболонка всіх рядків панелі (.row у likhtar-settings-modern.html):
 * картка #302c25 з рамкою #ffffff12, радіус 11 dp; у фокусі — #3a352c і світлий обідок #fffaf2 з відступом.
 */
@Composable
private fun RowShell(
    text: String,
    subtitle: String?,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    trailing: @Composable (focused: Boolean) -> Unit,
) {
    val exit = LocalDetailExit.current
    var focusedNow by remember { mutableStateOf(false) }
    val corner = 11.dp
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .onFocusChanged { focusedNow = it.isFocused }
                .drawWithContent {
                    drawContent()
                    if (focusedNow) {
                        val strokeWidth = 1.5.dp.toPx()
                        val gap = 1.5.dp.toPx()
                        val outset = gap + strokeWidth / 2f
                        drawRoundRect(
                            color = LikhtarColors.GlassActive,
                            topLeft = Offset(-outset, -outset),
                            size = Size(size.width + 2 * outset, size.height + 2 * outset),
                            cornerRadius = CornerRadius(corner.toPx() + outset),
                            style = Stroke(width = strokeWidth),
                        )
                    }
                }
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .then(exit?.let { target -> Modifier.focusDirections { left = target } } ?: Modifier),
            shape = RoundedCornerShape(corner),
            background = LikhtarColors.Surface,
            focusedBackground = LikhtarColors.SurfaceHigh,
            borderColor = Color.White.copy(alpha = 0.07f),
            focusRingColor = Color.Transparent,
            focusedScale = 1f,
            instantFill = true,
        ) { focused ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text,
                        style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp),
                        color = LikhtarColors.GlassActive,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp, lineHeight = 14.sp),
                            color = LikhtarColors.WarmMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                trailing(focused)
            }
        }
    }
}

/**
 * Перемикач (.toggle у likhtar-settings-modern.html): капсула 39×23 dp,
 * #f5be4f увімкнено / #5f5a54 вимкнено, білий бігунок 17×17 dp.
 */
@Composable
internal fun ToggleRow(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
) {
    RowShell(text = text, subtitle = subtitle, onClick = onToggle, focusRequester = null) { _ ->
        // Той самий перемикач-пігулка, що й у швидкому меню Головної та панелі «Мій дім».
        com.likhtar.home.core.designsystem.LikhtarSwitch(checked)
    }
}

/**
 * Вибір зі списку (.dialog button у likhtar-settings-modern.html): вибраний варіант має галочку ✓ #f5be4f праворуч.
 */
@Composable
internal fun ChoiceRow(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    RowShell(text = text, subtitle = null, onClick = onSelect, focusRequester = focusRequester) { _ ->
        if (selected) {
            Text(
                text = "✓",
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = LikhtarColors.LanternAmber,
            )
        }
    }
}

/** Значення, яке OK змінює (.value у likhtar-settings-modern.html): текст #c9c2b8 і стрілка ›. */
@Composable
internal fun ValueRow(
    text: String,
    value: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
) {
    RowShell(text = text, subtitle = subtitle, onClick = onClick, focusRequester = focusRequester) { _ ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 12.5.sp),
                    color = LikhtarColors.WarmStone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 185.dp),
                )
            }
            Text(
                text = "›",
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 15.sp),
                color = LikhtarColors.WarmStone,
            )
        }
    }
}

/**
 * Дія, а не стан. [destructive] фарбує її в тривожний колір.
 */
@Composable
internal fun ActionRow(
    text: String,
    action: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    destructive: Boolean = false,
) {
    RowShell(text = text, subtitle = subtitle, onClick = onClick, focusRequester = null) { _ ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = action,
                style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Medium, fontSize = 12.5.sp),
                color = if (destructive) LikhtarColors.Danger else LikhtarColors.WarmStone,
            )
            Text(
                text = "›",
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 15.sp),
                color = if (destructive) LikhtarColors.Danger else LikhtarColors.WarmStone,
            )
        }
    }
}

/** Підзаголовок групи всередині розділу (.group-title у likhtar-settings-modern.html): ВЕЛИКИМИ літерами #c7ae7c. */
@Composable
internal fun GroupTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = TextStyle(
            fontFamily = LikhtarType.Body,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            letterSpacing = 1.1.sp,
        ),
        color = LikhtarColors.WarmGoldSoft,
        modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
internal fun Note(text: String) {
    // .about: 26 px / 1.5 .82, до 640 px — текстом, без коробки.
    Text(
        text = text,
        style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp, lineHeight = 16.5.sp),
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
