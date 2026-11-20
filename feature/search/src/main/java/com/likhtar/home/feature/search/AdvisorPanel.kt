package com.likhtar.home.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.likhtar.home.core.catalog.WatchFormat
import com.likhtar.home.core.catalog.WatchMood
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarType

/**
 * «Що подивитись» — бічною панеллю (макет, екран 4), а не двома рядами пігулок на пів сторінки.
 * Формат перемикається на місці; вибір настрою — відповідь: панель закривається, порада лягає героєм.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdvisorPanel(
    advisor: AdvisorUiState,
    onFormat: (WatchFormat) -> Unit,
    onMood: (WatchMood) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LikhtarDialog(onDismiss = onDismiss, width = 400.dp, side = true) {
        Text(stringResource(R.string.search_what_to_watch), style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 17.sp), color = LikhtarColors.TextPrimary)
        Text(stringResource(R.string.search_format_question), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = LikhtarColors.TextDim, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WatchFormat.entries.forEachIndexed { i, f ->
                PillChip(formatLabel(f), on = f == advisor.format, onClick = { onFormat(f) }, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier)
            }
        }
        Text(stringResource(R.string.search_mood_question), style = TextStyle(fontFamily = LikhtarType.Body, fontSize = 11.5.sp), color = LikhtarColors.TextDim, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WatchMood.entries.forEach { m ->
                PillChip(moodLabel(m), on = m == advisor.mood, onClick = { onMood(m); onDismiss() })
            }
        }
    }
}
