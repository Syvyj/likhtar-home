package com.likhtar.home.core.catalog.media

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.util.concurrent.TimeUnit

/** `smb://host/share/шлях` → частини; `\\host\share\шлях` теж приймаємо. */
data class SmbAddress(val host: String, val share: String, val path: String) {
    companion object {
        fun parse(raw: String): SmbAddress? {
            val s = raw.trim().removePrefix("smb://").removePrefix("SMB://").replace('\\', '/').trim('/')
            val parts = s.split('/').filter(String::isNotBlank)
            if (parts.size < 2) return null
            return SmbAddress(parts[0], parts[1], parts.drop(2).joinToString("/"))
        }
    }
}

/** Запис папки SMB; [path] — від кореня спільної папки, з «/». */
data class SmbEntry(val name: String, val path: String, val folder: Boolean, val size: Long)

/**
 * SMB2/3 через smbj: одне з'єднання на джерело, перепідключення на помилці.
 * Порожній користувач — гостьовий вхід (спільна папка без пароля).
 */
class SmbAccess(private val source: MediaSource) {
    private val address = SmbAddress.parse(source.address) ?: error("Не SMB-адреса: ${source.address}")
    private var client: SMBClient? = null
    private var share: DiskShare? = null

    @Synchronized
    private fun share(): DiskShare {
        share?.takeIf { it.isConnected }?.let { return it }
        close()
        // BouncyCastle явно: у JCE Android немає MD4, без якого NTLM не входить.
        val config = SmbConfig.builder().withSecurityProvider(BCSecurityProvider())
            .withTimeout(15, TimeUnit.SECONDS).withSoTimeout(30, TimeUnit.SECONDS).build()
        val c = SMBClient(config)
        val auth = if (source.user.isBlank()) {
            AuthenticationContext.guest()
        } else {
            val domain = source.user.substringBefore('\\', "")
            AuthenticationContext(source.user.substringAfter('\\'), source.password.toCharArray(), domain.ifBlank { null })
        }
        // «host:порт» — для NAS на нестандартному порту; інакше 445.
        val port = address.host.substringAfter(':', "").toIntOrNull() ?: 445
        val session = c.connect(address.host.substringBefore(':'), port).authenticate(auth)
        val s = session.connectShare(address.share) as DiskShare
        client = c
        share = s
        return s
    }

    /** Вміст папки [dir] (від кореня джерела); «.» і «..» пропускаємо. */
    fun list(dir: String): List<SmbEntry> = retry {
        val full = join(address.path, dir)
        share().list(full.replace('/', '\\')).mapNotNull { info ->
            val name = info.fileName
            if (name == "." || name == "..") return@mapNotNull null
            val folder = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            SmbEntry(name, join(dir, name), folder, info.endOfFile)
        }
    }

    /** Відкритий файл для читання шматками; закриває викликач. */
    fun open(path: String): File = retry {
        share().openFile(
            join(address.path, path).replace('/', '\\'),
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
    }

    @Synchronized
    fun close() {
        runCatching { share?.close() }
        runCatching { client?.close() }
        share = null
        client = null
    }

    private fun <T> retry(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        close()
        block()
    }

    private fun join(a: String, b: String) = listOf(a, b).filter(String::isNotBlank).joinToString("/") { it.trim('/') }
}
