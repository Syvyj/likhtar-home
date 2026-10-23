package com.likhtar.home.core.catalog.media

import org.junit.Assert.assertEquals
import org.junit.Test

class DlnaParseTest {
    @Test
    fun descriptionGivesContentDirectoryControl() {
        val xml = """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0"><device>
<deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType><friendlyName>NAS: minidlna</friendlyName>
<serviceList>
<service><serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType><controlURL>/ctl/ConnectionMgr</controlURL></service>
<service><serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType><controlURL>/ctl/ContentDir</controlURL></service>
</serviceList></device></root>"""
        val server = Dlna.parseDescription(xml, "http://192.168.0.10:8200/rootDesc.xml")
        assertEquals(DlnaServer("NAS: minidlna", "http://192.168.0.10:8200/rootDesc.xml", "http://192.168.0.10:8200/ctl/ContentDir"), server)
    }

    @Test
    fun didlFoldersAndFiles() {
        val didl = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
<container id="64$1" parentID="64" childCount="3"><dc:title>Серіали</dc:title><upnp:class>object.container.storageFolder</upnp:class></container>
<item id="64$2" parentID="64"><dc:title>Dune.2021.mkv</dc:title><upnp:class>object.item.videoItem</upnp:class>
<res size="4096" protocolInfo="http-get:*:video/x-matroska:*">http://192.168.0.10:8200/MediaItems/22.mkv</res></item>
</DIDL-Lite>"""
        assertEquals(
            listOf(
                DlnaEntry("64$1", "Серіали", folder = true),
                DlnaEntry("64$2", "Dune.2021.mkv", folder = false, url = "http://192.168.0.10:8200/MediaItems/22.mkv", size = 4096, mime = "video/x-matroska"),
            ),
            Dlna.parseDidl(didl),
        )
    }

    @Test
    fun fileUrlTakesServerHost() {
        assertEquals(
            "http://192.168.0.91:8200/MediaItems/24.mp4",
            Dlna.rehost("http://192.168.139.2:8200/MediaItems/24.mp4", "http://192.168.0.91:8200/ctl/ContentDir"),
        )
        assertEquals("http://nas:32469/a.mkv", Dlna.rehost("http://nas:32469/a.mkv", "http://nas:32469/ctl"))
    }
}
