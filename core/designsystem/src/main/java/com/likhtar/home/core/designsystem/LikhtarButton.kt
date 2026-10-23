package com.likhtar.home.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/**
 * Кнопка лаунчера — одна на всі екрани.
 *
 * Досі кожен розділ мав свою: дві різні `ActionPill` (сторінка тайтла й Ліхтарик),
 * кнопка діалогу, кнопка онбордингу — різні скруглення, колір фокуса, поля й збільшення.
 * Мова одна: обране й головне — матове біле, решта — темна картка; у фокусі — біле.
 *
 * [leading] — значок перед підписом (ліхтар, позначка, мікрофон): отримує колір тексту
 * й чи кнопка зараз світла, щоб значок лишався видним на обох тлах.
 * [quiet] — без підкладки, поки не у фокусі (неактивні сезони).
 */
@Composable
fun LikhtarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    quiet: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    /** Колір бренду для підпису (wordmark сервісу); білий бренд на світлій кнопці — темніє. */
    brandColor: Color? = null,
    leading: (@Composable (color: Color, onLight: Boolean) -> Unit)? = null,
    /** Після підпису: мітка UA / СУБ на кнопці сервісу. */
    trailing: (@Composable () -> Unit)? = null,
    /**
     * Задана висота (кнопка поряд із полем пошуку, 52 dp) — підпис по центру. Без неї —
     * за вмістом: fillMaxHeight завжди розтягував кнопки діалогу на весь екран.
     */
    height: Dp? = null,
) {
    FocusableSurface(
        onClick = { if (enabled) onClick() },
        onLongClick = onLongClick,
        modifier = if (height != null) modifier.height(height) else modifier,
        shape = RoundedCornerShape(LikhtarDimens.CornerMedium),
        background = when {
            primary -> LikhtarColors.GlassActive
            quiet -> Color.Transparent
            enabled -> LikhtarColors.CardBlack
            else -> LikhtarColors.SurfaceAlt
        },
        focusedBackground = LikhtarColors.GlassActive,
        focusedScale = 1f,
    ) { focused ->
        val onLight = primary || (focused && enabled)
        val color = when {
            !enabled -> LikhtarColors.TextMuted
            brandColor != null && !(onLight && brandColor == Color.White) -> brandColor
            onLight -> LikhtarColors.OnGlassActive
            enabled -> LikhtarColors.TextPrimary
            else -> LikhtarColors.TextMuted
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = (if (height != null) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            leading?.invoke(color, onLight)
            Text(text = label, style = LikhtarType.Label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing?.invoke()
        }
    }
}

/**
 * Чип вибору — варіант відповіді (порадник «Скільки часу?», «Настрій?»), підказка пошуку.
 * Не дія: дія — [LikhtarButton]. Круглий; у фокусі — матове біле, як головна кнопка.
 * Обраний без фокуса — темний зі світлою рамкою й «✓»: коли обране теж біліло, у
 * стовпчику з двома білими чипами не було видно, де курсор.
 * [muted] — другорядний варіант (давній запит), приглушений підпис.
 */
@Composable
fun LikhtarChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    muted: Boolean = false,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(LikhtarDimens.CornerPill),
        background = LikhtarColors.CardBlack,
        focusedBackground = LikhtarColors.GlassActive,
        borderColor = if (selected) LikhtarColors.GlassActive else LikhtarColors.GlassBorder,
        focusedScale = 1f,
    ) { focused ->
        Text(
            text = if (selected) "✓  $text" else text,
            style = LikhtarType.Label,
            color = when {
                focused -> LikhtarColors.OnGlassActive
                selected -> LikhtarColors.TextPrimary
                muted -> LikhtarColors.TextTertiary
                else -> LikhtarColors.TextSecondary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
