package com.richang.todo.ui

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.richang.todo.companion.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CompanionChatTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun close() { store.clear() }
    @Test fun sendConfirmationReplyAndStatisticsWorkThroughUi() {
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("companion.db")
        app.getSharedPreferences("companion_secure", Context.MODE_PRIVATE).edit().clear().commit()
        val db = CompanionDatabase(app)
        val key = KeyGenerator.getInstance("AES").generateKey()
        val vault = ModelVault(app) { key }
        vault.save(ModelConfig("https://example.invalid/v1", "test-model", "FAKE_TEST_TOKEN"))
        var calls = 0
        val model = CompanionViewModel(app, db, vault, ModelClient { FakeConnection(it).also { calls++ } })
        store.put("chat", model)
        compose.setContent { RichangTheme { CompanionScreen({}, model) } }
        compose.waitUntil(10_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); !model.state.value.loading }
        compose.onNodeWithTag("chat-input").performTextInput("你好，测试伴侣")
        compose.onNodeWithTag("chat-send").performClick()
        compose.onNodeWithText("确认聊天数据发送范围").assertExists()
        assertEquals(0, calls)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("chat-input").assertTextContains("你好，测试伴侣")
        assertTrue(db.messages().isEmpty())
        compose.onNodeWithTag("chat-send").performClick()
        compose.onNodeWithText("同意并发送").performClick()
        compose.waitUntil(10_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); !model.state.value.busy && model.state.value.messages.size == 2 }
        compose.onNodeWithText("测试回复").assertExists()
        assertEquals(1, calls)
        compose.onNodeWithContentDescription("伴侣菜单").performClick()
        compose.onNodeWithText("聊天统计").performClick()
        compose.onNodeWithText("成功消息 2 条 · AI 回复 1 条").assertExists()
        compose.onNodeWithText("服务商已返回用量：12 tokens").assertExists()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithContentDescription("伴侣菜单").performClick()
        compose.onNodeWithText("新话题").performClick()
        compose.waitUntil(10_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); !model.state.value.busy && model.state.value.sessions.size == 2 }
        compose.onNodeWithText("测试回复").assertDoesNotExist()
        compose.onNodeWithContentDescription("伴侣菜单").performClick()
        compose.onNodeWithText("话题记录").performClick()
        compose.onNodeWithText("你好，测试伴侣").performClick()
        compose.onNodeWithText("测试回复").assertExists()
    }
}
