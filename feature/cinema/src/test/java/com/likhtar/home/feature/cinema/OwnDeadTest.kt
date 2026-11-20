package com.likhtar.home.feature.cinema

import com.likhtar.home.core.catalog.OwnWarm
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnDeadTest {
    private val mbit = 1e6 / 8

    @Test fun `джерело, що розганяється, — не мертве`() {
        assertEquals(false, looksDead(OwnWarm(ready = false, bytesPerSec = 12 * mbit, connections = 9), "2160p · DV"))
    }

    @Test fun `4K на 0,3 Мбіт — мертве`() {
        assertEquals(true, looksDead(OwnWarm(ready = false, bytesPerSec = 0.3 * mbit, connections = 2), "2160p · DV"))
    }

    @Test fun `наповнилось — не мертве навіть на нулі`() {
        assertEquals(false, looksDead(OwnWarm(ready = true, bytesPerSec = 0.0), "2160p"))
    }

    @Test fun `1080p на 2 Мбіт — живе`() {
        assertEquals(false, looksDead(OwnWarm(ready = false, bytesPerSec = 2 * mbit), "1080p"))
    }
}
