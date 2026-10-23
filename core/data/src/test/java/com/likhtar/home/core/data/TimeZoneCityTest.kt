package com.likhtar.home.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeZoneCityTest {
    @Test
    fun cityFromZone() {
        assertEquals("Warsaw", timeZoneCity("Europe/Warsaw"))
        assertEquals("New York", timeZoneCity("America/New_York"))
        assertEquals("Buenos Aires", timeZoneCity("America/Argentina/Buenos_Aires"))
        assertNull(timeZoneCity("Etc/UTC"))
        assertNull(timeZoneCity("UTC"))
    }
}
