package com.likhtar.home.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** «Додивився» — з початку титрів, якщо вони раніше за 92 %. */
class FinishLineTest {
    private val hour = 3_600_000L

    @Test fun `without credits it is 92 percent`() = assertEquals((hour * 0.92).toLong(), finishLineMs(hour, null))

    @Test fun `long credits move the line earlier`() = assertEquals(50 * 60_000L, finishLineMs(hour, 50 * 60_000L))

    @Test fun `credits after 92 percent do not move it later`() = assertEquals((hour * 0.92).toLong(), finishLineMs(hour, 59 * 60_000L))

    @Test fun `credits before the middle are not credits`() = assertEquals((hour * 0.92).toLong(), finishLineMs(hour, 10 * 60_000L))

    @Test fun `theintrodb answer`() {
        assertEquals(3432825L, IntroDb.parseCreditsStart("""{"tmdb_id":1396,"intro":[{"start_ms":228671,"end_ms":246465}],"credits":[{"start_ms":3432825,"end_ms":null}]}"""))
        assertNull(IntroDb.parseCreditsStart("""{"tmdb_id":1,"credits":[]}"""))
        assertNull(IntroDb.parseCreditsStart("""{"tmdb_id":1,"credits":[{"start_ms":null,"end_ms":40000}]}"""))
    }
}
