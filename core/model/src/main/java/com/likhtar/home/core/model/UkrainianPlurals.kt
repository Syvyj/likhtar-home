package com.likhtar.home.core.model

/**
 * Українські форми множини для підписів у UI.
 *
 * Android-плюралізація тут не підходить: у лаунчері рядки збираються у ViewModel,
 * а не в ресурсах, і форма потрібна разом із числом («4 застосунки»).
 */
object UkrainianPlurals {

    /** 1 застосунок · 2–4 застосунки · 5+ застосунків (з винятком для 11–14). */
    fun apps(count: Int): String = pick(
        count = count,
        one = "застосунок",
        few = "застосунки",
        many = "застосунків",
    )

    fun pick(count: Int, one: String, few: String, many: String): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }
}
