package com.richang.todo.ui

import android.content.Context
import android.os.Looper
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.richang.todo.companion.CompanionViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CompanionKeyboardTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun close() { store.clear() }

    @Test fun keyboardSpaceIsAppliedOnceAndToolbarStaysBelowStatusBar() {
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("companion.db")
        app.getSharedPreferences("companion_secure", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = CompanionViewModel(app)
        store.put("chat", vm)
        var keyboard by mutableStateOf(0.dp)
        compose.setContent {
            RichangTheme {
                CompanionScreen({}, vm, WindowInsets(top = 24.dp, bottom = if (keyboard > 0.dp) keyboard else 24.dp))
            }
        }
        compose.waitUntil(10_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); !vm.state.value.loading }
        val root = compose.onNodeWithTag("companion-window").getUnclippedBoundsInRoot()
        val resting = compose.onNodeWithTag("chat-input").getUnclippedBoundsInRoot()
        val toolbar = compose.onNodeWithTag("companion-back").getUnclippedBoundsInRoot()
        for (height in listOf(260.dp, 340.dp, 0.dp)) {
            compose.runOnIdle { keyboard = height }
            compose.onNodeWithTag("chat-input").performClick()
            compose.waitForIdle()
            val input = compose.onNodeWithTag("chat-input").getUnclippedBoundsInRoot()
            val header = compose.onNodeWithTag("companion-back").getUnclippedBoundsInRoot()
            val bottomInset = if (height > 0.dp) height else 24.dp
            assertEquals(toolbar.top.value, header.top.value, 1f)
            assertTrue(header.top >= root.top + 24.dp)
            assertEquals(resting.top.value - (bottomInset - 24.dp).value, input.top.value, 1f)
            val gap = root.bottom - bottomInset - input.bottom
            assertTrue("Unexpected gap above keyboard: $gap", gap >= 0.dp && gap < 60.dp)
            compose.onNodeWithTag("companion-back").assertIsDisplayed()
            compose.onNodeWithTag("chat-input").assertIsDisplayed()
        }
    }

    @Test fun fullScreenDialogUsesResizeOnAndroid9() {
        var window: Window? = null
        compose.setContent {
            RichangTheme {
                CompanionDialog({}, insets = WindowInsets(top = 24.dp, bottom = 280.dp)) {
                    val view = LocalView.current
                    SideEffect { window = (view.parent as DialogWindowProvider).window }
                }
            }
        }
        compose.runOnIdle {
            assertNotNull(window)
            assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                window!!.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
        }
    }
}
