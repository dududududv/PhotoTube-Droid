package com.yunai.phototube.data.timeline

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.squareup.moshi.Moshi
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.remote.PhotoTubeApi
import com.yunai.phototube.data.remote.toApiFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException

class TimelinePagingSource(
    private val api: PhotoTubeApi,
    private val moshi: Moshi,
    private val filter: AssetFilter,
) : PagingSource<String, MediaAsset>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, MediaAsset> = try {
        val response = api.getAssets(
            limit = params.loadSize.coerceIn(1, MAX_PAGE_SIZE),
            cursor = params.key,
            filters = filter.toQueryMap(),
        )
        if (!response.isSuccessful) {
            val failure = response.toApiFailure(moshi)
            if (failure.error.code == "INVALID_CURSOR" && params.key != null) {
                LoadResult.Invalid()
            } else {
                LoadResult.Error(failure)
            }
        } else {
            val page = response.body()
                ?: return LoadResult.Error(IOException("PhotoTube 返回了空时间轴响应"))
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

    override fun getRefreshKey(state: PagingState<String, MediaAsset>): String? = null

    private companion object {
        const val MAX_PAGE_SIZE = 500
    }
}
