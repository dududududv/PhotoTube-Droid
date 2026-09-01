package com.yunai.phototube.data.connection

import android.content.Context

interface ServerStore {
    fun getServerRoot(): ServerRoot?
    fun setServerRoot(serverRoot: ServerRoot)
    fun clear()
}

class ServerPreferences(context: Context) : ServerStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun getServerRoot(): ServerRoot? = preferences
        .getString(KEY_SERVER_ROOT, null)
        ?.let(ServerRoot::fromStored)

    override fun setServerRoot(serverRoot: ServerRoot) {
        preferences.edit().putString(KEY_SERVER_ROOT, serverRoot.value).apply()
    }

    override fun clear() {
        preferences.edit().remove(KEY_SERVER_ROOT).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "phototube_connection"
        const val KEY_SERVER_ROOT = "server_root"
    }
}
