package com.likhtar.home.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** D2: знімок останньої відповіді на диску — перший кадр розділу без мережі. */
class DiskSnapshotsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `записане читається за тим самим ключем`() {
        val s = DiskSnapshots(tmp.root)
        s.write("rows:cinema:default", """{"rows":[1]}""")
        assertEquals("""{"rows":[1]}""", s.read("rows:cinema:default", maxAgeMs = 60_000))
    }

    @Test fun `інший ключ — нічого`() {
        val s = DiskSnapshots(tmp.root)
        s.write("rows:cinema:default", "x")
        assertNull(s.read("rows:reality:default", maxAgeMs = 60_000))
    }

    @Test fun `застарілий — нічого`() {
        var now = 1_000_000L
        val s = DiskSnapshots(tmp.root) { now }
        s.write("k", "x")
        now += 8 * 24 * 60 * 60 * 1000L
        assertNull(s.read("k", maxAgeMs = 7 * 24 * 60 * 60 * 1000L))
    }

    @Test fun `ключ із будь-якими символами — безпечне ім'я файлу`() {
        val s = DiskSnapshots(tmp.root)
        s.write("rows:cinema:a/b?c=..&d", "x")
        assertEquals("x", s.read("rows:cinema:a/b?c=..&d", maxAgeMs = 60_000))
        assertEquals(1, tmp.root.walkTopDown().count { it.isFile })
    }
}
