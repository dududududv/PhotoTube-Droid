package com.yunai.phototube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yunai.phototube.ui.PhotoTubeApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Purge only local remnants; never contact an old account or server.
        getSharedPreferences("server_connection",MODE_PRIVATE).edit().clear().commit()
        getSharedPreferences("server_session",MODE_PRIVATE).edit().clear().commit()
        cacheDir.deleteRecursively()
        externalCacheDir?.deleteRecursively()
        enableEdgeToEdge()
        setContent { PhotoTubeApp() }
    }
}
