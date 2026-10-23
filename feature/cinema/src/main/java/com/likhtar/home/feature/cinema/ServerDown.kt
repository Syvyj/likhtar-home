package com.likhtar.home.feature.cinema

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.LikhtarType

/**
 * Рядів немає, бо сервер не відповів, — кажемо це прямо й даємо спробувати ще.
 *
 * Доти розділ у такому разі був просто порожнім (у Кінозалі — сірий рядок під згином, у
 * Реаліті — нічого), і «сервер лежить» не відрізнялось від «порожньо».
 * [focus] — коли героя немає, панель і є тим, куди стає фокус з меню.
 */
@Composable
internal fun ServerDownPanel(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
    /** Що саме не приїхало: за замовчуванням — ряди сервера; ТВ Ефір каже про телепрограму. */
    title: String = stringResource(R.string.cinema_server_down_title),
    hint: String = stringResource(R.string.cinema_server_down_hint),
) {
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = title, style = LikhtarType.PageTitle, color = LikhtarColors.TextPrimary)
        Text(
            text = hint,
            style = LikhtarType.Body1,
            color = LikhtarColors.TextSecondary,
        )
        LikhtarButton(
            label = stringResource(R.string.cinema_retry),
            primary = true,
            onClick = onRetry,
            modifier = if (focus != null) Modifier.focusRequester(focus) else Modifier,
        )
    }
}
