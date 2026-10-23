package com.likhtar.home.core.designsystem

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Три правила кожного ряду лаунчера ([LikhtarRail]) — пультом, як людина:
 *
 * 1. ↓ у ряд — на першу картку, хоч би яка ближча була геометрично;
 * 2. → з останньої картки — фокус лишається на ній (кидало на верхнє меню);
 * 3. ← з першої — теж лишається.
 *
 * Над рядом — кнопка «меню» навмисно праворуч: саме туди втікав фокус до правки.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
class LikhtarRailFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val menu = FocusRequester()

    @Composable
    private fun Screen(cards: Int) {
        Column {
            // «Меню» праворуч угорі — та сама пастка, що верхнє меню лаунчера.
            androidx.compose.foundation.layout.Box(
                Modifier.padding(start = 600.dp).size(80.dp).testTag("menu").focusRequester(menu).focusable(),
            )
            LikhtarRail {
                items((0 until cards).toList()) { i ->
                    androidx.compose.foundation.layout.Box(Modifier.size(80.dp).testTag("card$i").focusable())
                }
            }
        }
    }

    @Test
    fun downEntersOnFirstCard() {
        rule.setContent { Screen(cards = 6) }
        rule.runOnIdle { menu.requestFocus() }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("card0").assertIsFocused()
    }

    @Test
    fun rightOnLastCardStaysInRow() {
        rule.setContent { Screen(cards = 1) }
        rule.runOnIdle { menu.requestFocus() }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("card0").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag("card0").assertIsFocused()
    }

    @Test
    fun leftOnFirstCardStaysInRow() {
        rule.setContent { Screen(cards = 3) }
        rule.runOnIdle { menu.requestFocus() }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        rule.onNodeWithTag("card0").assertIsFocused()
    }
}
