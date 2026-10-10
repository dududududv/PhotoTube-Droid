package com.yunai.phototube.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

internal interface PrivateEndpoints {
    @POST("auth/private-access") suspend fun unlock(@Body body: RequestBody): Response<ResponseBody>
    @DELETE("auth/private-access") suspend fun lock(): Response<ResponseBody>
}
internal class PrivateRepository(private val server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(PrivateEndpoints::class.java)
    suspend fun unlock(password: String) {
        require(password.codePointCount(0, password.length) in 1..1024)
        expectNoContent(api.unlock(JSONObject().put("password", password).toString().toRequestBody("application/json".toMediaType())))
    }
    suspend fun lock() = expectNoContent(api.lock())
    private fun expectNoContent(response: Response<ResponseBody>) {
        checkPasswordRateLimit(response)
        if (!response.isSuccessful) response.json()
        response.body()?.close()
        if (response.code() != 204) throw ServerError("INVALID_RESPONSE", "私密授权响应未确认")
    }
    suspend fun expiry(): Instant {
        val session = server.session()
        if (!session.getBoolean("authenticated") || !session.getBoolean("privateAccessUnlocked")) throw ServerError("PRIVATE_ACCESS_REQUIRED", "请重新解锁私密空间")
        val expires = Instant.parse(session.getString("privateAccessExpiresAt"))
        if (!expires.isAfter(Instant.now())) throw ServerError("PRIVATE_ACCESS_REQUIRED", "私密授权已到期")
        return expires
    }
}
internal class PrivateLease(val server: PhotoTubeServer, val expiresAt: Instant, private val gate: AtomicBoolean) {
    fun revoke() { gate.set(false); server.client.dispatcher.cancelAll(); server.dispose() }
}

/** UI lifetime owns the lease. DELETE is serialized after any in-flight unlock, even on dismissal. */
internal class PrivateAccessController(private val server: PhotoTubeServer) {
    private val repository = PrivateRepository(server)
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val state = MutableStateFlow<PrivateLease?>(null)
    val lease = state.asStateFlow()
    private val lockError = MutableStateFlow<String?>(null)
    val lockMessage = lockError.asStateFlow()
    private var generation = 0
    private var closed = false
    private var verification: Job? = null
    suspend fun unlock(password: String) = withContext(Dispatchers.Main.immediate) {
        check(!closed)
        val requested = generation
        mutex.withLock {
            repository.unlock(password)
            val expires = repository.expiry()
            if (closed || generation != requested) return@withLock
            val gate = AtomicBoolean(true)
            val isolated = server.privateFork(gate::get) {
                gate.set(false)
                worker.launch { lock("私密授权已失效，请重新解锁") }
            }
            val value = PrivateLease(isolated, expires, gate)
            state.value?.revoke()
            state.value = value
            lockError.value = null
            verification = worker.launch {
                while (state.value === value) {
                    val remaining = java.time.Duration.between(Instant.now(), expires).toMillis()
                    if (remaining <= 0) { lock("私密授权已到期"); break }
                    delay(minOf(remaining, 30_000L))
                    try { repository.expiry() }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { lock("无法确认私密授权，请重新解锁"); break }
                }
            }
        }
    }
    fun lock(message: String? = null) {
        generation++
        state.value?.revoke()
        state.value = null
        verification?.cancel(); verification = null
        lockError.value = message
        worker.launch {
            mutex.withLock {
                try { repository.lock() }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { if (!closed) lockError.value = "本机已锁定，服务端锁定未确认；再次进入需重新验证密码" }
            }
        }
    }
    fun close() {
        if (closed) return
        closed = true
        lock()
        worker.launch { mutex.withLock { }; worker.cancel() }
    }
}
