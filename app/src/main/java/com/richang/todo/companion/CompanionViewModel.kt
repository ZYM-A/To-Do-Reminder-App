package com.richang.todo.companion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CompanionState(
    val loading: Boolean = true, val busy: Boolean = false, val persona: Persona = Persona(),
    val config: ModelConfig? = null, val sessions: List<ChatSession> = emptyList(), val messages: List<ChatMessage> = emptyList(),
    val sessionId: String = "", val draft: String = "", val error: String? = null, val consentText: String? = null, val consentRetryId: String? = null,
)
class CompanionViewModel @JvmOverloads constructor(
    application: Application,
    private val db: CompanionDatabase = CompanionDatabase(application),
    private val vault: ModelVault = ModelVault(application),
    private val client: ModelClient = ModelClient(),
) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(CompanionState())
    val state = mutable.asStateFlow()
    private var approved: String? = null
    private val drafts = mutableMapOf<String, String>()
    fun updateDraft(text: String) {
        if (text.length <= 4000) { drafts[mutable.value.sessionId] = text; mutable.value = mutable.value.copy(draft = text) }
    }
    init {
        // Close only after all child IO has finished or been cancelled.
        viewModelScope.coroutineContext[Job]?.invokeOnCompletion { db.close() }
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    db.recoverInterrupted()
                    if (db.sessions().isEmpty()) db.createSession(ChatSession())
                    Triple(db.persona(), db.sessions(), db.messages())
                }
                mutable.value = mutable.value.copy(persona = data.first, sessions = data.second, messages = data.third,
                    sessionId = data.second.first().id)
                try { mutable.value = mutable.value.copy(loading = false, config = withContext(Dispatchers.IO) { vault.load() }) }
                catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, error = "模型配置无法读取，请在模型设置中重新填写") }
            } catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, error = "伴侣资料读取失败，请关闭并重新打开应用") }
        }
    }
    private fun scopeKey(): String {
        val s = mutable.value
        return listOf(s.config?.baseUrl, s.config?.model, s.sessionId, s.persona.toString()).joinToString("\n")
    }
    private fun operate(block: suspend () -> Unit) {
        if (mutable.value.busy || mutable.value.loading) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "操作未完成，请检查输入或重试") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    private suspend fun reload() {
        val data = withContext(Dispatchers.IO) { db.sessions() to db.messages() }
        mutable.value = mutable.value.copy(sessions = data.first, messages = data.second)
    }
    fun savePersona(p: Persona, done: () -> Unit) = operate {
        val saved = p.validated()
        withContext(Dispatchers.IO) { db.savePersona(saved) }
        approved = null; mutable.value = mutable.value.copy(persona = saved); done()
    }
    fun saveConfig(c: ModelConfig, done: () -> Unit) = operate {
        val saved = c.validated()
        withContext(Dispatchers.IO) { vault.save(saved) }
        approved = null; mutable.value = mutable.value.copy(config = saved); done()
    }
    fun clearConfig(done: () -> Unit) = operate {
        withContext(Dispatchers.IO) { vault.clear() }
        approved = null; mutable.value = mutable.value.copy(config = null); done()
    }
    fun selectSession(id: String) {
        if (mutable.value.busy || mutable.value.sessions.none { it.id == id }) return
        approved = null; mutable.value = mutable.value.copy(sessionId = id, draft = drafts[id] ?: "", error = null)
    }
    fun newSession() = operate {
        val session = ChatSession()
        withContext(Dispatchers.IO) { db.createSession(session) }
        approved = null; mutable.value = mutable.value.copy(sessionId = session.id, draft = "")
        reload()
    }
    fun deleteSession(id: String, done: () -> Unit) = operate {
        withContext(Dispatchers.IO) {
            db.deleteSession(id)
            if (db.sessions().isEmpty()) db.createSession(ChatSession())
        }
        reload()
        drafts.remove(id)
        approved = null; mutable.value = mutable.value.copy(sessionId = mutable.value.sessions.first().id, draft = drafts[mutable.value.sessions.first().id] ?: "")
        done()
    }
    fun dismissConsent() { mutable.value = mutable.value.copy(consentText = null, consentRetryId = null) }
    fun clearError() { mutable.value = mutable.value.copy(error = null) }
    fun send(text: String, retryId: String? = null, consent: Boolean = false, accepted: () -> Unit = {}) {
        val s = mutable.value
        if (s.busy || s.loading) return
        val config = s.config
        if (config == null) { mutable.value = s.copy(error = "请先在模型设置中填写接口和密钥"); return }
        if (text.isBlank() || text.length > 4000) { mutable.value = s.copy(error = "消息需为 1—4000 字"); return }
        if (s.sessionId.isBlank()) { mutable.value = s.copy(error = "当前话题不可用，请重新打开应用"); return }
        if (!consent && approved != scopeKey()) {
            mutable.value = s.copy(consentText = text, consentRetryId = retryId); return
        }
        approved = scopeKey()
        mutable.value = s.copy(consentText = null, consentRetryId = null)
        val retry = s.messages.find { it.id == retryId && it.sessionId == s.sessionId && it.role == "user" && it.status == "error" }
        val message = (retry ?: ChatMessage(sessionId = s.sessionId, role = "user", content = text.trim(), status = "pending")).copy(createdAt = System.currentTimeMillis())
        // Retry moves the failed message to the end and uses only this topic as context.
        val conversation = s.messages.filter { it.sessionId == s.sessionId }
        val history = conversation.filter { it.id != retry?.id }
        operate {
            withContext(Dispatchers.IO) { db.pending(message) }
            if (retry == null) updateDraft("")
            accepted(); reload()
            try {
                val reply = withContext(Dispatchers.IO) { client.chat(config, s.persona, history, message.content) }
                withContext(Dispatchers.IO) { db.complete(message, reply, config.model) }
                if (reply.truncated) mutable.value = mutable.value.copy(error = "回复达到单次长度上限，可继续追问")
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { db.fail(message.id) }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { db.fail(message.id) }
                // Never surface provider response bodies or URLs that might echo credentials.
                val safe = if (e is IllegalStateException) e.message else null
                mutable.value = mutable.value.copy(error = safe ?: "连接未完成，请检查网络和配置。重试可能产生服务商费用")
            }
            reload()
        }
    }
    suspend fun fetchModels(c: ModelConfig): List<String> = withContext(Dispatchers.IO) { client.models(c) }

}
