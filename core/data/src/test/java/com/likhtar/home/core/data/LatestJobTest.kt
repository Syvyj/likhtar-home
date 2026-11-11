package com.likhtar.home.core.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** «Почати»: запізніла відповідь пошуку по 3-й серії не лягає у вікно 5-ї. */
class LatestJobTest {
    @Test fun `new job cancels the previous before its late answer lands`() = runTest {
        val shown = mutableListOf<String>()
        val jobs = LatestJob(this)
        jobs.launch { delay(45_000); shown += "S01E03" } // живий пошук до 45 с
        jobs.launch { delay(1_000); shown += "S01E05" }
        advanceUntilIdle()
        assertEquals(listOf("S01E05"), shown)
    }

    @Test fun `cancel stops the running job (Back, closed dialog)`() = runTest {
        val shown = mutableListOf<String>()
        val jobs = LatestJob(this)
        jobs.launch { delay(5_000); shown += "reopened" }
        jobs.cancel()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), shown)
    }
}
