package com.likhtar.home.core.tv

import java.io.InputStream

/**
 * Канал із плейлиста провайдера.
 *
 * [url] — те, чого немає в каталозі iptv-org, і саме через це плейлист потрібен: каталог
 * знає, які канали існують, а посилання на потік дає лише провайдер.
 */
data class M3uChannel(
    /** `tvg-id`. Порожній — у плейлисті його не було, зіставляти доведеться за назвою. */
    val id: String,
    val name: String,
    val group: String,
    val logo: String,
    val url: String,
)

/**
 * Розбір плейлиста M3U.
 *
 * Написано під реальні файли провайдерів, а не під приклад із документації: у них
 * зустрічається все одразу — `#EXTINF` без коми, відсутній `tvg-id`, `group-title` із
 * пробілами й порожній, дублікати каналів і обрізаний останній рядок. Тому жоден із цих
 * випадків не виняток, а рядок, який або пропускається, або добирається з того, що є.
 *
 * `tvg-logo` беремо охоче: логотипи для чужої людини приходять від її провайдера, і нам не
 * доводиться лікувати їх руками, як власні через `r300x300`.
 */
class M3uParser {

    fun parse(input: InputStream): List<M3uChannel> {
        val channels = mutableListOf<M3uChannel>()
        val seen = mutableSetOf<String>()
        var pending: Pending? = null

        input.bufferedReader().forEachLine { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith(EXTINF) -> pending = parseExtinf(line)
                // Решта директив (`#EXTGRP`, `#EXTVLCOPT`, `#PLAYLIST`) стоять МІЖ `#EXTINF`
                // і посиланням, тож на них не можна скидати накопичене.
                line.startsWith("#") -> Unit
                else -> {
                    val info = pending
                    pending = null
                    if (info != null) {
                        // Ключ — канонізована НАЗВА, а не id: у плейлистах один канал часто
                        // лежить двічі (основний і резервний потік), і в другого рядка
                        // `tvg-id` зазвичай немає — за id такі дублікати не збігаються.
                        // Канонізація заодно згортає абетки, тож `ICTV` і `IСТV` — один канал.
                        val key = dedupeKey(info.name).ifBlank { info.id }
                        if (key.isNotBlank() && seen.add(key)) {
                            channels += M3uChannel(
                                id = info.id,
                                name = info.name,
                                group = info.group,
                                logo = info.logo,
                                url = line,
                            )
                        }
                    }
                }
            }
        }
        // Обрізаний останній рядок: `#EXTINF` без посилання нижче — канал без потоку,
        // відкривати нічого, тому просто не потрапляє в список.
        return channels
    }

    private fun parseExtinf(line: String): Pending {
        val body = line.removePrefix(EXTINF)
        // Кома-роздільник — перша ПОЗА лапками, і це не дрібниця: у справжньому плейлисті
        // iptv-org атрибут `http-user-agent` містить «KHTML, like Gecko», тож перша кома в
        // рядку відрізала б назву посеред User-Agent. Остання теж не годиться — сама назва
        // буває з комою («2+2, повний день»).
        val comma = body.separatorComma()
        val attributes = if (comma >= 0) body.substring(0, comma) else body
        val name = if (comma >= 0) body.substring(comma + 1).trim() else ""
        val id = attribute(attributes, "tvg-id")
        return Pending(
            id = id,
            // Без коми назви немає — тоді беремо `tvg-name`, а далі id: картка без підпису
            // гірша за картку з технічною назвою.
            name = name.ifBlank { attribute(attributes, "tvg-name").ifBlank { id } },
            // iptv-org пише кілька груп через «;» («Animation;Kids;Religious»): вкладка — перша з них.
            group = attribute(attributes, "group-title").substringBefore(';').trim(),
            logo = attribute(attributes, "tvg-logo"),
        )
    }

    /**
     * Ключ, за яким два рядки — один канал.
     *
     * Це НЕ `matchKey`: той згортає «+» разом з іншою пунктуацією, і на справжньому плейлисті
     * iptv-org зливав «Inter» з «Inter+» та «Sonce» з «Sonce+» — а це різні канали. Тому «+»
     * перетворюємо на «plus», щоб він вижив: «1+1 HD» і «1+1» лишаються одним каналом,
     * «Inter» та «Inter+» — двома. Абетки згортає сам `normalizeChannelName`.
     */
    private fun dedupeKey(name: String): String = normalizeChannelName(name.replace("+", "plus"))

    /** Перша кома поза лапками, або -1. Атрибути M3U — це `key="value"`, і значення бувають з комами. */
    private fun String.separatorComma(): Int {
        var quoted = false
        forEachIndexed { index, char ->
            when {
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> return index
            }
        }
        return -1
    }

    /** Атрибут у лапках. Порожньо — атрибута немає або він порожній, для нас це те саме. */
    private fun attribute(source: String, name: String): String =
        Regex("$name=\"([^\"]*)\"").find(source)?.groupValues?.get(1)?.trim().orEmpty()

    private data class Pending(val id: String, val name: String, val group: String, val logo: String)

    private companion object {
        const val EXTINF = "#EXTINF:"
    }
}
