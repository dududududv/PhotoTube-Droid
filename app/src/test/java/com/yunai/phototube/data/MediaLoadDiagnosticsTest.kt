package com.yunai.phototube.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MediaLoadDiagnosticsTest {
    @Test fun logsKeepConstructedMediaUrlButRemoveCredentialsAndUnrelatedParameters() {
        val hash = "a".repeat(64)
        assertEquals("http://127.0.0.1:18474/api/v1/assets/test/thumbnail?size=MD&v=$hash",
            MediaLoadDiagnostics.safeUrl("http://user:secret@127.0.0.1:18474/api/v1/assets/test/thumbnail?size=MD&v=$hash&token=secret#secret"))
        assertNull(MediaLoadDiagnostics.safeUrl("http://127.0.0.1:18474/api/v1/auth/login"))
        assertNull(MediaLoadDiagnostics.safeUrl("http://127.0.0.1:18474/api/v1/assets/timeline-summary"))
    }
    @Test fun exceptionMessagesDoNotExposeAuthenticationHeaders() {
        val output = MediaLoadDiagnostics.reason(IOException("Cookie: phototube_session=secret\nAuthorization: bearer hidden",
            IOException("failed at http://user:secret@127.0.0.1:18474/api/v1/assets/test/original?token=secret")))
        assertFalse(output.contains("secret")); assertFalse(output.contains("hidden"))
        assertTrue(output.contains("IOException")); assertTrue(output.contains("[redacted]"))
    }
}
