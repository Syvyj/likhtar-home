package com.likhtar.home.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Панель сповіщень (lounge `NotificationFeed`): що з системних сповіщень показувати в Ліхтарі. */
class NotificationFeedTest {
    private val update = NotificationFacts(
        pkg = "com.android.vending", ongoing = false, media = false, groupSummary = false,
        importance = IMPORTANCE_DEFAULT, title = "Оновлення встановлено", text = "YouTube", clearable = true,
    )
    private fun show(n: NotificationFacts, muted: Set<String> = emptySet()) = showInFeed(n, ownPkg = "com.likhtar.home.personal", muted = muted)

    @Test fun `звичайне — так`() = assertTrue(show(update))

    @Test fun `постійні, медіа й групові підсумки — ні`() {
        assertFalse(show(update.copy(ongoing = true)))
        assertFalse(show(update.copy(media = true)))
        assertFalse(show(update.copy(groupSummary = true)))
    }

    @Test fun `важливість нижче LOW — ні, LOW — так`() {
        assertFalse(show(update.copy(importance = IMPORTANCE_LOW - 1)))
        assertTrue(show(update.copy(importance = IMPORTANCE_LOW)))
    }

    @Test fun `свої й сховані застосунки — ні`() {
        assertFalse(show(update.copy(pkg = "com.likhtar.home.personal")))
        assertFalse(show(update, muted = setOf("com.android.vending")))
    }

    @Test fun `те, що не прибрати («Cast Receiver · Ready to Cast»), — стан, а не новина`() =
        assertFalse(show(update.copy(clearable = false)))

    @Test fun `без тексту й назви — ні`() = assertFalse(show(update.copy(title = " ", text = "")))
}
