package com.yunai.phototube

import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.SessionCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/** Run with sessionRestartPhase=seed, then restore in a separate instrumentation process. */
class SessionRestartTest {
    @Test fun encryptedSessionSurvivesProcessAndInstall() {
        val phase=InstrumentationRegistry.getArguments().getString("sessionRestartPhase") ?: return
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        // An isolated, non-routable origin; never inspect or mutate the NAS session.
        val origin="https://session-restart.invalid/".toHttpUrl()
        val jar=SessionCookieJar(context,origin)
        if(phase=="seed") {
            jar.saveFromResponse(origin,listOf(Cookie.Builder().name("phototube_session")
                .value("restart-fixture-only").hostOnlyDomain(origin.host).path("/").secure().httpOnly()
                .expiresAt(System.currentTimeMillis()+3600000).build()))
            assertEquals("restart-fixture-only",SessionCookieJar(context,origin).loadForRequest(origin).single().value)
        } else {
            try {assertEquals("restart-fixture-only",jar.loadForRequest(origin).single().value)}
            finally {jar.clear()}
        }
    }
}
