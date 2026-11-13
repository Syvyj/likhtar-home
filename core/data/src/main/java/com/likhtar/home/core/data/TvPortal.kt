package com.likhtar.home.core.data

import org.json.JSONObject

/**
 * Обліковка порталу Stalker / Ministra: адреса, логін, ключ і MAC, під яким приставка входить.
 * MAC генерується один раз (префікс MAG — 00:1A:79) і далі не міняється: портал рахує пристрої.
 */
data class TvPortal(val url: String, val login: String, val key: String, val mac: String) {
    val valid: Boolean get() = url.startsWith("http") && login.isNotBlank() && key.isNotBlank()

    fun encode(): String = JSONObject().put("u", url).put("l", login).put("k", key).put("m", mac).toString()

    companion object {
        fun decode(raw: String?): TvPortal? = runCatching {
            val o = JSONObject(raw ?: return null)
            TvPortal(o.getString("u"), o.optString("l"), o.optString("k"), o.optString("m").ifBlank { newMac() })
        }.getOrNull()

        /** Адреса порталу без `/c/` і `index.html` — до неї дописуємо `/server/load.php`. */
        fun base(url: String): String = url.trim().trimEnd('/')
            .removeSuffix("/index.html").removeSuffix("/c").removeSuffix("/server/load.php").trimEnd('/')

        fun newMac(): String = "00:1A:79:" + (0 until 3).joinToString(":") { "%02X".format(java.security.SecureRandom().nextInt(256)) }
    }
}
