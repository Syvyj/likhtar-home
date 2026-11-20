package com.likhtar.home.core.catalog

/**
 * Коли Ліхтар востаннє ховався за іншим застосунком (сервіс, плеєр). Пише [android.app.Activity.onStop]
 * головної активності; читає «Подивились?» — питати лише після справжнього виходу дивитись.
 */
object AppForeground {
    @Volatile
    var stoppedAt: Long = 0L
}
