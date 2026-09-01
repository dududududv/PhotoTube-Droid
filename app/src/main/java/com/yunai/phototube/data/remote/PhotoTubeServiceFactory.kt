package com.yunai.phototube.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.album.UpdateAlbumRequestJsonAdapter
import com.yunai.phototube.data.edit.EditRequestJsonAdapter
import com.yunai.phototube.data.session.SessionEventBus
import java.io.File
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class PhotoTubeServiceFactory(
    cookieJar: CookieJar,
    private val sessionEventBus: SessionEventBus,
    cacheDirectory: File,
) {
    val moshi: Moshi = Moshi.Builder()
        .add(RatingByIdsRequestJsonAdapter)
        .add(EditRequestJsonAdapter)
        .add(UpdateAlbumRequestJsonAdapter)
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val httpCache = Cache(File(cacheDirectory, "authenticated-http"), HTTP_CACHE_BYTES)

    val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .cache(httpCache)
        .addInterceptor(DerivativeRetryInterceptor())
        .addInterceptor(unauthorizedInterceptor())
        .build()

    fun create(serverRoot: ServerRoot): PhotoTubeApi = Retrofit.Builder()
        .baseUrl(serverRoot.apiBaseUrl)
        .client(sharedHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(PhotoTubeApi::class.java)

    fun clearHttpCache() {
        httpCache.evictAll()
    }

    fun httpCacheSize(): Long = httpCache.size()

    val httpCacheMaxSize: Long
        get() = HTTP_CACHE_BYTES

    private fun unauthorizedInterceptor() = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.code == 401 && !chain.request().url.encodedPath.endsWith("/auth/login")) {
            val body = response.peekBody(MAX_ERROR_BODY_BYTES).string()
            val code = runCatching {
                moshi.adapter(ApiErrorBody::class.java).fromJson(body)?.code
            }.getOrNull()
            if (code == "UNAUTHORIZED") sessionEventBus.notifyUnauthorized()
        }
        response
    }

    private companion object {
        const val MAX_ERROR_BODY_BYTES = 64L * 1024L
        const val HTTP_CACHE_BYTES = 128L * 1024L * 1024L
    }
}
