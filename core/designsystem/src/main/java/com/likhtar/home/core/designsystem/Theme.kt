package com.likhtar.home.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Тема LIKHTAR. Лаунчер завжди темний — світлої схеми не передбачено. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    ) {
        // Увесь лаунчер прокручує за фокусом одним рухом (LikhtarGlide): типовий для ТВ «фокус
        // на третині» лишається, міняється лише те, як саме екран доїжджає.
        val spec = androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides remember(spec) { spec.gliding() },
            content = content,
        )
    }
}
