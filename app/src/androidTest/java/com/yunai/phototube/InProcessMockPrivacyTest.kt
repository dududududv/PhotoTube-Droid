package com.yunai.phototube

import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Bundled in-process fixtures only: no sockets, account or device media. */
class InProcessMockPrivacyTest {
    @Test fun revokedInProcessForkRejectsReadsWithoutAffectingOrdinaryLibrary() = runBlocking {
        val fixture=LocalMockFixture(InstrumentationRegistry.getInstrumentation().targetContext)
        val server=fixture.server()
        val allowed=AtomicBoolean(true)
        val isolated=server.privateFork({allowed.get()},{})
        try {
            assertTrue(isolated.assets(null,WallFilters(privateOnly=true)).items.isNotEmpty())
            assertNull(isolated.imageLoader.memoryCache)
            assertNull(isolated.imageLoader.diskCache)
            allowed.set(false)
            try {
                isolated.assets(null,WallFilters(privateOnly=true))
                fail("Revoked in-process mock must reject reads")
            } catch(expected:IOException) {
                assertEquals("私密空间已锁定",expected.message)
            }
            assertTrue(server.assets(null).items.isNotEmpty())
        } finally {isolated.dispose();server.dispose();fixture.close()}
    }
}
