package com.likhtar.home.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

/**
 * Дочекатися інтернету. Приставка часто стартує раніше, ніж підключиться Wi-Fi: шпалери й
 * погода, спитані в цю мить, лишались порожніми аж до наступного оновлення (годину й більше).
 * Хто отримав порожньо — чекає тут і питає ще раз.
 */
suspend fun awaitOnline(context: Context) {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
    val online = { cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }
    if (online()) return
    callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(Unit) }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.first()
}
