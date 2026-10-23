package com.likhtar.home.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/** Куди донатити: ті самі сторінки, що на сайті й у `.github/FUNDING.yml`. */
private data class Donate(val name: Int, val note: Int, val url: String)

private val KOFI = Donate(R.string.ds_support_kofi, R.string.ds_support_kofi_note, "https://ko-fi.com/syvyj")
private val BMC = Donate(R.string.ds_support_bmc, R.string.ds_support_bmc_note, "https://buymeacoffee.com/syvyj")
private val MONO = Donate(R.string.ds_support_mono, R.string.ds_support_mono_note, "https://send.monobank.ua/jar/5juJPj142b")

/**
 * «Підтримати автора»: три QR поруч — Ko-fi, Buy Me a Coffee, банка Monobank. Людина сканує той,
 * яким їй зручно платити, і одразу потрапляє на оплату, без проміжного сайту.
 * Українському інтерфейсу банка — першою.
 */
@Composable
fun SupportDialog(onDismiss: () -> Unit) {
    val ukrainian = LocalConfiguration.current.locales[0].language == "uk"
    val options = if (ukrainian) listOf(MONO, KOFI, BMC) else listOf(KOFI, BMC, MONO)
    LikhtarDialog(onDismiss = onDismiss, width = 760.dp) {
        Text(text = stringResource(R.string.ds_support_title), style = LikhtarType.PageTitle, color = LikhtarColors.TextPrimary)
        Text(
            text = stringResource(R.string.ds_support_lead),
            style = LikhtarType.Body1,
            color = LikhtarColors.TextSecondary,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(bottom = 18.dp)) {
            options.forEach { option ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(200.dp)) {
                    QrCode(content = option.url, size = 176.dp)
                    Text(
                        text = stringResource(option.name),
                        style = LikhtarType.TitleLead,
                        color = LikhtarColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(option.note),
                        style = LikhtarType.Body2,
                        color = LikhtarColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        LikhtarButton(label = stringResource(R.string.ds_close), onClick = onDismiss, primary = true)
    }
}
