package com.likhtar.home.core.designsystem

/**
 * Коли створено вікно лаунчера (uptime) — точка відліку замірів «до карток». Не старт процесу:
 * службу кнопок система піднімає разом із процесом задовго до вікна, і такий відлік бреше на секунди.
 */
object LaunchClock {
    @Volatile var windowCreatedAt = 0L

    fun sinceWindow(): Long = android.os.SystemClock.uptimeMillis() - windowCreatedAt
}
