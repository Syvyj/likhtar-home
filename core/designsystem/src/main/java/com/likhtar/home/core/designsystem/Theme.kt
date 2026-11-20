package com.likhtar.home.core.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Системний режим зменшення руху (`ANIMATOR_DURATION_SCALE == 0`), як радить Apple HIG. */
val LocalReduceMotion = compositionLocalOf { false }

/** Системний режим підвищеного контрасту (`high_text_contrast_enabled == 1`), прибирає прозорість пігулок. */
val LocalHighContrast = compositionLocalOf { false }

/** Тема LIKHTAR. Лаунчер завжди темний — світлої схеми не передбачено. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun LikhtarTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val highContrast = remember(context) {
        runCatching {
            Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
        }.getOrDefault(false)
    }
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
            LocalReduceMotion provides reduceMotion,
            LocalHighContrast provides highContrast,
            content = content,
        )
    }
}
