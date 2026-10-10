package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.PagingData
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.BulkAssetsScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic in-memory paging data; no socket, session or private media. */
class BulkAnchorTest {
    @get:Rule val rule=createComposeRule()
    @Test fun missingRowLeaderUsesSelectedPhotoAfterLoading() {
        val server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,LocalMockData.BASE,{})
        val loading=androidx.paging.LoadStates(androidx.paging.LoadState.Loading,androidx.paging.LoadState.NotLoading(false),androidx.paging.LoadState.NotLoading(false))
        val ready=androidx.paging.LoadStates(androidx.paging.LoadState.NotLoading(true),androidx.paging.LoadState.NotLoading(true),androidx.paging.LoadState.NotLoading(true))
        val flow=kotlinx.coroutines.flow.MutableStateFlow(PagingData.empty<WallEntry>(loading))
        val assets=(0 until 150).map {i -> WallEntry.Asset(RemoteAsset("synthetic-$i","PHOTO","BROWSABLE","2026-09-01T00:00:00Z",0,"fixture-$i",null,48,32,false,null))}
        try {
            rule.setContent {PhotoTubeTheme {
                BulkAssetsScreen(server,WallFilters(),150,{}, {},sourceFlow=flow,
                    initialRowKey="row-removed",initialSelectedId="synthetic-121")
            }}
            rule.waitForIdle()
            rule.onNodeWithTag("bulk-anchor-unavailable").assertDoesNotExist()
            rule.runOnIdle {flow.value=PagingData.from<WallEntry>(assets,ready)}
            rule.waitUntil(10000) {rule.onAllNodesWithTag("bulk-asset-synthetic-121").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithTag("bulk-asset-synthetic-121").assertIsDisplayed().assertIsSelected()
            rule.onNodeWithTag("bulk-grid").performScrollToIndex(0)
            rule.onNodeWithTag("bulk-anchor-unavailable").assertIsDisplayed()
        } finally {server.dispose()}
    }

    @Test fun missingAnchorSettlesAndDoesNotJumpWhenItAppearsLater() {
        val server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,LocalMockData.BASE,{})
        fun assets(start:Int,end:Int)=(start until end).map {i -> WallEntry.Asset(RemoteAsset("synthetic-$i","PHOTO","BROWSABLE","2026-09-01T00:00:00Z",0,"fixture-$i",null,48,32,false,null))}
        val ready=androidx.paging.LoadStates(androidx.paging.LoadState.NotLoading(true),androidx.paging.LoadState.NotLoading(true),androidx.paging.LoadState.NotLoading(true))
        val flow=kotlinx.coroutines.flow.MutableStateFlow(PagingData.from<WallEntry>(assets(0,100),ready))
        try {
            rule.setContent {PhotoTubeTheme {
                BulkAssetsScreen(server,WallFilters(),150,{}, {},sourceFlow=flow,
                    initialRowKey="row-synthetic-121",initialSelectedId="synthetic-121")
            }}
            rule.waitUntil(10000) {rule.onAllNodesWithTag("bulk-anchor-unavailable").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("已选 1 项").assertIsDisplayed()
            rule.onNodeWithTag("bulk-asset-synthetic-0").assertIsDisplayed()
            rule.runOnIdle {flow.value=PagingData.from<WallEntry>(assets(0,150),ready)}
            rule.waitForIdle()
            rule.onNodeWithTag("bulk-asset-synthetic-0").assertIsDisplayed()
            rule.onNodeWithTag("bulk-asset-synthetic-121").assertDoesNotExist()
        } finally {server.dispose()}
    }

    @Test fun suppliedPagingWindowOpensAtAnchorAndDoesNotForceItAfterUserScroll() {
        val server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,LocalMockData.BASE,{})
        val assets=(0 until 150).map {i -> WallEntry.Asset(RemoteAsset("synthetic-$i","PHOTO","BROWSABLE","2026-09-01T00:00:00Z",0,"fixture-$i",null,48,32,false,null))}
        val flow=flowOf(PagingData.from<WallEntry>(assets))
        try {
            rule.setContent {PhotoTubeTheme {
                BulkAssetsScreen(server,WallFilters(),150,{}, {},sourceFlow=flow,initialRowKey="row-synthetic-121",initialRowOffset=12)
            }}
            rule.waitUntil(10000) {rule.onAllNodesWithTag("bulk-asset-synthetic-121").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithTag("bulk-asset-synthetic-121").assertIsDisplayed().performClick()
            rule.onNodeWithTag("bulk-asset-synthetic-121").assertIsSelected()
            rule.onNodeWithTag("bulk-grid").performScrollToIndex(0)
            rule.onNodeWithText("已选 1 项").assertIsDisplayed()
            rule.onNodeWithTag("bulk-asset-synthetic-121").assertDoesNotExist()
        } finally {server.dispose()}
    }
}
