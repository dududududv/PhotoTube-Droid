package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.LocalConnection
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicit opt-in, user-authorized loopback service only. Never records media or credentials. */
class LocalIntegrationSmokeTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun loginReadGalleryAndRestoreSession() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(LocalConnection.enabled && args.getString("localIntegrationSmoke") == "true")
        val username = requireNotNull(args.getString("localUsername"))
        val password = requireNotNull(args.getString("localPassword"))
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(20000) {
                rule.onAllNodesWithTag("local-login-username").fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithTag("remote-photo-wall").fetchSemanticsNodes().isNotEmpty()
            }
            if(rule.onAllNodesWithTag("local-login-username").fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithTag("local-login-username").performTextReplacement(username)
                rule.onNodeWithTag("local-login-password").performTextReplacement(password)
                rule.onNodeWithTag("local-login-submit").performScrollTo().performClick()
            }
            waitForGallery()
        }
        // New Activity and ViewModel reload the encrypted CookieJar, not in-memory login state.
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForGallery()
            rule.onNodeWithTag("local-login-password").assertDoesNotExist()
        }
    }

    private fun waitForGallery() {
        rule.waitUntil(25000) { rule.onAllNodesWithTag("remote-photo-wall").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(25000) {
            rule.onAllNodes(SemanticsMatcher("Loaded gallery item") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("asset-") == true
            }).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("还没有照片").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        rule.onAllNodesWithTag("server-error").assertCountEquals(0)
    }
}
