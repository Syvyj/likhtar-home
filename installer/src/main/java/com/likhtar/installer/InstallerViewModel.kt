package com.likhtar.installer

import android.app.Application
import android.net.ConnectivityManager
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.likhtar.home.core.adb.LocalAdb
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Знайти телевізор у мережі (відкритий порт 5555) і поставити на нього Ліхтар:
 * з'єднання → «Дозволити налагодження?» на екрані → APK з останнього релізу → встановлення →
 * запуск. Домашнім екраном Ліхтар робиться вже сам, своїм майстром на телевізорі
 * (HomeTakeover, із запобіжниками) — звідси стоковий лаунчер не чіпаємо.
 */
class InstallerViewModel(app: Application) : AndroidViewModel(app) {

    enum class Phase { IDLE, CONNECTING, APPROVE, DOWNLOADING, INSTALLING, DONE, FAILED }

    data class State(
        val host: String = "",
        val found: List<String> = emptyList(),
        val scanning: Boolean = false,
        @StringRes val scanNote: Int? = null,
        val phase: Phase = Phase.IDLE,
        val progress: Float? = null,
        @StringRes val error: Int? = null,
        val errorDetail: String? = null,
    ) {
        val busy get() = phase in setOf(Phase.CONNECTING, Phase.APPROVE, Phase.DOWNLOADING, Phase.INSTALLING)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setHost(host: String) = _state.update { it.copy(host = host.trim()) }

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, scanNote = null, found = emptyList()) }
        viewModelScope.launch {
            val own = ownAddress()
            val found = if (own == null) emptyList() else withContext(Dispatchers.IO) {
                // Лише /24 навколо телефона — у домашніх мережах майже завжди так.
                val prefix = own.substringBeforeLast('.')
                (1..254).map { "$prefix.$it" }.filter { it != own }
                    .map { ip -> async { ip.takeIf { portOpen(it) } } }.awaitAll().filterNotNull()
            }
            _state.update {
                it.copy(
                    scanning = false,
                    found = found,
                    host = found.singleOrNull() ?: it.host,
                    scanNote = when {
                        own == null -> R.string.scan_no_wifi
                        found.isEmpty() -> R.string.scan_none
                        else -> null
                    },
                )
            }
        }
    }

    fun install() {
        val host = _state.value.host
        if (host.isBlank() || _state.value.busy) return
        _state.update { it.copy(phase = Phase.CONNECTING, progress = null, error = null, errorDetail = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val apk = File(app.cacheDir, "likhtar-home.apk")
            try {
                LocalAdb.connect(app, APPROVAL_MS, { phase(Phase.APPROVE) }, host).use { adb ->
                    phase(Phase.DOWNLOADING)
                    download(apk)
                    phase(Phase.INSTALLING)
                    val size = apk.length().toFloat()
                    val out = adb.install(apk) { sent -> _state.update { it.copy(progress = sent / size) } }
                    if ("Success" !in out) return@use fail(R.string.error_install, out.trim())
                    adb.shell("am start -a android.intent.action.MAIN -n $PACKAGE/$PACKAGE.MainActivity")
                    phase(Phase.DONE)
                }
            } catch (e: LocalAdb.Failure) {
                fail(
                    when (e.reason) {
                        LocalAdb.Reason.PORT_CLOSED, LocalAdb.Reason.DEBUGGING_OFF -> R.string.error_port
                        LocalAdb.Reason.NOT_APPROVED, LocalAdb.Reason.REJECTED -> R.string.error_approve
                        LocalAdb.Reason.WIRELESS_TLS -> R.string.error_tls
                        else -> R.string.error_connection
                    },
                )
            } catch (e: IOException) {
                fail(if (_state.value.phase == Phase.DOWNLOADING) R.string.error_download else R.string.error_connection, e.message)
            } finally {
                apk.delete()
            }
        }
    }

    private fun phase(p: Phase) = _state.update { it.copy(phase = p, progress = null) }

    private fun fail(@StringRes error: Int, detail: String? = null) =
        _state.update { it.copy(phase = Phase.FAILED, error = error, errorDetail = detail?.takeIf { d -> d.isNotBlank() }) }

    private fun download(to: File) {
        val connection = URL(APK_URL).openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val total = connection.contentLengthLong.toFloat()
        connection.getInputStream().use { input ->
            to.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    if (total > 0) _state.update { it.copy(progress = done / total) }
                }
            }
        }
    }

    /** IPv4 телефона в поточній мережі; `null` — не в Wi-Fi / без мережі. */
    private fun ownAddress(): String? {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        return cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    private fun portOpen(ip: String): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, 5555), SCAN_TIMEOUT_MS); true }
    } catch (e: IOException) {
        false
    }

    private companion object {
        const val PACKAGE = "com.likhtar.home"
        const val APK_URL = "https://github.com/Syvyj/likhtar-home/releases/latest/download/likhtar-home.apk"
        const val APPROVAL_MS = 60_000
        const val SCAN_TIMEOUT_MS = 500
    }
}
