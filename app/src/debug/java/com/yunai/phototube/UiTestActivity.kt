package com.yunai.phototube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*

/** Isolated UI host: never connects to the saved production server. Debug builds only. */
class UiTestActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) {
        val fixture = fontScaleOverride
        val context = if (fixture == null) base else base.createConfigurationContext(
            android.content.res.Configuration(base.resources.configuration).apply { fontScale = fixture })
        super.attachBaseContext(context)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { content?.invoke() }
    }
    companion object {
        var fontScaleOverride: Float? = null
        var content: (@Composable () -> Unit)? by mutableStateOf(null)
    }
}
