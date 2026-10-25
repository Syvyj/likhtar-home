package com.likhtar.home.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.likhtar.home.core.designsystem.LikhtarButton
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarType
import com.likhtar.home.core.designsystem.QrCode
import com.likhtar.home.feature.settings.R

/**
 * Вхід у Plex кодом: як у застосунках самого Plex, людина відкриває plex.tv/link на телефоні
 * (QR веде туди ж) і вводить [code]. Вікно чекає, поки вхід пройде, — його закриває той, хто чекає.
 */
@Composable
internal fun PlexLinkDialog(code: String, onDismiss: () -> Unit) {
    LikhtarDialog(onDismiss = onDismiss, width = 620.dp) {
        Text(stringResource(R.string.settings_media_plex_link_title), style = LikhtarType.PageTitle, color = LikhtarColors.TextPrimary)
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 14.dp, bottom = 18.dp),
        ) {
            QrCode(content = PLEX_LINK, size = 168.dp)
            Column {
                Text(stringResource(R.string.settings_media_plex_link_lead), style = LikhtarType.Body1, color = LikhtarColors.TextSecondary)
                // Код читають з дивана й набирають на телефоні — крупно й розрідкою.
                Text(code, style = LikhtarType.PageTitle.copy(fontSize = 56.sp, letterSpacing = 8.sp), color = LikhtarColors.TextPrimary, modifier = Modifier.padding(top = 10.dp))
            }
        }
        LikhtarButton(label = stringResource(com.likhtar.home.core.designsystem.R.string.ds_close), onClick = onDismiss)
    }
}

private const val PLEX_LINK = "https://plex.tv/link"
