package com.likhtar.home.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Список служб доступності після оновлення лаунчера: дописуємо свою, чужих не губимо. */
class KeyServiceRestoreTest {
    private val ours = "com.likhtar.home.personal/com.likhtar.home.HomeKeyService"

    @Test fun `порожньо — лише наша`() = assertEquals(ours, withService(null, ours))

    @Test fun `чужі лишаються, наша — в кінець`() = assertEquals("a.b/.X:$ours", withService("a.b/.X", ours))

    @Test fun `уже є — нічого не міняємо`() = assertNull(withService("a.b/.X:$ours", ours))

    @Test fun `рядок null від settings — як порожньо`() = assertEquals(ours, withService("null", ours))

    @Test fun `порожні шматки й пробіли відкидаємо`() = assertEquals("a.b/.X:$ours", withService(" a.b/.X :: \n", ours))

    @Test fun `коротка форма нашої служби — теж наша`() =
        assertNull(withService("com.likhtar.home.personal/.HomeKeyService", "com.likhtar.home.personal/com.likhtar.home.personal.HomeKeyService"))
}
