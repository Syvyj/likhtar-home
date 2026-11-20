package com.likhtar.home.feature.apps.cloud

import kotlinx.serialization.Serializable

/** Сторінка каталогу хмарних ігор; [search] — одразу з пошуком, [mine] — лише мої. */
@Serializable
data class CloudCatalogRoute(val search: Boolean = false, val mine: Boolean = false, val system: String? = null)

/** Картка хмарної гри за ключем зведення ([com.likhtar.home.core.data.cloud.cloudKey]). */
@Serializable
data class CloudGameRoute(val key: String)

/** «Які сервіси в тебе є?»: [first] — перший запуск. */
@Serializable
data class CloudSetupRoute(val first: Boolean = false)
