package com.likhtar.home.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/**
 * «Доступна версія …»: що змінилось і кнопка «Оновити». Під час завантаження — відсотки замість
 * кнопки; помилка — рядком під примітками. Встановлює сама система, тут лише запуск.
 */
@Composable
fun UpdateDialog(
    version: String,
    notes: String,
    progress: Float?,
    error: String?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    LikhtarDialog(onDismiss = onDismiss, width = 640.dp) {
        Text(stringResource(R.string.ds_update_title, version), style = LikhtarType.PageTitle, color = LikhtarColors.TextPrimary)
        Text(
            notes,
            style = LikhtarType.Body2,
            color = LikhtarColors.TextSecondary,
            modifier = Modifier.padding(top = 10.dp, bottom = 14.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
        )
        error?.let { Text(stringResource(R.string.ds_update_failed, it), style = LikhtarType.Label, color = LikhtarColors.Amber, modifier = Modifier.padding(bottom = 10.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (progress != null) {
                Text(stringResource(R.string.ds_update_downloading, (progress * 100).toInt()), style = LikhtarType.Label, color = LikhtarColors.TextPrimary, modifier = Modifier.padding(12.dp))
            } else {
                LikhtarButton(label = stringResource(R.string.ds_update_install), onClick = onUpdate, primary = true)
            }
            LikhtarButton(label = stringResource(R.string.ds_update_later), onClick = onDismiss)
        }
    }
}
