package com.yunai.phototube.data.duplicate

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.squareup.moshi.Moshi
import com.yunai.phototube.data.isSha256ContentHash
import com.yunai.phototube.data.remote.PhotoTubeApi
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.timeline.MediaAsset
import java.io.IOException
import kotlinx.coroutines.CancellationException

class DuplicateGroupPagingSource(
    private val api: PhotoTubeApi,
    private val moshi: Moshi,
    private val query: DuplicateQuery,
) : PagingSource<String, DuplicateGroup>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, DuplicateGroup> = try {
        val response = api.getDuplicateGroups(
            limit = params.loadSize.coerceIn(1, MAX_PAGE_SIZE),
            cursor = params.key,
            includeReviewed = query.includeReviewed,
            privateScope = query.privateScope,
        )
        if (!response.isSuccessful) response.toPagingFailure<DuplicateGroup>(moshi, params.key)
        else {
            val page = response.body() ?: return LoadResult.Error(IOException("PhotoTube 返回了空重复组列表"))
            LoadResult.Page(page.items, prevKey = null, nextKey = page.nextCursor)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        LoadResult.Error(failure)
    }

    override fun getRefreshKey(state: PagingState<String, DuplicateGroup>): String? = null
}

class DuplicateAssetPagingSource(
    private val api: PhotoTubeApi,
    private val moshi: Moshi,
    private val contentHash: String,
    private val privateScope: Boolean,
) : PagingSource<String, MediaAsset>() {
    init {
        require(contentHash.isSha256ContentHash()) { "重复组 contentHash 必须是 64 位小写 SHA-256" }
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, MediaAsset> = try {
        val response = api.getDuplicateAssets(
            contentHash = contentHash,
            limit = params.loadSize.coerceIn(1, MAX_PAGE_SIZE),
            cursor = params.key,
            privateScope = privateScope,
        )
        if (!response.isSuccessful) response.toPagingFailure<MediaAsset>(moshi, params.key)
        else {
            val page = response.body() ?: return LoadResult.Error(IOException("PhotoTube 返回了空重复组成员"))
            LoadResult.Page(page.items, prevKey = null, nextKey = page.nextCursor)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        LoadResult.Error(failure)
    }

    override fun getRefreshKey(state: PagingState<String, MediaAsset>): String? = null
}

private fun <V : Any> retrofit2.Response<*>.toPagingFailure(
    moshi: Moshi,
    cursor: String?,
): PagingSource.LoadResult<String, V> {
    val failure = toApiFailure(moshi)
    return if (failure.error.code == "INVALID_CURSOR" && cursor != null) {
        PagingSource.LoadResult.Invalid()
    } else {
        PagingSource.LoadResult.Error(failure)
    }
}

private const val MAX_PAGE_SIZE = 500
