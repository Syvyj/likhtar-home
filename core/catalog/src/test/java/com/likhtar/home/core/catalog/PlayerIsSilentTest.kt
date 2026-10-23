package com.likhtar.home.core.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Форми відповідей узяті з живого `/api/torrent/reading` (заміряно 03.09.2026). */
class PlayerIsSilentTest {

    private val noReaders = ReadingCheck(reading = false, readers = 0, moved = 0)
    private val stalled = ReadingCheck(reading = false, readers = 1, moved = 0)
    private val alive = ReadingCheck(reading = true, readers = 1, moved = 1)

    @Test
    fun `нуль читачів — вердикт з першої перевірки`() {
        assertTrue(playerIsSilent(noReaders))
    }

    @Test
    fun `живий читач без руху сам по собі не вердикт`() {
        assertTrue(stalled.doubtful)
        assertFalse("без другої перевірки звинувачувати не можна", playerIsSilent(stalled))
        assertFalse("друга каже «читає» — плеєр справний", playerIsSilent(stalled, alive))
        assertTrue("двічі без руху — це вже біда", playerIsSilent(stalled, stalled))
    }

    @Test
    fun `недоступний сервер і прямий потік не звинувачують плеєр`() {
        // Дефолт ReadingCheck() — саме те, що клієнт бере при будь-якій невдачі,
        // і те, що сервер віддає для kind:"direct".
        assertFalse(playerIsSilent(ReadingCheck()))
        assertFalse(playerIsSilent(alive))
    }
}
