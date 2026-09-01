package com.yunai.phototube.ui.viewer

import androidx.media3.datasource.HttpDataSource

internal data class VideoPlaybackFailure(
    val message: String,
    val retryFromStart: Boolean = false,
)

internal fun videoPlaybackFailureForHttpCode(responseCode: Int?): VideoPlaybackFailure = when (responseCode) {
    401 -> VideoPlaybackFailure("视频会话已失效，请重新登录")
    403 -> VideoPlaybackFailure("当前账号无权读取这个视频")
    404 -> VideoPlaybackFailure("视频原文件当前不可用")
    416 -> VideoPlaybackFailure(
        message = "播放位置已超出媒体范围，可以从头重新请求",
        retryFromStart = true,
    )
    503 -> VideoPlaybackFailure("视频仍在准备中，请稍后重试")
    null -> VideoPlaybackFailure("视频加载失败，请检查网络后重试")
    else -> VideoPlaybackFailure("视频加载失败（HTTP $responseCode）")
}

internal fun Throwable.videoHttpResponseCode(): Int? {
    var failure: Throwable? = this
    while (failure != null) {
        if (failure is HttpDataSource.InvalidResponseCodeException) return failure.responseCode
        failure = failure.cause
    }
    return null
}
