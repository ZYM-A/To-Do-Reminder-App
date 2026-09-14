package com.richang.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.semantics.SemanticsProperties
import com.richang.todo.companion.ModelConfig
import com.richang.todo.companion.Persona
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CompanionEditorsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun personaEditedFieldsSaveAndUnsavedReturnNeedsConfirmation() {
        var saved: Persona? = null
        var dismissed = false
        compose.setContent { RichangTheme { PersonaEditor(Persona(), false, null, { dismissed=true }, { saved=it }) } }
        compose.onNodeWithTag("persona-name").performTextReplacement("测试角色")
        compose.onNodeWithTag("persona-nickname").performScrollTo().performTextInput("朋友")
        compose.onNodeWithTag("persona-style").performScrollTo().performTextReplacement("短句")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("放弃未保存的人设修改？").assertExists()
        assertFalse(dismissed)
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("保存人设").performClick()
        assertEquals("测试角色", saved!!.name)
        assertEquals("朋友", saved!!.nickname)
        assertEquals("短句", saved!!.style)
    }
    @Test fun invalidPersonaCannotSave() {
        var saved = false
        compose.setContent { RichangTheme { PersonaEditor(Persona(), false, null, {}, { saved=true }) } }
        compose.onNodeWithTag("persona-name").performTextClearance()
        compose.onNodeWithText("保存人设").performClick()
        assertFalse(saved)
        compose.onNodeWithText("名字需为 1—40 字").assertExists()
    }
    @Test fun savingConfigDoesNotFetchAndKeyFieldIsPassword() {
        var saved: ModelConfig? = null
        var fetched = 0
        compose.setContent { RichangTheme { ModelConfigEditor(null, false, null, {}, { saved=it }, {}, { fetched++; emptyList() }) } }
        compose.onNodeWithTag("model-base").performTextInput("https://example.invalid/v1")
        compose.onNodeWithTag("model-key").performScrollTo().performTextInput("FAKE_TEST_TOKEN")
        compose.onNodeWithTag("model-key").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithTag("model-id").performScrollTo().performTextInput("test-model")
        compose.onNodeWithText("保存配置").performClick()
        assertEquals("test-model", saved!!.model)
        assertEquals("FAKE_TEST_TOKEN", saved!!.apiKey)
        assertEquals(0, fetched)
    }
    @Test fun modelListFetchRequiresExplicitConfirmationAndPresetClearsKey() {
        var fetched = 0
        val config = ModelConfig("https://example.invalid/v1", "test-model", "FAKE_TEST_TOKEN")
        compose.setContent { RichangTheme { ModelConfigEditor(config, false, null, {}, {}, {}, { fetched++; listOf("other-model") }) } }
        compose.onNodeWithText("测试连接并获取模型").performScrollTo().performClick()
        compose.onNodeWithText("验证这个服务商？").assertExists()
        assertEquals(0, fetched)
        compose.onNodeWithText("取消").performClick()
        assertEquals(0, fetched)
        compose.onNodeWithText("测试连接并获取模型").performClick()
        compose.onNodeWithText("确认并测试").performClick()
        compose.waitUntil { fetched == 1 }
        compose.onNodeWithText("从列表选择模型（1）").performScrollTo().performClick()
        compose.onNodeWithText("other-model").performClick()
        compose.onNodeWithTag("model-id").assertTextContains("other-model")
        compose.onNodeWithText("DeepSeek").performScrollTo().performClick()
        compose.onNodeWithTag("model-key").assertTextEquals("API 密钥", "")
        compose.onNodeWithTag("model-id").assertTextEquals("模型 ID", "")
    }
    @Test fun unsavedSecretIsNotRestoredFromAndroidSavedState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { RichangTheme { ModelConfigEditor(null, false, null, {}, {}, {}, { emptyList() }) } }
        compose.onNodeWithTag("model-key").performScrollTo().performTextInput("FAKE_UNSAVED_TOKEN")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("model-key").assertTextEquals("API 密钥", "")
    }
}
