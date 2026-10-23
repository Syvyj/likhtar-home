package com.likhtar.home.core.wallpaper

import com.likhtar.home.core.model.BackdropSpec

/**
 * Одні шпалери.
 *
 * Або [imageUri] (віддалене чи локальне зображення), або [backdrop] — вбудовані шпалери
 * описуються процедурно, а не файлом: це нуль ваги в APK і робота без мережі.
 * [author] і [sourceUri] потрібні для атрибуції — деякі джерела вимагають її показувати.
 */
data class Wallpaper(
    val id: String,
    val title: String,
    val imageUri: String? = null,
    val backdrop: BackdropSpec? = null,
    val author: String? = null,
    val sourceUri: String? = null,
) {
    /**
     * Рядок атрибуції під шпалерами. Показуємо автора й хост джерела, а не повне посилання:
     * URL пошуку Bing — це майже два рядки службових параметрів, які нікому нічого не кажуть,
     * а вимогу вказати авторство закриває саме ім'я автора.
     */
    val attribution: String?
        get() {
            val host = sourceUri?.substringAfter("//")?.substringBefore('/')?.removePrefix("www.")
            return when {
                author.isNullOrBlank() -> null
                host.isNullOrBlank() -> author
                else -> "$author · $host"
            }
        }
}

/** Коли лаунчер має просити наступні шпалери. */
enum class WallpaperUpdateMode {
    /** Змінювати за таймером (інтервал задається в налаштуваннях). */
    TIME_ELAPSED,

    /** Один статичний варіант — оновлювати не потрібно. */
    STATIC,
}

/**
 * Джерело шпалер.
 *
 * Той самий контракт, що згодом закриють зовнішні плагіни через AIDL-сервіс (див. §6 плану):
 * лаунчер знає лише [id], [name], [updateMode] і вміє попросити список.
 */
interface WallpaperProvider {
    val id: String
    val name: String
    val updateMode: WallpaperUpdateMode

    /** Порожній список означає «джерело нічого не дало» — це не помилка. */
    suspend fun wallpapers(): List<Wallpaper>
}
