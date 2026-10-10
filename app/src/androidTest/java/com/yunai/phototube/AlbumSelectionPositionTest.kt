package com.yunai.phototube

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Subsequent-page selection must inherit the visible album, not reload the first page. */
class AlbumSelectionPositionTest : RemoteAppTest() {
    override val photographicFixtures=true
    private val firstPageReads=AtomicInteger()
    override fun readFixture(request:RecordedRequest):MockResponse? {
        if(request.requestUrl!!.encodedPath=="/api/v1/albums/restore-album/assets" && request.requestUrl!!.queryParameter("cursor")==null)
            firstPageReads.incrementAndGet()
        return null
    }
    @Test fun removalUsesCurrentPageAndZoomThenReturnsWithoutMoving() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithTag("album-restore-album").performClick();waitTag("album-grid")
        rule.onNodeWithText("放大缩略图").performClick()
        rule.onNodeWithTag("album-grid").performScrollToNode(hasTestTag("album-media-restore-120"))
        rule.waitForIdle()
        val candidates=rule.onAllNodes(SemanticsMatcher("Album photo") {
            it.config.getOrElse(SemanticsProperties.TestTag) {""}.startsWith("album-media-")
        }).fetchSemanticsNodes().filter {it.boundsInWindow.height>1f}
        val source=candidates.minBy {it.boundsInWindow.top}
        val id=source.config[SemanticsProperties.TestTag].removePrefix("album-media-")
        assertTrue("Use a subsequent page",id.removePrefix("restore-").toInt()>90)
        val before=source.boundsInWindow
        val reads=firstPageReads.get()
        rule.onNodeWithText("选择").performClick()
        waitTag("member-select-$id")
        rule.onNodeWithTag("member-select-$id").assertIsDisplayed()
        val selection=rule.onNodeWithTag("member-select-$id").fetchSemanticsNode().boundsInWindow
        assertEquals("Keep the selected thumbnail zoom",before.width,selection.width,1f)
        assertEquals("Do not reopen the first page for selection",reads,firstPageReads.get())
        rule.onNodeWithTag("member-select-$id").performClick().assertIsSelected()
        auditShot("album-selection-current-page")
        rule.onNodeWithContentDescription("返回").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("album-member-grid").fetchSemanticsNodes().isEmpty()}
        assertEquals(before,rule.onNodeWithTag("album-media-$id").fetchSemanticsNode().boundsInWindow)
    }
}
