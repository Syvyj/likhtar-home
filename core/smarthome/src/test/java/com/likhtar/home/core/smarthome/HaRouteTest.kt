package com.likhtar.home.core.smarthome

import org.junit.Assert.assertEquals
import org.junit.Test

/** Маршрути сторінки входу в HA: без чинного nonce відкрита лише сторінка «хто ми». */
class HaRouteTest {
    @Test
    fun `з nonce — форма, перехід і повернення`() {
        assertEquals(HaRoute.FORM, haRoute("/ab12/", "ab12"))
        assertEquals(HaRoute.GO, haRoute("/ab12/go", "ab12"))
        assertEquals(HaRoute.CALLBACK, haRoute("/cb", "ab12"))
        assertEquals(HaRoute.NOT_FOUND, haRoute("/zz99/go", "ab12"))
    }

    @Test
    fun `після зв'язування nonce порожній — подвійний слеш і повернення не працюють`() {
        assertEquals(HaRoute.NOT_FOUND, haRoute("//", ""))
        assertEquals(HaRoute.NOT_FOUND, haRoute("//go", ""))
        assertEquals(HaRoute.NOT_FOUND, haRoute("/cb", ""))
        assertEquals(HaRoute.ABOUT, haRoute("/", ""))
    }
}
