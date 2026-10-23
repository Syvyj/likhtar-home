package com.likhtar.home.core.smarthome

import java.io.File

/** Тексти з values-uk/strings.xml без Context: тести перевіряють саме українські підписи. */
object UkTexts : SmartHomeTexts {
    private val strings = Regex("""<string name="(\w+)">(.*?)</string>""")
        .findAll(File("src/main/res/values-uk/strings.xml").readText())
        .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }

    override fun get(id: Int, vararg args: Any): String {
        val name = R.string::class.java.fields.first { it.getInt(null) == id }.name
        return strings.getValue(name).format(*args)
    }
}
