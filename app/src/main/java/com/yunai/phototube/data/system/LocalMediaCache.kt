package com.yunai.phototube.data.system

import coil3.ImageLoader
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory

/**
 * 唯一的认证媒体缓存边界。主动清理、私密锁定、登出与切换服务器都必须复用这里，
 * 避免只清一层后让受保护媒体继续从另一层命中。
 */
class LocalMediaCache(
    private val imageLoader: ImageLoader,
    private val serviceFactory: PhotoTubeServiceFactory,
) {
    fun clearAll() {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        serviceFactory.clearHttpCache()
    }

    fun snapshot() = LocalCacheSnapshot(
        memoryBytes = imageLoader.memoryCache?.size?.toLong() ?: 0L,
        memoryMaxBytes = imageLoader.memoryCache?.maxSize?.toLong() ?: 0L,
        imageDiskBytes = imageLoader.diskCache?.size ?: 0L,
        httpDiskBytes = serviceFactory.httpCacheSize(),
        httpDiskMaxBytes = serviceFactory.httpCacheMaxSize,
    )
}
