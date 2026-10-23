package com.likhtar.home.core.catalog

import javax.inject.Inject
import javax.inject.Singleton

/** Свій сервер роздач людини — у збірці для магазину його немає. */
@Singleton
class OwnTorrentServer @Inject constructor() {
    val url: String? get() = null

    fun serverTold(base: String): String? = null
}
