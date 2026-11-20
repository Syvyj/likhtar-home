package com.likhtar.home.core.catalog

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/** Рядок `watch_next_program` разом з тим, що потрібне лише для відбору. */
data class WatchNextRow(val resume: ServiceResume, val type: Int, val browsable: Boolean)

/** `WATCH_NEXT_TYPE_WATCHLIST` — «відкласти на потім», а не «продовжити». */
private const val WATCHLIST = 3

/** Стільки карток ряду видно одразу; серед них один застосунок займає не більше половини. */
private const val FIRST_PLACES = 6

/**
 * Що з Watch Next іде в «Продовжити»: без списку перегляду й прихованих (`browsable = 0`),
 * одна назва застосунку — один рядок (найсвіжіший), і застосунок, що пише кожну серію, не
 * займає всіх перших місць: понад половину з них — у кінець, а не геть (lounge, `stage_candidates`;
 * free-my-tv `WatchNext.java`).
 */
fun pickWatchNext(rows: List<WatchNextRow>): List<ServiceResume> {
    val fresh = rows.asSequence()
        .filter { it.type != WATCHLIST && it.browsable }
        .map { it.resume }
        .sortedByDescending { it.updatedAt }
        .distinctBy { it.packageName to it.title }
        .toList()
    if (fresh.distinctBy { it.packageName }.size < 2) return fresh
    val share = FIRST_PLACES / 2
    val taken = mutableMapOf<String, Int>()
    val first = mutableListOf<ServiceResume>()
    val later = mutableListOf<ServiceResume>()
    for (item in fresh) {
        val n = taken.getOrDefault(item.packageName, 0)
        if (first.size < FIRST_PLACES && n >= share) later += item else first += item
        taken[item.packageName] = n + 1
    }
    return first + later
}

/**
 * Картинку з Watch Next пишуть чужі застосунки — тож тягнемо лише `content://`,
 * `android.resource://` і публічний https (free-my-tv `NetPicture`): адреса в домашній мережі
 * чи на самій приставці — не картинка, а запит туди, куди лаунчер ходити не мусить.
 *
 * ponytail: без DNS-резолву — ім'я, що резолвиться в LAN, пройде; додати резолв, якщо стане важливо.
 */
fun safeArtwork(uri: String?): String? {
    if (uri.isNullOrBlank()) return null
    // Застосунки пишуть і з пробілами в шляху — URI їх не приймає, і картинка губилась.
    val clean = uri.trim().replace(" ", "%20")
    val parsed = runCatching { URI(clean) }.getOrNull() ?: return null
    return when (parsed.scheme?.lowercase()) {
        "content", "android.resource" -> clean
        "https" -> clean.takeIf { parsed.userInfo == null && publicHost(parsed.host) }
        else -> null
    }
}

private fun publicHost(rawHost: String?): Boolean {
    val host = rawHost?.lowercase()?.removePrefix("[")?.removeSuffix("]")?.trimEnd('.') ?: return false
    if (host == "localhost" || LOCAL_SUFFIXES.any(host::endsWith)) return false
    // Числові записи, які резолвер теж читає як IP («3232235525», «0300.0250.0.5», «0xC0A80005»,
    // «192.168.5»): дозволяємо лише звичайні чотири числа без провідних нулів.
    if (NUMERIC_HOST.matches(host) && !CANONICAL_IPV4.matches(host)) return false
    // Лише IP-літерали — getByName на імені пішов би в DNS.
    if (!(IPV4.matches(host) || ':' in host)) return true
    val ip = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
    val b = ip.address
    val cgnat = b.size == 4 && b[0].toInt() == 100 && (b[1].toInt() and 0xC0) == 64
    val uniqueLocal = ip is Inet6Address && (b[0].toInt() and 0xFE) == 0xFC
    return !(ip.isLoopbackAddress || ip.isSiteLocalAddress || ip.isLinkLocalAddress || ip.isAnyLocalAddress || cgnat || uniqueLocal)
}

private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
private val NUMERIC_HOST = Regex("""(0x[0-9a-f]+|\d+)(\.(0x[0-9a-f]+|\d+))*""")
private val CANONICAL_IPV4 = Regex("""(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}""")
private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal")
