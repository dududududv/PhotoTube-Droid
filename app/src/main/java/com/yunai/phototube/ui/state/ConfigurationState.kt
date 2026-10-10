package com.yunai.phototube.ui.state

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID

/** In-memory state only. Entries outlive a configuration change, never the owning route. */
internal class ConfigurationStateStore : ViewModel() {
    private val entries = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrCreate(key: String, factory: () -> T): T = entries.getOrPut(key, factory) as T

    fun release(key: String) { (entries.remove(key) as? AutoCloseable)?.close() }
    fun clear() { entries.keys.toList().forEach(::release) }
    override fun onCleared() { clear() }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Values must not capture an Activity, a View, or UI callbacks. Private data opts out. */
@Composable
internal fun <T : Any> rememberConfigurationState(owner: Any?, enabled: Boolean = true, factory: () -> T): T {
    if (!enabled) {
        val value = remember(owner, factory.javaClass) { factory() }
        DisposableEffect(value) { onDispose { (value as? AutoCloseable)?.close() } }
        return value
    }
    val store: ConfigurationStateStore = viewModel()
    val activity = LocalContext.current.activity()
    val key = rememberSaveable(owner) { UUID.randomUUID().toString() }
    val value = remember(store, key) { store.getOrCreate(key, factory) }
    DisposableEffect(store, key, activity) {
        onDispose { if (activity?.isChangingConfigurations != true) store.release(key) }
    }
    return value
}
