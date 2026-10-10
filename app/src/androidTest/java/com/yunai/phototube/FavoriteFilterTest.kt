package com.yunai.phototube

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.size
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
    @Test fun largeTextShortWindowCanReachClearAndCascade() {
        var latest=WallFilters(keyword="合成搜索",favorite=true)
        rule.setContent {PhotoTubeTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,2f)) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.then(androidx.compose.ui.Modifier.size(340.dp,320.dp))) {
                    var filters by remember {mutableStateOf(latest)}
                    FilterMenu(rememberLayerBackdrop(),CornerInsets(16.dp,16.dp,16.dp,16.dp),.4f,filters,
                        onChange={filters=it;latest=it},onDismiss={})
                }
            }
        }}
        rule.onNodeWithText("清除筛选").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle {Assert.assertEquals(WallFilters(keyword="合成搜索"),latest)}
        rule.onNodeWithText("收藏状态").performScrollTo().performClick()
        rule.onNodeWithText("只看未收藏").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle {Assert.assertEquals(false,latest.favorite)}
        rule.onNodeWithText("完成").assertIsDisplayed()
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
    @Test fun glassCascadeSelectsUnfavoritedAndCanClearIt() {
        var latest=WallFilters(keyword="旅行照")
        var dismissals=0
        rule.setContent {PhotoTubeTheme {
            var filters by remember {mutableStateOf(latest)}
            FilterMenu(rememberLayerBackdrop(),CornerInsets(16.dp,16.dp,32.dp,16.dp),.15f,filters,
                onChange={filters=it;latest=it},onDismiss={dismissals++})
        }}
        rule.onNodeWithText("收藏状态").performClick()
        rule.onNodeWithText("只看未收藏").performClick()
        rule.runOnIdle {Assert.assertEquals(false,latest.favorite)}
        rule.onNodeWithText("收藏状态").performClick()
        rule.onNodeWithText("只看未收藏").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,androidx.compose.ui.semantics.Role.RadioButton))
        rule.onNodeWithText("全部").assertIsNotSelected()
        rule.onNodeWithText("全部").performClick()
        rule.runOnIdle {Assert.assertNull(latest.favorite);Assert.assertEquals("旅行照",latest.keyword)}
        rule.onNodeWithText("收藏状态").performClick()
        rule.onNodeWithText("只看收藏").performClick()
        rule.onNodeWithText("清除筛选").performClick()
        rule.runOnIdle {Assert.assertNull(latest.favorite);Assert.assertEquals("旅行照",latest.keyword)}
        rule.onNodeWithText("只看归档").assertIsOff()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,androidx.compose.ui.semantics.Role.Checkbox))
        rule.onNodeWithText("只看归档").performClick().assertIsOn()
        rule.onNodeWithText("媒体类型").performClick()
        rule.onNodeWithText("照片与视频").assertIsSelected()
        rule.onNodeWithContentDescription("返回筛选菜单").performClick()
        rule.mainClock.autoAdvance=false
        rule.onNodeWithText("完成").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("完成").assertIsNotEnabled()
        Assert.assertEquals(0,dismissals)
        rule.mainClock.autoAdvance=true
        rule.waitForIdle()
        Assert.assertEquals(1,dismissals)
    }
}
