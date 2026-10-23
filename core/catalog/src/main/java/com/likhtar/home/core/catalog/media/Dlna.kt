package com.likhtar.home.core.catalog.media

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.likhtar.home.core.data.http
import com.likhtar.home.core.data.text
import java.io.StringReader
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** DLNA-сервер у мережі: ім'я, [location] — опис пристрою, [control] — ContentDirectory. */
data class DlnaServer(val name: String, val location: String, val control: String)

/** Запис каталогу DLNA: папка або файл ([url] — пряме посилання, яке відкриє будь-який плеєр). */
data class DlnaEntry(
    val id: String,
    val title: String,
    val folder: Boolean,
    val url: String = "",
    val size: Long = 0,
    val mime: String = "",
)

/**
 * UPnP/DLNA без бібліотек: пошук — SSDP (M-SEARCH на 239.255.255.250:1900), опис —
 * XML пристрою, перегляд — SOAP `Browse` до ContentDirectory. Файли DLNA віддає по HTTP,
 * тож плеєр отримує посилання як є.
 */
object Dlna {
    private const val TAG = "LikhtarMedia"
    private const val CONTENT_DIRECTORY = "urn:schemas-upnp-org:service:ContentDirectory:1"

    /** Сервери, що відповіли за [timeoutMs]. Мультикаст на Wi-Fi вимагає MulticastLock. */
    suspend fun discover(context: Context, timeoutMs: Int = 3000): List<DlnaServer> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("likhtar-dlna")?.apply { setReferenceCounted(false); acquire() }
        val locations = linkedSetOf<String>()
        try {
            MulticastSocket().use { socket ->
                socket.soTimeout = 500
                val group = InetAddress.getByName("239.255.255.250")
                val query = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n"
                val bytes = query.toByteArray()
                repeat(2) { socket.send(DatagramPacket(bytes, bytes.size, group, 1900)) }
                val until = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(4096)
                while (System.currentTimeMillis() < until) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    headerOf(String(packet.data, 0, packet.length), "location")?.let(locations::add)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSDP не вдався", e)
        } finally {
            lock?.release()
        }
        locations.mapNotNull { describe(it) }.distinctBy { it.control }
    }

    /** Опис пристрою за [location]; `null` — не медіасервер або не відповів. */
    fun describe(location: String): DlnaServer? = runCatching {
        val xml = http(location, 4000, 4000) { it.text() }
        parseDescription(xml, location)
    }.onFailure { Log.w(TAG, "опис $location", it) }.getOrNull()

    internal fun parseDescription(xml: String, location: String): DlnaServer? {
        val doc = parse(xml)
        val base = doc.getElementsByTagName("URLBase").item(0)?.textContent?.trim().takeUnless { it.isNullOrEmpty() } ?: location
        val name = doc.getElementsByTagName("friendlyName").item(0)?.textContent?.trim().orEmpty()
        val services = doc.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val s = services.item(i) as Element
            if (s.child("serviceType")?.startsWith("urn:schemas-upnp-org:service:ContentDirectory") == true) {
                val control = s.child("controlURL") ?: continue
                return DlnaServer(name.ifBlank { URL(location).host }, location, URL(URL(base), control).toString())
            }
        }
        return null
    }

    /** Усе з папки [objectId] («0» — корінь), сторінками по 200. */
    fun browse(control: String, objectId: String): List<DlnaEntry> {
        val out = mutableListOf<DlnaEntry>()
        var start = 0
        while (true) {
            val body = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>
<u:Browse xmlns:u="$CONTENT_DIRECTORY"><ObjectID>${escape(objectId)}</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag>
<Filter>*</Filter><StartingIndex>$start</StartingIndex><RequestedCount>200</RequestedCount><SortCriteria></SortCriteria></u:Browse>
</s:Body></s:Envelope>"""
            val reply = http(
                control, 5000, 15000, "POST",
                mapOf("Content-Type" to "text/xml; charset=\"utf-8\"", "SOAPACTION" to "\"$CONTENT_DIRECTORY#Browse\""),
                body,
            ) { it.text() }
            val doc = parse(reply)
            val result = doc.getElementsByTagName("Result").item(0)?.textContent.orEmpty()
            val page = parseDidl(result).map { if (it.folder) it else it.copy(url = rehost(it.url, control)) }
            out += page
            val total = doc.getElementsByTagName("TotalMatches").item(0)?.textContent?.trim()?.toIntOrNull() ?: 0
            start += page.size
            if (page.isEmpty() || start >= total) return out
        }
    }

    /** DIDL-Lite: `container` — папка, `item` з `res` — файл. */
    internal fun parseDidl(didl: String): List<DlnaEntry> {
        if (didl.isBlank()) return emptyList()
        val root = parse(didl).documentElement
        val out = mutableListOf<DlnaEntry>()
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val title = e.child("dc:title") ?: e.child("title") ?: continue
            when (e.localName ?: e.tagName) {
                "container" -> out += DlnaEntry(e.getAttribute("id"), title, folder = true)
                "item" -> {
                    val res = e.getElementsByTagNameNS("*", "res").item(0) as? Element ?: (e.getElementsByTagName("res").item(0) as? Element) ?: continue
                    val proto = res.getAttribute("protocolInfo")
                    out += DlnaEntry(
                        id = e.getAttribute("id"),
                        title = title,
                        folder = false,
                        url = res.textContent.trim(),
                        size = res.getAttribute("size").toLongOrNull() ?: 0,
                        mime = proto.split(':').getOrNull(2).orEmpty(),
                    )
                }
            }
        }
        return out
    }

    /**
     * Сервер у Docker (Plex, Jellyfin, minidlna на NAS) підписує файли адресою контейнера —
     * 172.17.x чи 192.168.139.x, з мережі її не видно. Хост беремо той, за яким сервер щойно
     * відповів, порт і шлях — з посилання.
     */
    internal fun rehost(url: String, control: String): String = runCatching {
        val u = URL(url)
        val host = URL(control).host
        if (u.host == host) url else URL(u.protocol, host, u.port, u.file).toString()
    }.getOrDefault(url)

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(InputSource(StringReader(xml)))

    private fun Element.child(tag: String): String? {
        val local = tag.substringAfter(':')
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i) as? Element ?: continue
            if ((n.localName ?: n.tagName.substringAfter(':')) == local) return n.textContent.trim()
        }
        return null
    }

    private fun headerOf(reply: String, name: String): String? = reply.lineSequence()
        .firstOrNull { it.substringBefore(':').trim().equals(name, ignoreCase = true) }
        ?.substringAfter(':')?.trim()

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
