package com.example.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "SecureTokenStore"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "openjarvis_github_pat_aes_key"
private const val PREFS_NAME = "openjarvis_secure_vault"
private const val KEY_GITHUB_PAT_ENC = "github_pat_enc"
private const val GCM_TAG_BITS = 128

/**
 * Armazenamento criptografado com Android Keystore (AES-256-GCM) para credenciais
 * sensíveis fornecidas pelo próprio usuário em tempo de execução (ex.: GitHub PAT).
 * Nunca grava tokens em texto puro e nunca expõe valores em logs.
 */
class SecureTokenStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveGitHubToken(rawToken: String): Boolean {
        val clean = rawToken.trim()
        if (clean.isEmpty()) {
            clearGitHubToken()
            return true
        }
        return runCatching {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(KEY_GITHUB_PAT_ENC, encoded).apply()
            true
        }.onFailure { err ->
            Log.e(TAG, "Failed to encrypt token in Android Keystore: ${err.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    fun getGitHubToken(): String? {
        val encoded = prefs.getString(KEY_GITHUB_PAT_ENC, null) ?: return null
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 14) return null
            val ivLen = combined[0].toInt() and 0xFF
            if (ivLen <= 0 || combined.size <= 1 + ivLen) return null
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val ciphertext = combined.copyOfRange(1 + ivLen, combined.size)
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8).takeIf { it.isNotBlank() }
        }.onFailure { err ->
            Log.e(TAG, "Failed to decrypt token from Android Keystore: ${err.javaClass.simpleName}")
        }.getOrNull()
    }

    fun hasGitHubToken(): Boolean = !getGitHubToken().isNullOrBlank()

    fun clearGitHubToken() {
        prefs.edit().remove(KEY_GITHUB_PAT_ENC).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
