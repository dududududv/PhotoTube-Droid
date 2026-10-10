package com.yunai.phototube.ui.state

import androidx.compose.runtime.Composable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow

/** Cached cursor window owned by a route; factories must only capture data-layer objects. */
private class ConfigurationPaging<T : Any>(source: Flow<PagingData<T>>) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val flow = source.cachedIn(scope)
    override fun close() { scope.cancel() }
}

@Composable
internal fun <T : Any> rememberConfigurationPaging(
    owner: Any?, enabled: Boolean = true, factory: () -> Flow<PagingData<T>>,
): Flow<PagingData<T>> = rememberConfigurationState(owner, enabled) { ConfigurationPaging(factory()) }.flow
