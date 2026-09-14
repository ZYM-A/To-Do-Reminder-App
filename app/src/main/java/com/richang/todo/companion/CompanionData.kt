package com.richang.todo.companion

import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Persona(
    val name: String = "AI 伴侣",
    val nickname: String = "",
    val personality: String = "温柔、耐心，认真倾听，尊重彼此的生活。",
    val style: String = "自然简洁，先理解感受；需要时再一起想办法。",
) {
    fun validated(): Persona {
        require(name.isNotBlank() && name.length <= 40) { "名字需为 1—40 字" }
        require(nickname.length <= 40) { "称呼最多 40 字" }
        require(personality.length <= 4000) { "性格最多 4000 字" }
        require(style.length <= 2000) { "说话风格最多 2000 字" }
        return copy(name = name.trim(), nickname = nickname.trim(), personality = personality.trim(), style = style.trim())
    }
    fun prompt() = """你是一个提供温柔陪伴的 AI。诚实表明 AI 身份，不声称是真人；尊重用户自主，不以愧疚、占有或排他表达要求用户陪伴。不要编造未提供的记忆，不声称能操作日记或日程。
角色名字：$name
对用户的称呼：${nickname.ifBlank { "自然称呼，不自行编造昵称" }}
性格：$personality
说话风格：$style"""
}

/** Never include credentials in generated toString, logs, saved UI state or database rows. */
class ModelConfig(val baseUrl: String = "", val model: String = "", val apiKey: String = "") {
    fun validated(requireModel: Boolean = true): ModelConfig {
        val base = baseUrl.trim().trimEnd('/')
        val uri = try { URI(base) } catch (_: Exception) { throw IllegalArgumentException("请输入有效的 HTTPS 接口地址") }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null) { "仅支持 HTTPS 地址，不能含账号、查询参数或片段" }
        require(!base.endsWith("/chat/completions") && !base.endsWith("/models")) { "请填写基础地址，例如 https://服务商域名/v1" }
        require(base.length <= 1000 && (uri.port == -1 || uri.port in 1..65535)) { "接口地址无效" }
        require(apiKey.isNotBlank() && apiKey.length <= 4096 && apiKey.none { it.isWhitespace() || it.code < 32 }) { "密钥不能为空或包含空格、换行" }
        require(!requireModel || (model.isNotBlank() && model.length <= 200)) { "请填写模型 ID" }
        return ModelConfig(base, model.trim(), apiKey.trim())
    }
    val destination: String get() = runCatching { URI(baseUrl).authority }.getOrNull() ?: ""
    override fun toString() = "ModelConfig(redacted)"
}
data class ChatSession(val id: String = UUID.randomUUID().toString(), val title: String = "新话题", val createdAt: Long = System.currentTimeMillis())
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(), val sessionId: String, val role: String, val content: String,
    val status: String = "sent", val createdAt: Long = System.currentTimeMillis(), val model: String = "",
    val tokens: Long? = null,
)
data class ChatReply(val content: String, val tokens: Long?, val truncated: Boolean = false)
data class ChatStats(val messages: Int, val replies: Int, val activeDays: Int, val togetherDays: Long, val tokens: Long, val measuredReplies: Int) {
    companion object {
        fun from(messages: List<ChatMessage>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): ChatStats {
            val sent = messages.filter { it.status == "sent" }
            val dates = sent.filter { it.role == "user" }.map { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            val replies = sent.filter { it.role == "assistant" }
            return ChatStats(sent.size, replies.size, dates.distinct().size,
                dates.minOrNull()?.let { (ChronoUnit.DAYS.between(it, today) + 1).coerceAtLeast(1) } ?: 0,
                replies.sumOf { it.tokens ?: 0 }, replies.count { it.tokens != null })
        }
    }
}
