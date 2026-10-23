package com.likhtar.home.core.designsystem

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Тема LIKHTAR. Лаунчер завжди темний — світлої схеми не передбачено. */
@Composable
fun LikhtarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = LikhtarColors.Accent,
            onPrimary = LikhtarColors.OnAccent,
            background = LikhtarColors.Background,
            onBackground = LikhtarColors.TextPrimary,
            surface = LikhtarColors.Surface,
            onSurface = LikhtarColors.TextPrimary,
            border = LikhtarColors.Border,
        ),
        content = content,
    )
}
