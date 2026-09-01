package com.yunai.phototube.data.album

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.squareup.moshi.Moshi
import com.yunai.phototube.data.remote.PhotoTubeApi
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.timeline.MediaAsset
import java.io.IOException
import kotlinx.coroutines.CancellationException

class AlbumPagingSource(
    private val api: PhotoTubeApi,
    private val moshi: Moshi,
) : PagingSource<String, Album>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, Album> = try {
        val response = api.getAlbums(
            limit = params.loadSize.coerceIn(1, MAX_PAGE_SIZE),
            cursor = params.key,
        )
        if (!response.isSuccessful) {
            response.asPagingFailure(moshi, params.key)
        } else {
            val page = response.body()
                ?: return LoadResult.Error(IOException("PhotoTube 返回了空相册响应"))
            LoadResult.Page(page.items, prevKey = null, nextKey = page.nextCursor)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        LoadResult.Error(failure)
    }

    override fun getRefreshKey(state: PagingState<String, Album>): String? = null
}

class AlbumAssetPagingSource(
    private val albumId: String,
    private val api: PhotoTubeApi,
    private val moshi: Moshi,
) : PagingSource<String, MediaAsset>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, MediaAsset> = try {
        val response = api.getAlbumAssets(
            albumId = albumId,
            limit = params.loadSize.coerceIn(1, MAX_PAGE_SIZE),
            cursor = params.key,
        )
        if (!response.isSuccessful) {
            response.asPagingFailure(moshi, params.key)
        } else {
            val listing = response.body()
                ?: return LoadResult.Error(IOException("PhotoTube 返回了空相册资产响应"))
            LoadResult.Page(listing.assets.items, prevKey = null, nextKey = listing.assets.nextCursor)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        LoadResult.Error(failure)
    }

    override fun getRefreshKey(state: PagingState<String, MediaAsset>): String? = null
}

class AlbumPathSyncRunPagingSource(
    private val albumId: String,
    private val api: PhotoTubeApi,
    private val moshi: Moshi,
) : PagingSource<String, AlbumPathSyncRun>() {
    init {
        require(albumId.isNotBlank()) { "相册 ID 不能为空" }
    }

    override suspend fun load(
        params: LoadParams<String>,
    ): LoadResult<String, AlbumPathSyncRun> = try {
        val response = api.getAlbumPathSyncRuns(
            albumId = albumId,
            limit = params.loadSize.coerceIn(1, MAX_PAGE_SIZE),
            cursor = params.key,
        )
        if (!response.isSuccessful) {
            response.asPagingFailure(moshi, params.key)
        } else {
            val page = response.body()
                ?: return LoadResult.Error(IOException("PhotoTube 返回了空同步历史响应"))
            LoadResult.Page(page.items, prevKey = null, nextKey = page.nextCursor)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        LoadResult.Error(failure)
    }

    override fun getRefreshKey(state: PagingState<String, AlbumPathSyncRun>): String? = null
}

private fun <Key : Any, Value : Any> retrofit2.Response<*>.asPagingFailure(
    moshi: Moshi,
    cursor: String?,
): PagingSource.LoadResult<Key, Value> {
    val failure = toApiFailure(moshi)
    return if (failure.error.code == "INVALID_CURSOR" && cursor != null) {
        PagingSource.LoadResult.Invalid()
    } else {
        PagingSource.LoadResult.Error(failure)
    }
}

private const val MAX_PAGE_SIZE = 500
