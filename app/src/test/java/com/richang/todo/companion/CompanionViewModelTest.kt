package com.richang.todo.companion

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import javax.crypto.KeyGenerator
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class CompanionViewModelTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var db: CompanionDatabase
    private lateinit var vault: ModelVault
    private lateinit var vm: CompanionViewModel
    private val requests = mutableListOf<FakeConnection>()
    private var status = 200
    private val store = ViewModelStore()
    @Before fun setup() {
        app.deleteDatabase("companion.db")
        app.getSharedPreferences("companion_secure", Context.MODE_PRIVATE).edit().clear().commit()
        db = CompanionDatabase(app)
        val key = KeyGenerator.getInstance("AES").generateKey()
        vault = ModelVault(app) { key }
        vault.save(ModelConfig("https://example.invalid/v1", "test-model", "FAKE_TEST_TOKEN"))
        vm = CompanionViewModel(app, db, vault, ModelClient { FakeConnection(it, status).also { c -> synchronized(requests) { requests.add(c) } } })
        store.put("vm", vm)
        settle()
    }
    private fun settle() {
        val deadline = System.currentTimeMillis() + 10_000
        do {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (!vm.state.value.loading && !vm.state.value.busy) return
            Thread.sleep(10)
        } while (System.currentTimeMillis() < deadline)
        fail("ViewModel did not become idle")
    }
    @After fun cleanup() { store.clear(); db.close() }
    @Test fun noTransmissionOrDatabaseWriteUntilConsent() {
        assertTrue(requests.isEmpty())
        vm.updateDraft("hello")
        vm.send("hello")
        assertEquals("hello", vm.state.value.consentText)
        assertEquals("hello", vm.state.value.draft)
        assertTrue(db.messages().isEmpty())
        assertTrue(requests.isEmpty())
        vm.dismissConsent()
        assertNull(vm.state.value.consentText)
        assertTrue(requests.isEmpty())
        vm.send("hello"); vm.send("hello", consent=true); settle()
        assertEquals(1, requests.size)
        assertEquals(2, db.messages().size)
        assertEquals("", vm.state.value.draft)
    }
    @Test fun sameTopicKeepsContextButOtherTopicsAreExcludedAndReconfirmed() {
        vm.send("first-topic-secret", consent=true); settle()
        vm.send("followup"); settle()
        assertEquals(2, requests.size)
        assertTrue(requests.last().output.toString("UTF-8").contains("first-topic-secret"))
        vm.newSession(); settle()
        vm.send("new-topic")
        assertNotNull(vm.state.value.consentText)
        assertEquals(2, requests.size)
        vm.send("new-topic", consent=true); settle()
        val payload = JSONObject(requests.last().output.toString("UTF-8"))
        assertEquals(2, payload.getJSONArray("messages").length())
        assertFalse(payload.toString().contains("first-topic-secret"))
    }
    @Test fun changingPersonaOrProviderRevokesSendingConsent() {
        vm.send("hello", consent=true); settle()
        vm.savePersona(Persona(name="新角色")) {}; settle()
        vm.send("after-persona")
        assertNotNull(vm.state.value.consentText)
        assertEquals(1, requests.size)
        vm.send("after-persona", consent=true); settle()
        vm.saveConfig(ModelConfig("https://other.invalid", "other-model", "FAKE_OTHER_TOKEN")) {}; settle()
        assertEquals(2, requests.size)
        vm.send("after-provider")
        assertNotNull(vm.state.value.consentText)
        assertEquals(2, requests.size)
        vm.send("after-provider", consent=true); settle()
        assertEquals("other.invalid", requests.last().url.host)
    }
    @Test fun failedSendNeedsManualRetryAndDoesNotDuplicateUserMessage() {
        status = 429
        vm.send("retry-me", consent=true); settle()
        assertEquals(1, requests.size)
        val failed = db.messages().single()
        assertEquals("error", failed.status)
        assertEquals(0, ChatStats.from(db.messages()).messages)
        status = 200
        vm.send(failed.content, failed.id); settle()
        assertEquals(2, requests.size)
        assertEquals(2, db.messages().size)
        assertEquals(failed.id, db.messages().first().id)
        assertEquals("sent", db.messages().first().status)
    }
    @Test fun draftsStayWithTheirTopicAndDeleteOnlyRemovesThatTopic() {
        val first = vm.state.value.sessionId
        vm.updateDraft("unsent-one")
        vm.newSession(); settle()
        val second = vm.state.value.sessionId
        assertEquals("", vm.state.value.draft)
        vm.updateDraft("unsent-two")
        vm.selectSession(first)
        assertEquals("unsent-one", vm.state.value.draft)
        vm.selectSession(second)
        assertEquals("unsent-two", vm.state.value.draft)
        vm.deleteSession(second) {}; settle()
        assertEquals(first, vm.state.value.sessionId)
        assertEquals("unsent-one", vm.state.value.draft)
        vm.deleteSession(first) {}; settle()
        assertEquals(1, db.sessions().size)
        assertNotEquals(first, vm.state.value.sessionId)
        assertEquals("", vm.state.value.draft)
    }
}
