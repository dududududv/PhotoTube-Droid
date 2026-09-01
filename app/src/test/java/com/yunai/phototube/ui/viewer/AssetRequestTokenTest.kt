package com.yunai.phototube.ui.viewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetRequestTokenTest {
    @Test
    fun `只有资产 ID 与 generation 同时一致才允许异步写回`() {
        val request = AssetRequestToken(assetId = "asset-a", generation = 7)

        assertTrue(request.matches(currentAssetId = "asset-a", currentGeneration = 7))
        assertFalse(request.matches(currentAssetId = "asset-b", currentGeneration = 7))
        assertFalse(request.matches(currentAssetId = "asset-a", currentGeneration = 8))
        assertFalse(request.matches(currentAssetId = null, currentGeneration = 7))
    }

    @Test
    fun `编辑完成事件显式携带所属资产`() {
        val event = EditApplyEvent(
            revision = 3,
            assetId = "asset-a",
            activeEdit = null,
        )

        assertTrue(event.assetId == "asset-a")
    }
}
