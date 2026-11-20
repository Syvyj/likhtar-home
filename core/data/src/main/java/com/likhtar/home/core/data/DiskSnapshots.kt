package com.likhtar.home.core.data

import java.io.File
import java.security.MessageDigest

/**
 * D2 (lounge): остання добра відповідь розділу на диску — холодний старт малює з неї перший кадр
 * за долю секунди, а свіжа підміняє її, щойно приїде. Ключ — те, від чого залежить відповідь
 * (розділ, профіль, встановлені застосунки); ім'я файлу — його хеш. Запис атомарний: тимчасовий
 * файл і перейменування, щоб обірваний запис не лишив половину JSON.
 */
class DiskSnapshots(private val dir: File, private val now: () -> Long = System::currentTimeMillis) {
    fun write(key: String, body: String) {
        runCatching {
            dir.mkdirs()
            val target = file(key)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(body)
            tmp.setLastModified(now())
            tmp.renameTo(target)
        }
    }

    /** `null` — немає, старше за [maxAgeMs] чи не читається. */
    fun read(key: String, maxAgeMs: Long): String? = runCatching {
        file(key).takeIf { it.isFile && now() - it.lastModified() <= maxAgeMs }?.readText()
    }.getOrNull()

    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }
}
