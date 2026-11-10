package app.localrp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provider API key storage (DeepSeek item 14, Phase 5): the plaintext key only ever
 * lives in memory — on disk an AES/GCM ciphertext encrypted with an AndroidKeyStore
 * key survives, so a copied backup of app-private prefs cannot yield the secret.
 * Device traffic remains user-initiated only (strictly opt-in in the routing batch).
 *
 * The crypto helpers ([encryptValue]/[decryptValue]) are key-parameterized so the
 * JVM unit tests exercise them with a synthetic key; the Keystore generation path
 * [deviceKey] is Android-only and guarded so a missing provider degrades to "off".
 */
object ApiKeyStore {
    private const val PREFS = "provider"
    private const val PREF_KEY = "provider_api_key_enc"
    private const val ALIAS = "localrp_provider_key"
    private const val GCM_TAG_BITS = 128

    /** Stores the key encrypted at rest; returns false when Keystore is unavailable. */
    fun save(context: Context, plain: String): Boolean {
        val trimmed = plain.trim()
        if (trimmed.isEmpty()) return false
        val key = deviceKey(context) ?: return false
        val encrypted = runCatching { encryptValue(key, trimmed) }.getOrNull() ?: return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, encrypted).apply()
        return true
    }

    /** Decrypts in memory only, on demand; never logs or persists the plaintext. */
    fun load(context: Context): String? {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_KEY, null) ?: return null
        val key = deviceKey(context) ?: return null
        return decryptValue(key, stored)
    }

    /** Erases the configured secret (user-initiated "remove key" action). */
    fun erase(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(PREF_KEY).apply()
    }

    /** True only when a decrypted key actually exists (a lost-Keystore ciphertext no longer lies). */
    fun isConfigured(context: Context): Boolean = load(context) != null

    /** Keystore-backed AES key, generated on first use; null without the provider. */
    private fun deviceKey(context: Context): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: run {
                val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
                generator.init(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generator.generateKey()
            }
    }.getOrNull()

    /** AES/GCM with a fresh IV each time; output is Base64(iv || ciphertext). */
    fun encryptValue(key: SecretKey, plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + encrypted.size)
        iv.copyInto(packed)
        encrypted.copyInto(packed, iv.size)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
    }

    /** Null (never an exception) for tampered, truncated or foreign-format payloads. */
    fun decryptValue(key: SecretKey, stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        return runCatching {
            val packed = Base64.getUrlDecoder().decode(stored)
            if (packed.size <= GCM_TAG_BITS / 8) return null
            val iv = packed.copyOfRange(0, 12)
            val encrypted = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }
}
