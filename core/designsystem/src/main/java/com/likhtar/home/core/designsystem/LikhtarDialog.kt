package com.likhtar.home.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.view.KeyEvent as AndroidKeyEvent

/**
 * Каркас модального вікна LIKHTAR.
 *
 * Це **справжнє вікно** (`Dialog`), а не Box поверх екрана, і саме в цьому суть. Накладений
 * Box не забирає фокус: користувач бачить меню, тисне вниз — і фокус іде в список під ним,
 * дії відбуваються у фоні. Окреме вікно тримає фокус усередині себе, бо так влаштована
 * система, а не тому, що ми щось вгадали з `focusProperties`.
 *
 * Друга річ, яку воно вирішує: «хвіст» довгого натискання. Меню відкривається на `KeyDown`
 * із прапорцем long-press, але поки користувач тримає кнопку, система шле **повторні**
 * `KeyDown` (autorepeat) — вони прилітають уже в меню і без захисту читаються як вибір
 * пункту ще до того, як кнопку відпустили. Тому OK/Enter тут глухі, аж доки не прийде
 * перший `KeyUp` — тобто доки палець реально не знято з кнопки. Навігація стрілками
 * працює одразу: її довге натискання не породжує.
 */
@Composable
fun LikhtarDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 420.dp,
    // Бічна панель на всю висоту біля правого краю — тайтл лишається видно ліворуч.
    side: Boolean = false,
    // Відкрите звичайним кліком (на KeyUp), а не довгим натисканням: хвоста autorepeat
    // немає, і перше OK — уже вибір, а не «відпустили кнопку».
    armed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var confirmArmed by remember { mutableStateOf(armed) }

        Box(if (side) Modifier.fillMaxSize() else Modifier, contentAlignment = Alignment.CenterEnd) {
        Column(
            modifier = modifier
                .width(width)
                .then(if (side) Modifier.fillMaxHeight() else Modifier)
                .onPreviewKeyEvent { event ->
                    val isConfirm = event.nativeKeyEvent.keyCode in CONFIRM_KEY_CODES
                    when {
                        !isConfirm -> false
                        confirmArmed -> false
                        event.type == KeyEventType.KeyUp -> {
                            confirmArmed = true
                            true
                        }
                        // Свіже натискання (не автоповтор) — уже вибір: KeyUp утримання на
                        // повільній приставці приходить у головне вікно, поки меню ще
                        // відкривається, і перше OK у меню мовчки ковталось.
                        event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> {
                            confirmArmed = true
                            false
                        }
                        // KeyDown (зокрема autorepeat утримуваної кнопки) до першого KeyUp.
                        else -> true
                    }
                }
                .clip(
                    if (side) {
                        RoundedCornerShape(topStart = LikhtarDimens.CornerLarge, bottomStart = LikhtarDimens.CornerLarge)
                    } else {
                        RoundedCornerShape(LikhtarDimens.CornerLarge)
                    },
                )
                .background(LikhtarColors.Surface)
                .padding(if (side) 32.dp else 16.dp),
            content = content,
        )
        }
    }
}

private val CONFIRM_KEY_CODES = setOf(
    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
    AndroidKeyEvent.KEYCODE_ENTER,
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
)
