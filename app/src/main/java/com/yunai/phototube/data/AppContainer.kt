package com.yunai.phototube.data

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.yunai.phototube.data.asset.AssetRepository
import com.yunai.phototube.data.album.AlbumRepository
import com.yunai.phototube.data.connection.ServerPreferences
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.session.EncryptedCookieJar
import com.yunai.phototube.data.session.SessionEventBus
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.timeline.TimelineRepository
import com.yunai.phototube.data.tag.TagRepository
import com.yunai.phototube.data.trash.TrashRepository
import com.yunai.phototube.data.job.JobRepository
import com.yunai.phototube.data.duplicate.DuplicateRepository
import com.yunai.phototube.data.home.HomeRepository
import com.yunai.phototube.data.edit.EditRepository
import com.yunai.phototube.data.folder.FolderRepository
import com.yunai.phototube.data.xmp.XmpExportRepository
import com.yunai.phototube.data.system.SystemRepository
import com.yunai.phototube.data.system.LocalMediaCache
import com.yunai.phototube.data.memory.MemoryExclusionRepository

class AppContainer(context: Context) {
    private val cookieJar = EncryptedCookieJar(context)
    val sessionEventBus = SessionEventBus()
    val serviceFactory = PhotoTubeServiceFactory(cookieJar, sessionEventBus, context.cacheDir)
    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components {
            add(
                OkHttpNetworkFetcherFactory(
                    callFactory = { serviceFactory.sharedHttpClient },
                ),
            )
        }
        .build()
    private val localMediaCache = LocalMediaCache(imageLoader, serviceFactory)
    val sessionRepository = SessionRepository(
        serverPreferences = ServerPreferences(context),
        cookieJar = cookieJar,
        serviceFactory = serviceFactory,
        onSessionCleared = localMediaCache::clearAll,
        onPrivateAccessLocked = localMediaCache::clearAll,
    )
    val timelineRepository = TimelineRepository(sessionRepository, serviceFactory)
    val assetRepository = AssetRepository(
        sessionRepository = sessionRepository,
        serviceFactory = serviceFactory,
        onProtectedMediaInvalidated = localMediaCache::clearAll,
    )
    val albumRepository = AlbumRepository(sessionRepository, serviceFactory)
    val tagRepository = TagRepository(sessionRepository, serviceFactory)
    val trashRepository = TrashRepository(sessionRepository, serviceFactory)
    val jobRepository = JobRepository(sessionRepository, serviceFactory)
    val duplicateRepository = DuplicateRepository(sessionRepository, serviceFactory)
    val homeRepository = HomeRepository(sessionRepository, serviceFactory)
    val editRepository = EditRepository(sessionRepository, serviceFactory)
    val folderRepository = FolderRepository(sessionRepository, serviceFactory)
    val xmpExportRepository = XmpExportRepository(sessionRepository, serviceFactory)
    val systemRepository = SystemRepository(sessionRepository, serviceFactory, localMediaCache)
    val memoryExclusionRepository = MemoryExclusionRepository(sessionRepository, serviceFactory)
}
