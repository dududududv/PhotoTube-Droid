package com.yunai.phototube.ui

import kotlinx.coroutines.CancellationException

internal fun Throwable.rethrowCancellation() {
    if (this is CancellationException) throw this
}
