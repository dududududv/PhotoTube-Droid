package com.yunai.phototube.data.memory

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.squareup.moshi.Moshi
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.remote.PhotoTubeApi
import com.yunai.phototube.data.remote.toApiFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException

class MemoryExclusionPagingSource(
    private val api: PhotoTubeApi,
    private val moshi: Moshi,
) : PagingSource<String, MemoryExclusion>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, MemoryExclusion> = try {
        val response = api.getMemoryExclusions(
            limit = params.loadSize.coerceIn(1, MAX_PAGE_SIZE),
            cursor = params.key,
        )
        if (!response.isSuccessful) {
            val failure: ApiFailure = response.toApiFailure(moshi)
            if (failure.error.code == "INVALID_CURSOR" && params.key != null) {
                LoadResult.Invalid()
            } else {
                LoadResult.Error(failure)
            }
        } else {
            val page = response.body()
                ?: return LoadResult.Error(IOException("PhotoTube 返回了空回忆屏蔽列表"))
            LoadResult.Page(
                data = page.items,
                prevKey = null,
                nextKey = page.nextCursor,
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        LoadResult.Error(failure)
    }

    override fun getRefreshKey(state: PagingState<String, MemoryExclusion>): String? = null

    private companion object {
        const val MAX_PAGE_SIZE = 500
    }
}
