package com.likhtar.home.feature.settings

import java.net.URLEncoder

/** Що вписати в звіт про помилку: приставка й збірка — без жодних особистих даних. */
data class BugReportDevice(
    val maker: String,
    val model: String,
    val android: String,
    val sdk: Int,
    val version: String,
    val build: String,
)

/**
 * Посилання на нову issue з уже вписаними приставкою, Android і версією — для QR у «Про
 * застосунок» (як у Nottilo): з телевізора текст не набереш, а з телефона — так. Шаблон лише
 * українською й англійською. Назви приставки обрізаємо: QR із довгим посиланням — дрібний.
 */
fun bugReportUrl(repo: String, d: BugReportDevice): String {
    val body = """
        |Що сталося / What happened:
        |
        |
        |---
        |${d.maker.take(NAME_MAX)} ${d.model.take(NAME_MAX)}
        |Android ${d.android.take(NAME_MAX)} (API ${d.sdk})
        |Likhtar ${d.version.take(NAME_MAX)} (${d.build})
    """.trimMargin()
    return "$repo/issues/new?title=${encode("[${d.version.take(NAME_MAX)}] ")}&body=${encode(body)}"
}

private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

private const val NAME_MAX = 60
