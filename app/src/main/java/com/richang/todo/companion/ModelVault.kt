package com.richang.todo.companion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ModelVault(context: Context, private val keyProvider: () -> SecretKey = ::androidKey) {
    private val prefs = context.getSharedPreferences("companion_secure", Context.MODE_PRIVATE)
    fun load(): ModelConfig? {
        val stored = prefs.getString("encrypted_config", null) ?: return null
        try {
            val parts = stored.split(":")
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            cipher.updateAAD("richang-model-v1".toByteArray())
            val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
            return ModelConfig(json.getString("base"), json.getString("model"), json.getString("key")).validated()
        } catch (_: Exception) { throw IllegalStateException("模型配置无法解密，请重新填写配置") }
    }
    fun save(config: ModelConfig) {
        val valid = config.validated()
        val clear = JSONObject().put("base", valid.baseUrl).put("model", valid.model).put("key", valid.apiKey).toString().toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD("richang-model-v1".toByteArray())
        val encrypted = cipher.doFinal(clear)
        clear.fill(0)
        check(prefs.edit().putString("encrypted_config", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "模型配置保存失败" }
    }
    fun clear() { check(prefs.edit().remove("encrypted_config").commit()) { "配置清除失败" } }
    companion object {
        private const val ALIAS = "richang_model_key_v1"
        private fun androidKey(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
        }
    }
}
