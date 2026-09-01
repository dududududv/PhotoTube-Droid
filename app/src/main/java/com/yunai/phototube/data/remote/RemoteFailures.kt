package com.yunai.phototube.data.remote

import com.squareup.moshi.Moshi
import retrofit2.Response

class ApiFailure(
    val httpStatus: Int,
    val error: ApiErrorBody,
) : Exception(error.message)

fun Throwable.isInvalidCursorFailure(cursor: String?): Boolean =
    cursor != null && this is ApiFailure && error.code == "INVALID_CURSOR"

fun Response<*>.toApiFailure(moshi: Moshi): ApiFailure {
    val rawError = errorBody()?.string()
    val parsed = rawError?.let { body ->
        runCatching { moshi.adapter(ApiErrorBody::class.java).fromJson(body) }.getOrNull()
    }
    return ApiFailure(
        httpStatus = code(),
        error = parsed ?: ApiErrorBody(
            code = "HTTP_${code()}",
            message = "PhotoTube 请求失败（HTTP ${code()}）",
            retryable = code() >= 500,
            logId = "",
        ),
    )
}
