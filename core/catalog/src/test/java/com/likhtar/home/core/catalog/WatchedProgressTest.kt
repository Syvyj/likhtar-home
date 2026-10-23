package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

/** Справжня форма відповіді сервера: серіал — рядком на серію, фільм — одним рядком. */
class WatchedProgressTest {

    @Test
    fun `серіал з незавершеною серією не переглянутий`() {
        val body = """
            {"results":[
              {"kind":"movie","tmdb_id":1002155,"finished":true},
              {"kind":"tv","tmdb_id":108978,"finished":true},
              {"kind":"tv","tmdb_id":108978,"finished":false},
              {"kind":"tv","tmdb_id":261145,"finished":true}
            ]}
        """.trimIndent()

        assertEquals(setOf("movie-1002155", "tv-261145"), parseFinished(body))
    }

    @Test
    fun `порожня і поламана відповідь дають порожній набір`() {
        assertEquals(emptySet<String>(), parseFinished("""{"results":null}"""))
        assertEquals(emptySet<String>(), parseFinished("{}"))
    }
}
