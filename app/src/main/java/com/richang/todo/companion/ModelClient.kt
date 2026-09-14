package com.richang.todo.companion

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream

class ModelClient(private val connection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    fun models(config: ModelConfig): List<String> {
        val json = JSONObject(execute(config.validated(false), "/models", null))
        val data = json.optJSONArray("data") ?: throw IllegalStateException("接口未返回模型列表，可以手动填写模型 ID")
        return (0 until minOf(data.length(), 1000)).mapNotNull { data.optJSONObject(it)?.optString("id") }
            .filter { it.isNotBlank() && it.length <= 200 }.distinct().sorted()
    }
    fun chat(config: ModelConfig, persona: Persona, history: List<ChatMessage>, text: String): ChatReply {
        val valid = config.validated()
        val payload = payload(valid, persona.validated(), history, text)
        val json = JSONObject(execute(valid, "/chat/completions", payload.toString()))
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val content = choice?.optJSONObject("message")?.opt("content") as? String
        check(!content.isNullOrBlank() && content.length <= 30_000) { "模型未返回有效文字，请尝试支持文字聊天的模型" }
        val usage = json.optJSONObject("usage")
        val tokens = if (usage?.has("total_tokens") == true && !usage.isNull("total_tokens"))
            usage.optLong("total_tokens", -1).takeIf { it >= 0 } else null
        return ChatReply(content, tokens, choice?.optString("finish_reason") == "length")
    }
    private fun execute(config: ModelConfig, path: String, body: String?): String {
        val c = connection(URL(config.baseUrl + path))
        try {
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000; c.readTimeout = 90_000
            c.requestMethod = if (body == null) "GET" else "POST"
            c.setRequestProperty("Authorization", "Bearer " + config.apiKey)
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            check(status in 200..299) {
                when (status) {
                    in 300..399 -> "接口要求跳转，已阻止发送密钥，请核对基础地址"
                    401, 403 -> "鉴权失败，请检查密钥和模型权限"
                    402 -> "服务商提示余额不足，请检查账户"
                    404 -> "接口或模型不存在，请核对地址和模型 ID"
                    429 -> "服务商限流或额度不足，请稍后手动重试"
                    in 500..599 -> "模型服务暂时不可用，请稍后手动重试"
                    else -> "请求未成功（HTTP $status），请检查模型和接口配置"
                }
            }
            return c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    check(out.size() + read <= 1_048_576) { "接口返回内容过大，已停止读取" }
                    out.write(buffer, 0, read)
                }
                out.toString("UTF-8")
            }
        } finally { c.disconnect() }
    }
    companion object {
        fun payload(config: ModelConfig, persona: Persona, history: List<ChatMessage>, text: String): JSONObject {
            require(text.isNotBlank() && text.length <= 4000) { "消息需为 1—4000 字" }
            val kept = mutableListOf<ChatMessage>()
            var chars = 0
            for (message in history.filter { it.status == "sent" && it.role in listOf("user", "assistant") }.takeLast(20).asReversed()) {
                if (chars + message.content.length > 24_000) break
                kept.add(message); chars += message.content.length
            }
            val messages = JSONArray().put(JSONObject().put("role", "system").put("content", persona.prompt()))
            kept.asReversed().dropWhile { it.role != "user" }.forEach {
                messages.put(JSONObject().put("role", it.role).put("content", it.content))
            }
            messages.put(JSONObject().put("role", "user").put("content", text))
            return JSONObject().put("model", config.model).put("messages", messages).put("stream", false).put("max_tokens", 4096)
        }
    }
}
