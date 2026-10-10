package com.yunai.phototube

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.data.WallFilters
import com.yunai.phototube.ui.CornerInsets
import com.yunai.phototube.ui.FilterMenu
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.*

class FavoriteFilterTest {
    @get:Rule val rule=createComposeRule()
    @Test fun largeTextShortWindowCanReachClearAndFavoriteToggle() {
        var latest=WallFilters(keyword="合成搜索",favorite=true)
        var dismissals=0
        rule.setContent {PhotoTubeTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,2f)) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(340.dp,320.dp)) {
                    var filters by remember {mutableStateOf(latest)}
                    FilterMenu(rememberLayerBackdrop(),CornerInsets(16.dp,16.dp,16.dp,16.dp),.4f,filters,
                        onChange={filters=it;latest=it},onDismiss={dismissals++})
                }
            }
        }}
        rule.onNodeWithText("清除筛选").performScrollTo().performClick()
        rule.runOnIdle {Assert.assertEquals(WallFilters(keyword="合成搜索"),latest)}
        rule.onNodeWithText("只看收藏").performScrollTo().performClick()
        rule.waitForIdle()
        rule.runOnIdle {Assert.assertEquals(true,latest.favorite);Assert.assertEquals(0,dismissals)}
        rule.onNodeWithText("完成").assertDoesNotExist()
    }
    @Test fun threeStatesPreserveTypedBatchScopeAndOtherConditions() {
        val initial=WallFilters(keyword="旅行照",libraryId="library",folderPath="旅行",tagsExclude=setOf(12))
        Assert.assertFalse(initial.query().containsKey("favorite"))
        val unfavorited=initial.copy(favorite=false)
        Assert.assertEquals("false",unfavorited.query()["favorite"])
        Assert.assertEquals(false,unfavorited.json().get("favorite"))
        Assert.assertTrue(unfavorited.active)
        Assert.assertTrue(unfavorited.label.contains("未收藏"))
        Assert.assertEquals("true",initial.copy(favorite=true).query()["favorite"])
        Assert.assertEquals(initial.query(),unfavorited.copy(favorite=null).query())
    }
    @Test fun favoriteToggleRestoresAllAndKeepsOtherConditions() {
        var latest=WallFilters(keyword="旅行照",kind=com.yunai.phototube.data.WallKind.PHOTO)
        var dismissals=0
        rule.setContent {PhotoTubeTheme {
            var filters by remember {mutableStateOf(latest)}
            var shown by remember {mutableStateOf(true)}
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            if(shown) FilterMenu(rememberLayerBackdrop(),CornerInsets(16.dp,16.dp,32.dp,16.dp),.15f,filters,
                onChange={filters=it;latest=it},onDismiss={dismissals++;shown=false})
            else androidx.compose.material3.TextButton({shown=true}) {androidx.compose.material3.Text("打开")}
            }
        }}
        rule.onNodeWithText("不限收藏").assertDoesNotExist()
        rule.onNodeWithText("只看未收藏").assertDoesNotExist()
        rule.onNodeWithText("只看收藏").assertIsOff().performClick()
        rule.onNodeWithText("只看收藏").assertIsOn()
        rule.runOnIdle {Assert.assertEquals(true,latest.favorite);Assert.assertEquals(0,dismissals)}
        rule.onNodeWithContentDescription("关闭筛选菜单").performTouchInput {click(androidx.compose.ui.geometry.Offset(5f,center.y))}
        rule.waitForIdle()
        rule.onNodeWithText("打开").performClick()
        rule.onNodeWithText("只看收藏").assertIsOn().performClick()
        rule.onNodeWithText("只看收藏").assertIsOff()
        rule.runOnIdle {
            Assert.assertNull(latest.favorite)
            Assert.assertFalse(latest.query().containsKey("favorite"))
            Assert.assertEquals("旅行照",latest.keyword)
            Assert.assertEquals(com.yunai.phototube.data.WallKind.PHOTO,latest.kind)
        }
        rule.onNodeWithText("只看归档").assertIsOff().performClick()
        rule.onNodeWithText("只看归档").assertIsOn()
        rule.onNodeWithTag("filter-menu").assertExists()
        rule.onNodeWithContentDescription("关闭筛选菜单").performTouchInput {click(androidx.compose.ui.geometry.Offset(5f,center.y))}
        rule.waitForIdle()
        rule.runOnIdle {Assert.assertTrue(latest.archived);Assert.assertEquals(2,dismissals)}
    }
}
