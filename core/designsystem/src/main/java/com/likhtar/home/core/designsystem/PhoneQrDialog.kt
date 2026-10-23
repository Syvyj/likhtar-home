package com.likhtar.home.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/**
 * Діалог «наведіть телефон на QR»: код, кроки й рядок стану внизу. [url] `null` — приставка
 * не бачить домашньої мережі, і QR показати нікуди.
 */
@Composable
fun PhoneQrDialog(
    title: String,
    url: String?,
    lines: List<String>,
    onDismiss: () -> Unit,
    status: String? = null,
    statusOk: Boolean = false,
) {
    LikhtarDialog(onDismiss = onDismiss, width = 600.dp) {
        // Крупно: діалог читають з дивана, а не з монітора.
        Text(text = title, style = LikhtarType.PageTitle, color = LikhtarColors.TextPrimary)
        if (url == null) {
            Text(
                text = stringResource(R.string.ds_qr_no_network),
                style = LikhtarType.Body2,
                color = LikhtarColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
        } else {
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QrCode(content = url, size = 200.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lines.forEach { Text(text = it, style = LikhtarType.TitleLead, color = LikhtarColors.TextPrimary) }
                    Text(text = stringResource(R.string.ds_qr_same_network, url), style = LikhtarType.Body1, color = LikhtarColors.TextSecondary)
                }
            }
        }
        status?.let {
            Text(
                text = it,
                style = LikhtarType.Label,
                color = if (statusOk) LikhtarColors.AccentBright else LikhtarColors.TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        LikhtarButton(label = stringResource(if (statusOk) R.string.ds_done else R.string.ds_close), onClick = onDismiss, primary = statusOk)
    }
}
