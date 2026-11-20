package com.likhtar.home.feature.home

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.likhtar.home.core.model.LauncherApp
import com.likhtar.home.core.model.LikhtarSection
import com.likhtar.home.core.smarthome.SmartDevice
import com.likhtar.home.core.smarthome.SmartDeviceKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModernHomeFocusTest {
    @get:Rule val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private val tiles = listOf(
        HomeTile(LikhtarSection.CINEMA, 0),
        HomeTile(LikhtarSection.LIVE_TV, 0),
        HomeTile(LikhtarSection.SMART_HOME, 0),
        HomeTile(LikhtarSection.APPS, 0),
    )

    @Test fun separateRowsNavigateAndOpenSection() {
        var selected: LikhtarSection? = null
        rule.setContent {
            ModernHomeContent(HomeUiState(tiles = tiles), { selected = it }, {}, {}, null, {})
        }
        assertFocusedText(text(R.string.home_all_apps))
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        assertFocusedText(text(LikhtarSection.CINEMA.titleRes))
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        rule.runOnIdle { assertEquals(LikhtarSection.CINEMA, selected) }
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        assertFocusedText(text(R.string.home_all_apps))
    }

    @Test fun verticalNavigationPreservesColumnBetweenRows() {
        val apps = listOf(
            LauncherApp("pkg.one", "Act1", "Netflix"),
            LauncherApp("pkg.two", "Act2", "YouTube"),
        )
        rule.setContent {
            ModernHomeContent(HomeUiState(tiles = tiles, pinnedApps = apps), {}, {}, {}, null, {})
        }
        assertFocusedText("NETFLIX")
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        assertFocusedText("YouTube")
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        assertFocusedText(text(LikhtarSection.LIVE_TV.titleRes))
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        assertFocusedText("YouTube")
    }

    @Test fun smartPanelBackRestoresWidgetFocus() {
        var toggled: String? = null
        val devices = listOf(
            SmartDevice("light.living", "Вітальня", SmartDeviceKind.LIGHT, "on", isOn = true),
        )
        rule.setContent {
            ModernHomeContent(
                HomeUiState(tiles = tiles, smartDevices = devices),
                {}, {}, {}, null, {},
                onToggleSmartDevice = { toggled = it.entityId },
            )
        }
        rule.onNodeWithText(text(R.string.home_my_home)).performClick()
        // Панель ставить фокус на першу річ, якою можна керувати; ↓ — «Усі пристрої».
        assertFocusedText("Вітальня")
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        assertFocusedText(text(R.string.home_all_devices))
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        assertFocusedText("Вітальня")
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        rule.runOnIdle { assertEquals("light.living", toggled) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertFocusedText(text(R.string.home_my_home))
    }

    @Test fun combinedRowStartsOnSectionWithoutApps() {
        rule.setContent {
            ModernHomeContent(HomeUiState(tiles = tiles, homeCombinedRows = true), {}, {}, {}, null, {})
        }
        assertFocusedText(text(LikhtarSection.CINEMA.titleRes))
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        assertFocusedText(text(LikhtarSection.LIVE_TV.titleRes))
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        assertFocusedText(text(R.string.home_all_apps))
    }

    @Test fun gearDropdownAndInlineMoveModesWork() {
        var combinedSet: Boolean? = null
        var sectionsFirstToggled = false
        var openedSection: LikhtarSection? = null
        val movedCalls = mutableListOf<Pair<String, Boolean>>()
        val apps = listOf(
            LauncherApp("pkg.one", "Act1", "Netflix"),
            LauncherApp("pkg.two", "Act2", "YouTube"),
        )
        rule.setContent {
            ModernHomeContent(
                HomeUiState(tiles = tiles, pinnedApps = apps, allHomeApps = apps),
                onSelectSection = { openedSection = it },
                onOpenWeather = {},
                onLaunch = {},
                update = null,
                onUpdate = {},
                onSetCombinedRows = { combinedSet = it },
                onToggleSectionsFirst = { sectionsFirstToggled = true },
                onMoveHomeApp = { id, left -> movedCalls.add(id to left) },
            )
        }
        // ← з першої картки веде на кнопку керування рядом «≡», яка відкриває спадне меню ряду
        assertFocusedText("NETFLIX")
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) }
        assertFocusedText("≡")
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        assertFocusedText(text(R.string.home_row_move))
        // Обираємо «Переставити ряд» -> вмикається режим переставляння ряду (↑↓), натискаємо ↓ і OK щоб відпустити
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        assertFocusedText("↑↓")
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        rule.runOnIdle { assertEquals(true, sectionsFirstToggled) }
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        assertFocusedText("≡")

        // Повертаємось праворуч на першу іконку і довгим натисканням відкриваємо спадне меню під іконкою
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        assertFocusedText("NETFLIX")
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter, 600L) }
        assertFocusedText(text(R.string.home_app_move))
        // Обираємо «Переставити» -> іконка закріплюється за ←/→, з'являються стрілки ‹ ›, OK відпускає
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        assertFocusedText("NETFLIX")
        rule.onNodeWithText("›").assertExists()
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) }
        rule.runOnIdle {
            assertEquals(listOf("pkg.one/Act1" to false, "pkg.one/Act1" to true), movedCalls)
        }
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        rule.onNodeWithText("›").assertDoesNotExist()

        // Клік на шестерню відкриває спадне меню, а в «Швидких налаштуваннях» — лише Layout
        rule.onNodeWithContentDescription(text(R.string.home_settings)).performClick()
        assertFocusedText(text(R.string.home_quick_settings))
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        assertFocusedText(text(R.string.home_layout_two_rows))
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        assertFocusedText(text(R.string.home_layout_one_row))
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        rule.runOnIdle { assertEquals(true, combinedSet) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNode(isFocused() and hasAnyDescendant(hasContentDescription(text(R.string.home_settings)))).assertExists()
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        assertFocusedText(text(R.string.home_quick_settings))
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        assertFocusedText(text(R.string.home_all_settings))
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
        rule.runOnIdle { assertEquals(LikhtarSection.SETTINGS, openedSection) }
    }

    private fun assertFocusedText(label: String) {
        rule.onNode(isFocused() and hasAnyDescendant(hasText(label))).assertExists()
    }
}
