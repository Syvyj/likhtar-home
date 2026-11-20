package com.likhtar.home.core.catalog

import javax.inject.Inject
import javax.inject.Singleton

/** Свого сервера потоків у публічній збірці немає: адреси не міняються, позначок немає. */
@Singleton
class OwnStreamServer @Inject constructor() {
    /** Адреса для плеєра — як є. */
    fun retarget(url: String): String = url

    /** Позначка для звіту про спробу — порожня. */
    val attemptFlag: String get() = ""
}
