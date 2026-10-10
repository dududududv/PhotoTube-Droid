package com.yunai.phototube.ui.remote

import android.view.View
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CompletableDeferred

/** Start the source-cover transition after the new window has submitted its first frame. */
internal suspend fun awaitAlbumFirstFrame(view: View) {
    if (!view.isHardwareAccelerated) {
        // Frame-commit callbacks are unavailable to software-rendered test hosts.
        withFrameNanos { }
        return
    }
    val observer = view.viewTreeObserver
    val committed = CompletableDeferred<Unit>()
    val callback = Runnable { committed.complete(Unit) }
    observer.registerFrameCommitCallback(callback)
    view.postInvalidateOnAnimation()
    try {
        committed.await()
    } finally {
        // Closing before the first frame must not retain the dialog or start a
        // stale entrance animation after its exit has already begun.
        if (observer.isAlive) observer.unregisterFrameCommitCallback(callback)
    }
}
