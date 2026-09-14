package com.richang.todo.companion

import android.content.Context
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import javax.crypto.KeyGenerator
import org.json.JSONObject

internal class FakeConnection(url: URL, private val code: Int = 200, private val body: String =
    """{"choices":[{"message":{"content":"测试回复"},"finish_reason":"stop"}],"usage":{"total_tokens":12}}"""
) : HttpURLConnection(url) {
    val output = ByteArrayOutputStream()
    var disconnected = false
    var bodyReads = 0
    override fun connect() = Unit
    override fun disconnect() { disconnected = true }
    override fun usingProxy() = false
    override fun getOutputStream() = output
    override fun getResponseCode() = code
    override fun getInputStream() = body.byteInputStream().also { bodyReads++ }
    override fun getErrorStream() = error("Provider error bodies must not be read")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CompanionCoreTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val config get() = ModelConfig("https://example.invalid/v1", "test-model", "FAKE_TEST_TOKEN")
    @Before fun clear() {
        app.deleteDatabase("companion.db")
        app.getSharedPreferences("companion_secure", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun configRejectsUnsafeDestinationsAndHeaderInjection() {
        listOf("http://example.invalid", "https://user:pass@example.invalid", "https://example.invalid?key=x",
            "https://example.invalid#x", "https://example.invalid:70000", "https://example.invalid/v1/models",
            "https://example.invalid/v1/chat/completions").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { ModelConfig(url, "model", "FAKE").validated() }
        }
        assertThrows(IllegalArgumentException::class.java) { ModelConfig(config.baseUrl, "model", "FAKE\r\nx:y").validated() }
        assertThrows(IllegalArgumentException::class.java) { ModelConfig(config.baseUrl, "", "FAKE").validated() }
        assertEquals("", ModelConfig(config.baseUrl, "", "FAKE").validated(false).model)
        assertFalse(config.toString().contains(config.apiKey))
        assertFalse(config.toString().contains(config.baseUrl))
    }
    @Test fun personaValidationAndPromptPreserveUserChoices() {
        val p = Persona("  小伴  ", "朋友", "温柔", "简洁").validated()
        assertEquals("小伴", p.name)
        assertTrue(p.prompt().contains("朋友"))
        assertTrue(p.prompt().contains("AI"))
        assertThrows(IllegalArgumentException::class.java) { p.copy(name = " ").validated() }
        assertThrows(IllegalArgumentException::class.java) { p.copy(personality = "a".repeat(4001)).validated() }
    }
    @Test fun payloadOnlyIncludesSuccessfulRecentContextAndNoCredentials() {
        val history = (0 until 30).map { ChatMessage(sessionId="topic", role=if (it%2==0) "user" else "assistant", content="message-$it") } +
            ChatMessage(sessionId="topic", role="user", content="failed-secret", status="error")
        val payload = ModelClient.payload(config, Persona(), history, "current")
        val messages = payload.getJSONArray("messages")
        assertEquals(22, messages.length())
        assertEquals("message-10", messages.getJSONObject(1).getString("content"))
        assertEquals("current", messages.getJSONObject(21).getString("content"))
        assertFalse(payload.toString().contains("failed-secret"))
        assertFalse(payload.toString().contains(config.apiKey))
        assertFalse(payload.getBoolean("stream"))
    }
    @Test fun payloadRespectsCharacterLimitAndDropsOrphanAssistant() {
        val history = (0..9).map { ChatMessage(sessionId="t", role=if (it%2==0) "user" else "assistant", content="x".repeat(5000)) }
        val messages = ModelClient.payload(config, Persona(), history, "current").getJSONArray("messages")
        assertEquals(6, messages.length())
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        val orphan = ModelClient.payload(config, Persona(), listOf(history.last()), "hello").getJSONArray("messages")
        assertEquals(2, orphan.length())
    }
    @Test fun chatUsesNormalizedHttpsEndpointAndExpectedProtocol() {
        lateinit var request: FakeConnection
        val client = ModelClient { FakeConnection(it).also { c -> request = c } }
        val reply = client.chat(ModelConfig(config.baseUrl + "/", config.model, config.apiKey), Persona(), emptyList(), "hello")
        assertEquals("https://example.invalid/v1/chat/completions", request.url.toString())
        assertEquals("POST", request.requestMethod)
        assertEquals("Bearer FAKE_TEST_TOKEN", request.getRequestProperty("Authorization"))
        assertFalse(request.instanceFollowRedirects)
        assertEquals(12L, reply.tokens)
        assertEquals("测试回复", reply.content)
        assertTrue(request.disconnected)
    }
    @Test fun redirectsAndErrorsNeverExposeProviderBodiesOrRetry() {
        for (code in listOf(302, 401, 402, 429, 503)) {
            var calls = 0
            lateinit var c: FakeConnection
            val client = ModelClient { calls++; FakeConnection(it, code, "SECRET_PROVIDER_ECHO").also { c = it } }
            val error = assertThrows(IllegalStateException::class.java) { client.chat(config, Persona(), emptyList(), "test") }
            assertFalse(error.message!!.contains("SECRET_PROVIDER_ECHO"))
            assertEquals(1, calls)
            assertEquals(0, c.bodyReads)
            assertTrue(c.disconnected)
        }
    }
    @Test fun invalidAndOversizedResponsesFailClosed() {
        for (body in listOf("""{"choices":[{"message":{"content":null}}]}""",
            """{"choices":[{"message":{"content":""}}]}""", "x".repeat(1_048_577))) {
            val client = ModelClient { FakeConnection(it, 200, body) }
            assertThrows(IllegalStateException::class.java) { client.chat(config, Persona(), emptyList(), "test") }
        }
    }
    @Test fun truncatedReplyAndUnknownTokenUsageAreExplicit() {
        val client = ModelClient { FakeConnection(it, 200, """{"choices":[{"message":{"content":"partial"},"finish_reason":"length"}]}""") }
        val reply = client.chat(config, Persona(), emptyList(), "test")
        assertTrue(reply.truncated)
        assertNull(reply.tokens)
    }
    @Test fun modelsRequestHasNoPersonaOrChatBody() {
        lateinit var c: FakeConnection
        val client = ModelClient { FakeConnection(it, 200, """{"data":[{"id":"b"},{"id":"a"},{"id":"a"}]}""").also { f -> c=f } }
        assertEquals(listOf("a","b"), client.models(config))
        assertEquals("GET", c.requestMethod)
        assertEquals("/v1/models", c.url.path)
        assertEquals(0, c.output.size())
    }
    @Test fun vaultEncryptsEntireConfigWithFreshIvAndCanReopen() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val vault = ModelVault(app) { key }
        vault.save(config)
        val prefs = app.getSharedPreferences("companion_secure", Context.MODE_PRIVATE)
        val first = prefs.getString("encrypted_config", null)!!
        assertFalse(first.contains(config.apiKey))
        assertFalse(first.contains(config.baseUrl))
        assertFalse(first.contains(config.model))
        assertEquals(setOf("encrypted_config"), prefs.all.keys)
        val loaded = ModelVault(app) { key }.load()!!
        assertEquals(config.apiKey, loaded.apiKey)
        assertEquals(config.baseUrl, loaded.baseUrl)
        assertEquals(config.model, loaded.model)
        vault.save(config)
        assertNotEquals(first, prefs.getString("encrypted_config", null))
        vault.clear()
        assertNull(vault.load())
    }
    @Test fun vaultTamperAndWrongKeyFailWithoutLeakingConfig() {
        val key = KeyGenerator.getInstance("AES").generateKey()
        ModelVault(app) { key }.save(config)
        val wrong = ModelVault(app) { KeyGenerator.getInstance("AES").generateKey() }
        assertEquals("模型配置无法解密，请重新填写配置",
            assertThrows(IllegalStateException::class.java) { wrong.load() }.message)
        app.getSharedPreferences("companion_secure", Context.MODE_PRIVATE).edit().putString("encrypted_config", "broken").commit()
        assertThrows(IllegalStateException::class.java) { ModelVault(app) { key }.load() }
    }
    @Test fun databasePersistsPersonaAndCascadesOnlySelectedTopic() {
        var db = CompanionDatabase(app)
        val p = Persona("测试角色", "测试称呼", "安静", "短句")
        db.savePersona(p)
        db.createSession(ChatSession("one")); db.createSession(ChatSession("two"))
        db.insert(ChatMessage(sessionId="one", role="user", content="first"))
        db.insert(ChatMessage(sessionId="two", role="user", content="second"))
        db.close()
        db = CompanionDatabase(app)
        assertEquals(p, db.persona())
        assertEquals(2, db.messages().size)
        db.deleteSession("one")
        assertEquals("second", db.messages().single().content)
        assertEquals("two", db.sessions().single().id)
        assertEquals(p, db.persona())
        db.close()
    }
    @Test fun pendingRecoveryAndRetryOrderAreDurable() {
        val db = CompanionDatabase(app)
        db.createSession(ChatSession("t"))
        val user = ChatMessage(sessionId="t", role="user", content="retry", createdAt=10)
        db.pending(user); db.recoverInterrupted()
        assertEquals("error", db.messages().single().status)
        db.insert(ChatMessage(sessionId="t", role="user", content="later", createdAt=20))
        db.pending(user.copy(createdAt=30))
        assertEquals(user.id, db.messages().last().id)
        db.complete(user, ChatReply("done", 6), "test-model")
        assertEquals(3, db.messages().size)
        assertTrue(db.messages().all { it.status=="sent" })
        assertEquals("test-model", db.messages().last().model)
        db.close()
    }
    @Test fun failedAssistantInsertRollsBackSuccessfulUserMark() {
        val db = CompanionDatabase(app)
        db.createSession(ChatSession("t"))
        val user = ChatMessage(sessionId="t", role="user", content="test")
        db.pending(user)
        assertThrows(IllegalArgumentException::class.java) { db.complete(user, ChatReply("", null), "test-model") }
        assertEquals("pending", db.messages().single().status)
        db.close()
    }
    @Test fun statsCountOnlyRetainedSuccessfulMessagesAndKnownUsage() {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.of(2026, 1, 5)
        fun at(day: Int) = today.minusDays(day.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val messages = listOf(
            ChatMessage(sessionId="t", role="user", content="a", createdAt=at(2)),
            ChatMessage(sessionId="t", role="assistant", content="b", createdAt=at(2), tokens=12),
            ChatMessage(sessionId="t", role="user", content="c", createdAt=at(0)),
            ChatMessage(sessionId="t", role="assistant", content="d", createdAt=at(0)),
            ChatMessage(sessionId="t", role="user", content="e", createdAt=at(4), status="error"))
        assertEquals(ChatStats(4, 2, 2, 3, 12, 1), ChatStats.from(messages, today, zone))
        assertEquals(ChatStats(0,0,0,0,0,0), ChatStats.from(emptyList(), today, zone))
    }
}
