package com.likhtar.home.feature.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.TriviaStatement
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.ProtoButton
import com.likhtar.home.core.designsystem.requestAfterFrame

/**
 * «Правда чи брехня»: твердження на кадрі тайтла (кадр малює той, хто кличе), «Правда» / «Брехня»,
 * після відповіді — вердикт, пояснення й дві кнопки. Те саме в Ліхтарику (раунд дня) і героєм Кінозалу.
 *
 * ← з першої кнопки й → з останньої віддаються [onEdgeLeft]/[onEdgeRight]: на героєві Кінозалу
 * вони гортають слайди (спека етапу 2).
 */
@Composable
fun TriviaPanel(
    statement: TriviaStatement,
    /** Що людина відповіла: `null` — ще не відповіла. */
    answered: Boolean?,
    kicker: String,
    onAnswer: (Boolean) -> Unit,
    nextLabel: String,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
    onEdgeLeft: (() -> Unit)? = null,
    onEdgeRight: (() -> Unit)? = null,
    menuFocus: FocusRequester? = null,
    width: Dp = 460.dp,
) {
    // Кнопки міняються після відповіді — фокус переходить на першу нову, а не губиться.
    LaunchedEffect(statement.id, answered != null) {
        if (answered != null) runCatching { firstFocus.requestAfterFrame() }
    }
    Column(modifier.widthIn(max = width), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(kicker.uppercase(), style = TextStyle(fontFamily = LikhtarType.Mono, fontSize = 11.5.sp, letterSpacing = 0.8.sp), color = LikhtarColors.Glow)
        Text(
            listOf(statement.item.title, statement.item.year).filter { it.isNotBlank() }.joinToString(" · "),
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = LikhtarColors.Paper.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            statement.text,
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 27.sp, shadow = OverviewShadow),
            color = LikhtarColors.TextPrimary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (answered != null) {
            val right = answered == statement.truth
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    stringResource(if (statement.truth) R.string.trivia_true else R.string.trivia_false).uppercase(),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 0.6.sp),
                    color = LikhtarColors.OnGlassActive,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (right) LikhtarColors.Amber else LikhtarColors.Danger)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    stringResource(if (right) R.string.trivia_right else R.string.trivia_wrong),
                    style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    color = if (right) LikhtarColors.Glow else LikhtarColors.Danger,
                )
            }
            Text(
                statement.why,
                style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 13.sp, lineHeight = 18.sp, shadow = OverviewShadow),
                color = LikhtarColors.Paper.copy(alpha = 0.9f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 4.dp)) {
            val (firstLabel, secondLabel) = if (answered == null) {
                stringResource(R.string.trivia_true) to stringResource(R.string.trivia_false)
            } else {
                nextLabel to stringResource(R.string.trivia_open)
            }
            ProtoButton(
                label = firstLabel,
                icon = null,
                height = 36.dp,
                onClick = { if (answered == null) onAnswer(true) else onNext() },
                modifier = Modifier.focusRequester(firstFocus)
                    .then(if (menuFocus != null) Modifier.focusProperties { up = menuFocus } else Modifier)
                    .edge(Key.DirectionLeft, onEdgeLeft),
            )
            ProtoButton(
                label = secondLabel,
                icon = null,
                height = 36.dp,
                onClick = { if (answered == null) onAnswer(false) else onOpen() },
                modifier = Modifier
                    .then(if (menuFocus != null) Modifier.focusProperties { up = menuFocus } else Modifier)
                    .edge(Key.DirectionRight, onEdgeRight),
            )
        }
    }
}

private fun Modifier.edge(key: Key, action: (() -> Unit)?): Modifier = if (action == null) this else onKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && e.key == key) { action(); true } else false
}
