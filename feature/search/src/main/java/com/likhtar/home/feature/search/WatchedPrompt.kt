package com.likhtar.home.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import com.likhtar.home.core.catalog.TrackedShow
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.ProtoButton

/**
 * Публічна збірка: серію дивились у застосунку сервісу, і Ліхтар цього не бачить. Повернулись —
 * одне OK, і статус іде далі («переглянуто до SxEy»).
 */
@Composable
internal fun WatchedPrompt(show: TrackedShow, onAnswer: (Boolean) -> Unit) {
    val yes = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { yes.requestFocus() } }
    LikhtarDialog(onDismiss = { onAnswer(false) }, width = 420.dp, armed = true) {
        Text(
            stringResource(R.string.search_watched_q, show.item.title, show.season, show.episode),
            style = TextStyle(fontFamily = LikhtarType.Body, fontWeight = FontWeight.Bold, fontSize = 15.sp),
            color = LikhtarColors.TextPrimary,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ProtoButton(label = stringResource(R.string.search_watched_yes), onClick = { onAnswer(true) }, icon = null, modifier = Modifier.focusRequester(yes))
            ProtoButton(label = stringResource(R.string.search_watched_no), onClick = { onAnswer(false) }, icon = null)
        }
    }
}
