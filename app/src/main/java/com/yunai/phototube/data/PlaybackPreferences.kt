package com.yunai.phototube.data

import android.content.Context

/** Device-local browsing preferences, independent of server and media identity. */
internal class PlaybackPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("playback", Context.MODE_PRIVATE)
    var autoPlay: Boolean
        get() = prefs.getBoolean("auto_play", false)
        set(value) { prefs.edit().putBoolean("auto_play", value).apply() }
    var muted: Boolean
        get() = prefs.getBoolean("muted", true)
        set(value) { prefs.edit().putBoolean("muted", value).apply() }
    var volume: Float
        get() = prefs.getFloat("volume", 1f).coerceIn(0f, 1f)
        set(value) { prefs.edit().putFloat("volume", value.coerceIn(0f, 1f)).apply() }
}
