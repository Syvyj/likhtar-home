import java.util.Properties
import org.gradle.api.Project

/** Спільні константи SDK для всіх модулів. */
object LikhtarSdk {
    const val COMPILE = 37
    const val MIN = 24
}

/**
 * Локальні ключі з кореневого `.env` (не потрапляє в Git). Явний Gradle property
 * (-P…) має пріоритет для CI — тому спершу пробуємо findProperty.
 */
fun Project.likhtarEnv(vararg keys: String): String {
    val env = Properties().apply {
        rootProject.file(".env").takeIf { it.isFile }?.inputStream()?.use(::load)
    }
    return keys.firstNotNullOfOrNull { key ->
        (findProperty(key) as String?) ?: env.getProperty(key)
    }.orEmpty()
}
