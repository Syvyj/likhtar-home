package com.likhtar.home.core.model

import kotlin.coroutines.cancellation.CancellationException

/**
 * `runCatching`, що не ковтає скасування корутини.
 *
 * Звичайний `runCatching` ловить і `CancellationException`: пішли з екрана — запит
 * «провалився», і результат-невдача осідав у кеші як «логотипа немає», «обкладинки немає»
 * до перезапуску лаунчера. Inline — тож усередині можна кликати
 * suspend-функції.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
