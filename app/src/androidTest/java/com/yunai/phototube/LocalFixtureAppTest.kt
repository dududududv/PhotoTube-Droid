package com.yunai.phototube

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.LocalMockFixture
import com.yunai.phototube.data.PhotoWallViewModel
import com.yunai.phototube.ui.PhotoTubeContent
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule

/** Fixtures are injected into a test Activity, never selected by the installed app. */
abstract class LocalFixtureAppTest {
    @get:Rule val rule = createAndroidComposeRule<UiTestActivity>()
    private lateinit var fixture: LocalMockFixture
    private lateinit var wall: PhotoWallViewModel
    @Before fun setUpFixture() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        fixture = LocalMockFixture(app)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PhotoWallViewModel(app, { _, expired -> fixture.server(expired) }, "test_gallery_fixture")
                    .also { wall = it } as T
        }
        rule.runOnUiThread { UiTestActivity.content = { PhotoTubeTheme { PhotoTubeContent(viewModel(factory = factory)) } } }
        rule.waitForIdle()
        rule.runOnIdle { wall.connect(fixture.address) }
    }
    @After fun tearDownFixture() {
        rule.runOnUiThread { UiTestActivity.content = null; rule.activity.viewModelStore.clear() }
        fixture.close()
    }
}
