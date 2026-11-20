package com.likhtar.home.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Text
import com.likhtar.home.R
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(val store: NotificationFeedStore) : ViewModel()

/**
 * Бічна панель сповіщень: OK на картці — відкрити (як тап у системній панелі), під нею — кнопки
 * дій застосунку, «Прибрати» і «Сховати від застосунку».
 */
@Composable
fun NotificationsPanel(list: List<TvNotification>, store: NotificationFeedStore, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LaunchedEffect(list.isEmpty()) { if (list.isEmpty()) onDismiss() }
    LikhtarDialog(onDismiss = onDismiss, width = 600.dp, side = true, armed = true) {
        Text(stringResource(R.string.notifications_title), style = LikhtarType.PageTitle, color = LikhtarColors.TextPrimary, modifier = Modifier.padding(bottom = 10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.heightIn(max = 600.dp)) {
            items(list, key = { it.key }) { n ->
                // «Вниз» з картки на всю ширину Compose вів до найближчої до центру кнопки — «Сховати від
                // застосунку»: одне ↓ + OK ховало весь застосунок. Тепер ↓ — завжди на першу кнопку ряду.
                val firstPill = remember { FocusRequester() }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FocusableSurface(
                        onClick = {
                            store.open(n.key)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                            .then(if (n == list.first()) Modifier.focusRequester(first) else Modifier)
                            .focusProperties { down = firstPill },
                        shape = RoundedCornerShape(12.dp),
                        background = LikhtarColors.Surface,
                        focusedBackground = LikhtarColors.GlassActive,
                        focusedScale = 1.02f,
                    ) { focused ->
                        val ink = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextPrimary
                        val sub = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextSecondary
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(n.app, style = LikhtarType.Label, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (n.title.isNotBlank()) Text(n.title, style = LikhtarType.TitleLead, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (n.text.isNotBlank()) Text(n.text, style = LikhtarType.Body1, color = sub, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        n.actions.forEachIndexed { index, label ->
                            PanelPill(label, if (index == 0) Modifier.focusRequester(firstPill) else Modifier) {
                                store.act(n.key, index)
                                onDismiss()
                            }
                        }
                        PanelPill(stringResource(R.string.notifications_clear), if (n.actions.isEmpty()) Modifier.focusRequester(firstPill) else Modifier) { store.dismiss(n.key) }
                        PanelPill(stringResource(R.string.notifications_mute_app)) { store.mute(n.pkg) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        background = LikhtarColors.Surface,
        focusedBackground = LikhtarColors.GlassActive,
        focusedScale = 1.04f,
    ) { focused ->
        Text(
            label,
            style = LikhtarType.Label,
            color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
