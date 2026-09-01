package com.yunai.phototube.data.xmp

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.squareup.moshi.Moshi
import com.yunai.phototube.data.remote.PhotoTubeApi
import com.yunai.phototube.data.remote.toApiFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException

class XmpExportPagingSource(
    private val api: PhotoTubeApi,
    private val moshi: Moshi,
) : PagingSource<String, XmpExportRun>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, XmpExportRun> = try {
        val response = api.getXmpExportRuns(
            limit = params.loadSize.coerceIn(1, MAX_PAGE_SIZE),
            cursor = params.key,
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
                ?: return LoadResult.Error(IOException("PhotoTube 返回了空 XMP 导出历史"))
            LoadResult.Page(data = page.items, prevKey = null, nextKey = page.nextCursor)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        LoadResult.Error(failure)
    }

    override fun getRefreshKey(state: PagingState<String, XmpExportRun>): String? = null

    private companion object {
        const val MAX_PAGE_SIZE = 500
    }
}
