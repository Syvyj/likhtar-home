package com.likhtar.home.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/**
 * Голова сторінки — одна на всі: «Усі стрімінги», добірки, список Реаліті. Досі
 * підсторінки Кінозалу писали заголовок 30 sp, а Налаштування й Розумний дім — 22 sp, хоч
 * це сторінки одного рівня. Підзаголовок — лічильник чи «Каталог».
 */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier.padding(top = LikhtarDimens.PageHeaderTop)) {
        Text(text = title, style = LikhtarType.HeroTitle, color = LikhtarColors.TextPrimary)
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = LikhtarType.Body2,
                color = LikhtarColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
