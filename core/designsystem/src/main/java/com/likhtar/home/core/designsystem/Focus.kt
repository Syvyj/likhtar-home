package com.likhtar.home.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Фокус-контракт LIKHTAR (з §2 плану):
 *  - на екрані завжди рівно один видимий фокус — його малює лише сфокусований елемент;
 *  - початковий фокус кожного екрана — перша доступна картка;
 *  - hover ніколи не є станом фокусу.
 *
 * Повертає [FocusRequester], який запитує фокус при появі екрана та повторно —
 * коли змінюється [key] (наприклад, коли з репозиторію приходить перший список карток).
 */
@Composable
fun rememberInitialFocusRequester(key: Any? = Unit): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) {
        // requestFocus падає, поки вузол не приєднаний до дерева фокуса. На TV перший кадр
        // екрана стабільно потрапляє в це вікно, тому пробуємо ще кілька кадрів підряд —
        // інакше початковий фокус з'являється лише після першого натискання пульта.
        requester.requestWithRetry()
    }
    return requester
}

/**
 * Запит фокуса, поки вузол не приєднався до дерева: пробуємо зараз і ще кадр за кадром.
 * Один помічник на всі екрани — до нього було шість ручних циклів із чотирма різними
 * лімітами. Повертає, чи фокус узяли.
 */
suspend fun FocusRequester.requestWithRetry(frames: Int = FOCUS_FRAMES): Boolean {
    repeat(frames) {
        if (tryFocus()) return true
        withFrameNanos { }
    }
    return false
}

/**
 * Запит фокуса з відповіддю, чи вдалося. `requestFocus()` без аргументів у Compose 1.12 нічого не
 * повертає й на неприєднаному вузлі не кидає (лише друкує FocusRelatedWarning), тож «немає
 * винятку» більше не означає «фокус узяли»: помічник ніколи не повторював спробу.
 * Варіант з напрямком повертає Boolean; runCatching — для FocusRequester.Default/Cancel.
 */
fun FocusRequester.tryFocus(): Boolean =
    runCatching { requestFocus(androidx.compose.ui.focus.FocusDirection.Enter) }.getOrDefault(false)

/** Дванадцять кадрів — з запасом на LazyColumn, що компонує вміст на етапі розмітки. */
const val FOCUS_FRAMES = 12

/**
 * Кадр очікування перед запитом фокуса.
 *
 * Запит із поточного кадру гине мовчки: вузол (оверлей, сітка після «Відкрити всі»)
 * ще не розміщений у дереві фокуса, requestFocus кидає виняток — і елемент потім не
 * реагує на пульт узагалі. Тому спершу один кадр, потім запит у [runCatching].
 */
suspend fun FocusRequester.requestAfterFrame() {
    withFrameNanos { }
    runCatching { requestFocus() }
}

