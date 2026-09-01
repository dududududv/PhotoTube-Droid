package com.yunai.phototube.data.session

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 在真实 Android Keystore 上验证 Cookie 密文落盘和 CookieJar 重建恢复。 */
@RunWith(AndroidJUnit4::class)
class EncryptedCookieJarInstrumentedTest {
    private lateinit var targetContext: Context
    private lateinit var isolatedContext: Context
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = targetContext.getSharedPreferences(TEST_PREFERENCES_NAME, Context.MODE_PRIVATE)
        check(preferences.edit().clear().commit())
        isolatedContext = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
        }
    }

    @After
    fun tearDown() {
        check(preferences.edit().clear().commit())
    }

    @Test
    fun encryptedCookieSurvivesJarRecreationWithoutLeakingPlaintext() {
        val responseUrl = "https://photos.example.test/api/v1/auth/login?source=android".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("phototube_session")
            .value(SECRET_VALUE)
            .hostOnlyDomain("photos.example.test")
            .path("/api/v1")
            .expiresAt(System.currentTimeMillis() + 86_400_000L)
            .secure()
            .httpOnly()
            .build()

        EncryptedCookieJar(isolatedContext).saveFromResponse(responseUrl, listOf(cookie))

        val encrypted = preferences.getString(ENCRYPTED_COOKIES_KEY, null)
        assertNotNull(encrypted)
        assertFalse(encrypted.orEmpty().contains(SECRET_VALUE))

        val restoredJar = EncryptedCookieJar(isolatedContext)
        val restored = restoredJar.loadForRequest(
            "https://photos.example.test/api/v1/assets".toHttpUrl(),
        )

        assertEquals(1, restored.size)
        assertEquals(SECRET_VALUE, restored.single().value)
        assertTrue(restored.single().secure)
        assertTrue(restored.single().httpOnly)
        assertTrue(restored.single().hostOnly)
        assertTrue(
            restoredJar.loadForRequest("https://other.example.test/api/v1/assets".toHttpUrl()).isEmpty(),
        )
        assertTrue(
            restoredJar.loadForRequest("http://photos.example.test/api/v1/assets".toHttpUrl()).isEmpty(),
        )
        assertTrue(
            restoredJar.loadForRequest("https://photos.example.test/health".toHttpUrl()).isEmpty(),
        )

        restoredJar.clear()
        assertNull(preferences.getString(ENCRYPTED_COOKIES_KEY, null))
    }

    @Test
    fun corruptedCiphertextIsDiscardedInsteadOfBreakingStartup() {
        check(preferences.edit().putString(ENCRYPTED_COOKIES_KEY, "not-valid-ciphertext").commit())

        val restoredJar = EncryptedCookieJar(isolatedContext)

        assertTrue(
            restoredJar.loadForRequest("https://photos.example.test/api/v1/assets".toHttpUrl()).isEmpty(),
        )
        assertFalse(preferences.contains(ENCRYPTED_COOKIES_KEY))
    }

    private companion object {
        const val TEST_PREFERENCES_NAME = "phototube_secure_session_instrumented_test"
        const val ENCRYPTED_COOKIES_KEY = "encrypted_cookies"
        const val SECRET_VALUE = "instrumented-secret-cookie-value"
    }
}
