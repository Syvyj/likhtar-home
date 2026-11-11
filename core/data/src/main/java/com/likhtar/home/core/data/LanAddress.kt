package com.likhtar.home.core.data

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * IPv4 домашньої мережі — адреса, за якою телефон дістане приставку («З телефону»,
 * підключення Home Assistant). VPN (tun0 на приставці — 10.5.0.2) пропускаємо: телефон
 * туди не дістане. Кабель краще за Wi‑Fi, якщо є обидва.
 */
fun lanAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual && VPN.none(it.name::startsWith) }
        .sortedBy { if (it.name.startsWith("eth")) 0 else 1 }
        .flatMap { nic -> nic.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress } }
        .firstOrNull()?.hostAddress
}.getOrNull()

private val VPN = listOf("tun", "ppp", "wg", "tap", "ipsec")

/**
 * Запит прийшов із домашньої мережі: приватна IPv4, link-local чи IPv6 ULA (fc00::/7). Сервери
 * слухають усі інтерфейси, тож без цього на сторінки «з телефона» стукали б і з VPN, і з
 * публічної IPv6 приставки.
 */
fun java.net.Socket.fromLocalNetwork(): Boolean {
    val a = inetAddress ?: return false
    return a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress ||
        (a is java.net.Inet6Address && (a.address[0].toInt() and 0xFE) == 0xFC)
}
