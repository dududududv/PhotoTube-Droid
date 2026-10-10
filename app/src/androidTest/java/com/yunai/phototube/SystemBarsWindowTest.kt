package com.yunai.phototube

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.yunai.phototube.ui.theme.LocalDarkTheme
import com.yunai.phototube.ui.theme.ThemeSystemBars
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic solid-colour windows only; no app session, media or server. */
class SystemBarsWindowTest {
    @get:Rule val rule=createComposeRule()

    @Test fun nestedWindowsFollowThemeAndRestoreMediaAppearanceOnReturn() {
        val management=mutableStateOf(false)
        val dark=mutableStateOf(false)
        lateinit var mediaView:View
        lateinit var managementView:View
        rule.setContent {
            CompositionLocalProvider(LocalDarkTheme provides dark.value) {
                Dialog({},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
                    ThemeSystemBars(media=true)
                    val view=LocalView.current
                    SideEffect {mediaView=view}
                    Box(Modifier.fillMaxSize().background(Color.Black))
                    if(management.value) Dialog({},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
                        ThemeSystemBars()
                        val topView=LocalView.current
                        SideEffect {managementView=topView}
                        Box(Modifier.fillMaxSize().background(if(dark.value) Color.Black else Color.White))
                    }
                }
            }
        }
        fun awaitAppearance(view:()->View,light:Boolean) {
            rule.waitUntil(10000) {
                val current=view()
                val window=(current.parent as? DialogWindowProvider)?.window
                window!=null && current.hasWindowFocus() && WindowCompat.getInsetsController(window,current).let {
                    it.isAppearanceLightNavigationBars==light && it.isAppearanceLightStatusBars==light
                }
            }
        }
        rule.waitForIdle()
        awaitAppearance({mediaView},false)
        rule.runOnIdle {management.value=true}
        rule.waitForIdle()
        awaitAppearance({managementView},true)
        rule.runOnIdle {dark.value=true}
        awaitAppearance({managementView},false)
        rule.runOnIdle {dark.value=false}
        awaitAppearance({managementView},true)
        rule.runOnIdle {management.value=false}
        awaitAppearance({mediaView},false)
        rule.runOnIdle {management.value=true}
        rule.waitForIdle()
        awaitAppearance({managementView},true)
    }
}
