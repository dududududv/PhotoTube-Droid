package com.yunai.phototube.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ClearableCookieJar : CookieJar {
    fun clear()
}

class EncryptedCookieJar(context: Context) : ClearableCookieJar {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val entries = mutableListOf<PersistedCookie>()

    init {
        entries += readEntries()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        val updated = mergePersistedCookies(entries, url, cookies, now)
        entries.clear()
        entries += updated
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val active = activePersistedCookies(entries, now)
        if (active != entries) {
            entries.clear()
            entries += active
            persist()
        }
        return cookiesForRequest(entries, url, now)
    }

    @Synchronized
    override fun clear() {
        entries.clear()
        preferences.edit().remove(KEY_ENCRYPTED_COOKIES).apply()
    }

    private fun readEntries(): List<PersistedCookie> {
        val encrypted = preferences.getString(KEY_ENCRYPTED_COOKIES, null) ?: return emptyList()
        return runCatching {
            decodePersistedCookies(decrypt(encrypted), System.currentTimeMillis())
        }.getOrElse {
            preferences.edit().remove(KEY_ENCRYPTED_COOKIES).apply()
            emptyList()
        }
    }

    private fun persist() {
        preferences.edit()
            .putString(KEY_ENCRYPTED_COOKIES, encrypt(encodePersistedCookies(entries)))
            .apply()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, cipherText)
            .joinToString(SEPARATOR) { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(SEPARATOR, limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "phototube_secure_session"
        const val KEY_ENCRYPTED_COOKIES = "encrypted_cookies"
        const val KEY_ALIAS = "phototube.session.cookies.v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val SEPARATOR = "."
    }
}
