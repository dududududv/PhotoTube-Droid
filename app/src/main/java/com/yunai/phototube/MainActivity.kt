package com.yunai.phototube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import com.yunai.phototube.data.AppContainer
import com.yunai.phototube.ui.PhotoTubeApp
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.theme.PhotoTubeDroidTheme

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SingletonImageLoader.setSafe { appContainer.imageLoader }
        val systemBarColor = PhotoTubeColors.Background.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
        )
        setContent {
            PhotoTubeDroidTheme {
                PhotoTubeApp(appContainer, this)
            }
        }
    }
}
