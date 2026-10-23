package com.likhtar.home.core.model

/**
 * Яка це збірка. `play` — для магазину: лише офіційні джерела (застосунки
 * сервісів, YouTube), без власних потоків, TorrServer і сторонніх плеєрів; MEGOGO й SWEET.TV —
 * лише для України; TMDB — через наш сервер, без ключа в APK. `personal` — домашня, як була.
 *
 * Значення ставить застосунок на старті (LikhtarApplication) — модулі не мусять мати
 * flavor-ів, щоб про це знати.
 */
object Distribution {
    @Volatile
    var store: Boolean = false
}
