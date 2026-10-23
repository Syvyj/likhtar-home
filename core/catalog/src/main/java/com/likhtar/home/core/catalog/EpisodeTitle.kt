package com.likhtar.home.core.catalog

/**
 * Назва випуску без назви самого шоу.
 *
 * Телепортал віддає її повним рядком — «Україна має талант. 2026. Випуск 4 від
 * 13.09.2026», — і в списку, де назва шоу вже стоїть угорі, вона повторюється в
 * кожному рядку. Відрізаємо все до «Випуск», а коли такого слова немає — лишаємо
 * як є: вигадувати номер із нічого гірше за довгий рядок.
 *
 * Тут, у ядрі, бо підпис потрібен трьом місцям: сторінці шоу, «Продовжити» Реаліті й
 * ряду над плиткою на Головній(там ішов сирий рядок).
 */
fun showEpisodeTitle(raw: String, show: String): String {
    // Дата стоїть окремою плашкою — у назві «від 17.09.2026» лише повтор.
    val text = raw.replace(DATED, "").trim()
    for (word in listOf("Випуск", "Серія")) {
        val at = text.indexOf(word)
        if (at > 0) return text.substring(at).trim()
    }
    // «…Команда 1,2 серії українською» — дві серії одним відео.
    PAIRED.find(text)?.let { return "Серії " + it.groupValues[1] + "–" + it.groupValues[2] }
    // «Другий шанс на перше кохання 1 серія» — так підписують серії на YouTube.
    NUMBERED.find(text)?.let { return "Серія " + it.groupValues[1] }
    // «ПРЕМ'ЄРА! ЛЕГЕНДАРНЕ ШОУ … | 1 ВИПУСК» — шоу мовників на YouTube, капслоком.
    (ISSUE_AFTER.find(text) ?: ISSUE_BEFORE.find(text))?.let { return "Випуск " + it.groupValues[1] }
    return text.removePrefix(show).trimStart('.', ' ', '-', '—', '|').ifBlank { raw }
}

private val DATED = Regex("""\s*від\s+\d{1,2}\.\d{1,2}\.\d{4}""", RegexOption.IGNORE_CASE)
private val PAIRED = Regex("""(\d+)\s*[,\-–]\s*(\d+)\s*(?:серії|серии)""", RegexOption.IGNORE_CASE)
private val ISSUE_BEFORE = Regex("""(\d+)\s*(?:-?й\s+)?випуск""", RegexOption.IGNORE_CASE)
private val ISSUE_AFTER = Regex("""випуск\s*№?\s*(\d+)""", RegexOption.IGNORE_CASE)
private val NUMBERED = Regex("""(\d+)\s*(?:серія|серия)""", RegexOption.IGNORE_CASE)
