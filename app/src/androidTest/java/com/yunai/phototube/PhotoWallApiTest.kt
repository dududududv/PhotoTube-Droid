package com.yunai.phototube

import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.*
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PhotoWallApiTest {
    private lateinit var host:MockWebServer
    private lateinit var api:PhotoTubeServer
    private lateinit var transport:OkHttpClient
    private val expired=AtomicInteger()
    @Before fun setup() {
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTLS=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTLS=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        host=MockWebServer().apply {useHttps(serverTLS.sslSocketFactory(),false);start()}
        transport=OkHttpClient.Builder().sslSocketFactory(clientTLS.sslSocketFactory(),clientTLS.trustManager).build()
        api=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").toString(),{expired.incrementAndGet()},transport)
        api.cookies.clear()
    }
    @After fun teardown() {api.cookies.clear();api.dispose();host.shutdown()}
    private fun json(value:String,code:Int=200)=MockResponse().setResponseCode(code).setHeader("Content-Type","application/json").setBody(value)
    private fun asset(id:String="10000000-0000-0000-0000-000000000001")=JSONObject().put("id",id).put("kind","PHOTO").put("state","PROCESSING")
        .put("takenAt","2026-09-01T00:00:00Z").put("takenAtOffsetMinutes",JSONObject.NULL).put("fileName","image.jpg")
        .put("contentHash",JSONObject.NULL).put("width",JSONObject.NULL).put("height",JSONObject.NULL).put("favorite",false)
    @Test fun healthAndSharedEncryptedSession()=runBlocking {
        host.enqueue(json("""{"status":"ok","database":{"reachable":true},"aiWorker":null,"xmpExport":{"available":false}}"""))
        api.health();Assert.assertEquals("/api/v1/health",host.takeRequest().path)
        host.enqueue(MockResponse().setResponseCode(204).setHeader("Set-Cookie","phototube_session=test-only-token; Path=/; HttpOnly; Max-Age=3600; Secure"))
        api.login("fixture-user","fixture-password");Assert.assertEquals("POST",host.takeRequest().method)
        host.enqueue(json("""{"authenticated":true,"passwordSet":true}"""))
        Assert.assertTrue(api.session().getBoolean("authenticated"))
        Assert.assertEquals("phototube_session=test-only-token",host.takeRequest().getHeader("Cookie"))
        val restored=SessionCookieJar(InstrumentationRegistry.getInstrumentation().targetContext,api.origin)
        Assert.assertEquals(1,restored.loadForRequest(api.origin).size)
        Assert.assertTrue(restored.loadForRequest(api.origin.newBuilder().port(api.origin.port+1).build()).isEmpty())
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        Assert.assertFalse(context.getSharedPreferences("server_session",0).all.values.any {it.toString().contains("test-only-token")})
    }
    @Test fun cursorPaginationIsOpaqueAndNullEndsList()=runBlocking {
        val token="opaque+/=cursor"
        host.enqueue(json(JSONObject().put("items",JSONArray().put(asset())).put("nextCursor",token).toString()))
        val source=AssetPagingSource(api)
        val first=source.load(PagingSource.LoadParams.Refresh(null,100,false)) as PagingSource.LoadResult.Page
        Assert.assertEquals(token,first.nextKey?.token)
        Assert.assertEquals("false",host.takeRequest().requestUrl!!.queryParameter("archived"))
        host.enqueue(json("""{"items":[],"nextCursor":null}"""))
        val second=source.load(PagingSource.LoadParams.Append(first.nextKey!!,100,false)) as PagingSource.LoadResult.Page
        Assert.assertNull(second.nextKey)
        Assert.assertEquals(token,host.takeRequest().requestUrl!!.queryParameter("cursor"))
        Assert.assertNull(second.prevKey!!.token)
        Assert.assertNull(api.thumbnail(first.data.single()))
    }
    @Test fun cursorInvalidationAndRateLimitStayDistinct()=runBlocking {
        host.enqueue(json("""{"code":"INVALID_CURSOR","message":"游标已失效"}""",400))
        val result=AssetPagingSource(api).load(PagingSource.LoadParams.Append(AssetCursor("old"),100,false))
        Assert.assertTrue(result is PagingSource.LoadResult.Invalid)
        host.enqueue(json("""{"code":"RATE_LIMITED","message":"稍后再试","logId":"fixture-log"}""",429))
        val error=AssetPagingSource(api).load(PagingSource.LoadParams.Refresh(null,100,false)) as PagingSource.LoadResult.Error
        Assert.assertEquals("RATE_LIMITED",(error.throwable as ServerError).code)
        Assert.assertEquals(2,host.requestCount)
    }
    @Test fun unauthorizedExpiresButBadPasswordDoesNot()=runBlocking {
        host.enqueue(json("""{"code":"INVALID_CREDENTIALS","message":"口令不正确"}""",401))
        try {api.login("fixture","wrong");Assert.fail()} catch(_:ServerError) {}
        Assert.assertEquals(0,expired.get())
        host.enqueue(json("""{"code":"UNAUTHORIZED","message":"请登录"}""",401))
        try {api.assets(null);Assert.fail()} catch(_:ServerError) {}
        Assert.assertEquals(1,expired.get())
    }
    @Test fun thumbnailIsVersionedAndPendingRetryIsBounded() {
        val media=RemoteAsset("abc","PHOTO","BROWSABLE","2026-09-01T00:00:00Z",null,"image.jpg","version hash",640,480,false,null)
        val url=api.thumbnail(media)!!
        repeat(3) {host.enqueue(json("""{"code":"DERIVATIVE_PENDING","message":"处理中"}""",503).setHeader("Retry-After","0"))}
        api.client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use {Assert.assertEquals(503,it.code)}
        Assert.assertEquals(3,host.requestCount)
        val request=host.takeRequest().requestUrl!!
        Assert.assertEquals("version hash",request.queryParameter("v"))
        Assert.assertEquals("MD",request.queryParameter("size"))
    }
    @Test fun groupingDoesNotUseDeviceTimezone() {
        val groups=listOf(MonthGroup("2026-09","2026-09-01T00:00:00+08:00",2),MonthGroup("2026-08","2026-08-01T00:00:00+08:00",1))
        val media=RemoteAsset("x","PHOTO","BROWSABLE","2026-08-31T18:00:00Z",null,"x",null,null,null,false,null)
        Assert.assertEquals("2026-09",monthKey(media,groups))
        Assert.assertEquals("2026-08",monthKey(media.copy(takenAtOffsetMinutes=-420),groups))
        Assert.assertEquals("https://example.com",PhotoTubeServer.normalize("https://example.com/"))
        Assert.assertTrue(runCatching {PhotoTubeServer.normalize("https://example.com/api/v1")}.isFailure)
    }
}
