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
