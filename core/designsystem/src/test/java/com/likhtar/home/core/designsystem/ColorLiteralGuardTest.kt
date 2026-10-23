package com.likhtar.home.core.designsystem

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Сторож палітри: `Color(0x…)` живе лише у файлах кольорів designsystem (`Color.kt`,
 * `*Colors.kt`). Екрани беруть кольори за іменем — інакше нічний перенос прототипів
 * знову розсипає ті самі значення по десятках файлів.
 */
class ColorLiteralGuardTest {
    @Test
    fun `кольори лише в палітрі`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val literal = Regex("""Color\(0x[0-9A-Fa-f]{6,8}\)""")
        val offenders = listOf("app", "core", "feature").flatMap { dir ->
            File(root, dir).walkTopDown()
                .filter { it.extension == "kt" && "/src/main/" in it.path }
                .filterNot { it.name == "Color.kt" || it.name.endsWith("Colors.kt") }
                .flatMap { file ->
                    file.readLines().withIndex()
                        .filter { (_, line) -> literal.containsMatchIn(line) }
                        .map { (i, _) -> "${file.relativeTo(root)}:${i + 1}" }
                }
                .toList()
        }
        assertTrue("Колір без імені — винеси в LikhtarColors чи *Colors.kt:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
