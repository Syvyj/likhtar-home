package com.likhtar.home.feature.cinema

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.TitleKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Back зі сторінки тайтла ставить фокус на ту саму картку ряду (FocusMemory + CollectionRail).
 *
 * Сторінка добірки знищується разом із композицією, тож «та сама картка» — це ключ у
 * [FocusMemory] і запит фокуса після нової композиції. Якщо картка не позначена
 * `.remembered`, запит гине, і фокус лишається ніде — саме так виглядали скарги
 * «після Back фокуса не видно».
 */
@RunWith(AndroidJUnit4::class)
class CollectionRailFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val items = (1..5).map { CatalogItem(tmdbId = it, kind = TitleKind.MOVIE, title = "Тайтл $it", year = "2020") }

    @Test
    fun backReturnsToRememberedCard() {
        val remembered = items[2]
        val memory = FocusMemory(mutableStateOf("i:${remembered.id}"), FocusRequester())
        rule.setContent {
            CompositionLocalProvider(LocalFocusMemory provides memory) {
                CollectionRail(title = "Ряд", items = items, posters = true, onOpen = {}, onFocused = {}, onOpenAll = null)
            }
        }
        rule.runOnIdle { memory.requester.requestFocus() }
        assertFocusedCardIs(remembered.title)
    }

    @Test
    fun anchorLandsOnFirstCard() {
        val anchor = FocusRequester()
        rule.setContent {
            CollectionRail(title = "Ряд", items = items, posters = true, anchor = anchor, onOpen = {}, onFocused = {}, onOpenAll = null)
        }
        rule.runOnIdle { anchor.requestFocus() }
        assertFocusedCardIs(items[0].title)
    }

    /** Назва стоїть ПІД постером, не всередині, тож картку впізнаємо за лівим краєм підпису. */
    private fun assertFocusedCardIs(title: String) {
        val card = rule.onNode(isFocused()).getBoundsInRoot()
        val label = rule.onNodeWithText(title).getBoundsInRoot()
        assertEquals("сфокусована картка не під підписом «$title»", label.left.value, card.left.value, 1f)
    }
}
