package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import com.yunai.phototube.ui.remote.RemotePhotoWall
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class LargeLibraryPagingTest : RemoteAppTest() {
    override val assetCountFixture = 100_000
    override val photographicFixtures = true
    private val cursors = CopyOnWriteArrayList<String>()
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (request.requestUrl!!.encodedPath == "/api/v1/assets") {
            assertNull(request.requestUrl!!.queryParameter("offset"))
            cursors += request.requestUrl!!.queryParameter("cursor") ?: "first"
        }
        return null
    }

    @Test fun scrollBeyondCacheThenReloadEvictedPagesByCursor() {
        lateinit var state: LazyListState
        lateinit var scope: CoroutineScope
        rule.runOnUiThread {
            UiTestActivity.content = {
                state = rememberLazyListState()
                scope = rememberCoroutineScope()
                PhotoTubeTheme { RemotePhotoWall(wall, 0.dp, listState = state) }
            }
        }
        waitTag("asset-restore-0")
        var maxRows = 0
        fun visibleIds(): List<Int> = rule.runOnIdle {
            maxRows = maxOf(maxRows, state.layoutInfo.totalItemsCount)
            state.layoutInfo.visibleItemsInfo.mapNotNull {
                it.key.toString().removePrefix("row-restore-").toIntOrNull()
            }
        }
        fun advance(forward: Boolean) {
            rule.runOnIdle {
                val next = (state.firstVisibleItemIndex + if (forward) 6 else -6)
                    .coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                scope.launch { state.scrollToItem(next) }
            }
            rule.waitForIdle()
        }
        var steps = 0
        while ((visibleIds().maxOrNull() ?: 0) < 900 && steps++ < 240) advance(true)
        assertTrue("Could not reach page ten; cursors=$cursors", (visibleIds().maxOrNull() ?: 0) >= 900)
        assertTrue("Should fetch a small window, not the whole library: ${cursors.size}", cursors.size < 30)
        val anchor=visibleIds().first()
        val flowBefore=wall.pageFlow
        val requestsBefore=cursors.size
        rule.runOnIdle {wall.refresh()}
        rule.waitUntil(15000) {!wall.busy && cursors.size>requestsBefore}
        assertSame("Business refresh must retain its pager",flowBefore,wall.pageFlow)
        rule.waitUntil(10000) {rule.onAllNodesWithTag("asset-restore-$anchor").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("asset-restore-$anchor").assertIsDisplayed()
        assertNotEquals("Refresh must use the current cursor", "first",cursors[requestsBefore])
        val firstLoads = cursors.count { it == "first" }
        steps = 0
        while (0 !in visibleIds() && steps++ < 240) advance(false)
        rule.onNodeWithTag("asset-restore-0").assertIsDisplayed()
        assertTrue("Evicted first page should be fetched again: $cursors", cursors.count { it == "first" } > firstLoads)
        assertTrue(cursors.size < 40)
        // Fixture images form two-photo rows. Allow transient page delivery, but not
        // an accumulating 900-photo/450-row history after the 500-item cache limit.
        assertTrue("Photo wall retained $maxRows rows", maxRows < 400)
        println("Large library: maxRows=$maxRows, requests=${cursors.size}, cursors=$cursors")
    }
}
