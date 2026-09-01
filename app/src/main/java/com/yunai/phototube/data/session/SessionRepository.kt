package com.yunai.phototube.data.session

import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.connection.ServerStore
import com.yunai.phototube.data.remote.HealthResponse
import com.yunai.phototube.data.remote.LoginRequest
import com.yunai.phototube.data.remote.PrivateAccessRequest
import com.yunai.phototube.data.remote.PhotoTubeApi
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.SessionInfo
import com.yunai.phototube.data.remote.toApiFailure
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response

interface AppSessionGateway {
    fun currentServer(): ServerRoot?
    suspend fun bootstrap(): BootstrapResult
    suspend fun connect(rawAddress: String): BootstrapResult.Session
    suspend fun login(username: String, password: String): BootstrapResult.Session
    suspend fun logout()
    suspend fun switchServer(): ServerSwitchResult
    fun clearExpiredSession()
    fun forgetServer()
}

interface PrivateAccessGateway {
    suspend fun getSessionInfo(): SessionInfo
    suspend fun unlockPrivateAccess(password: String): SessionInfo
    suspend fun lockPrivateAccess()
}

class SessionRepository(
    private val serverPreferences: ServerStore,
    private val cookieJar: ClearableCookieJar,
    private val serviceFactory: PhotoTubeServiceFactory,
    private val onSessionCleared: () -> Unit = {},
    private val onPrivateAccessLocked: () -> Unit = {},
) : AppSessionGateway, PrivateAccessGateway {
    private val privateAccessGate = PrivateAccessOperationGate()

    override fun currentServer(): ServerRoot? = serverPreferences.getServerRoot()

    override suspend fun bootstrap(): BootstrapResult {
        val serverRoot = serverPreferences.getServerRoot() ?: return BootstrapResult.NeedsServer
        val session = serviceFactory.create(serverRoot).getSession().requireBody()
        return BootstrapResult.Session(serverRoot, session)
    }

    override suspend fun connect(rawAddress: String): BootstrapResult.Session {
        val serverRoot = ServerRoot.parse(rawAddress).getOrElse { throw it }
        if (serverPreferences.getServerRoot() != serverRoot) clearLocalSession()
        val api = serviceFactory.create(serverRoot)
        verifyHealth(api.getHealth().requireBody())

        serverPreferences.setServerRoot(serverRoot)
        return BootstrapResult.Session(serverRoot, api.getSession().requireBody())
    }

    override suspend fun login(username: String, password: String): BootstrapResult.Session {
        val serverRoot = requireNotNull(serverPreferences.getServerRoot()) {
            "尚未配置 PhotoTube 服务地址"
        }
        val api = serviceFactory.create(serverRoot)
        api.login(LoginRequest(username.trim(), password)).requireSuccess()
        val session = api.getSession().requireBody()
        if (!session.authenticated || session.user == null) {
            throw IncompatibleServerException("登录成功后没有取得有效会话")
        }
        return BootstrapResult.Session(serverRoot, session)
    }

    override suspend fun logout() {
        val serverRoot = serverPreferences.getServerRoot()
        if (serverRoot != null) serviceFactory.create(serverRoot).logout().requireSuccess()
        clearLocalSession()
    }

    /**
     * 切换服务器不能被旧服务器离线阻断。本机隔离边界始终执行；返回值只表示
     * 旧服务器是否确认删除了会话行，调用方必须据此给出诚实提示。
     */
    override suspend fun switchServer(): ServerSwitchResult {
        val serverRoot = serverPreferences.getServerRoot()
        val serverLogoutConfirmed = serverRoot == null || runCatching {
            serviceFactory.create(serverRoot).logout().requireSuccess()
        }.isSuccess
        try {
            clearLocalSession()
        } finally {
            serverPreferences.clear()
        }
        return ServerSwitchResult(serverLogoutConfirmed)
    }

    override suspend fun getSessionInfo(): SessionInfo {
        val serverRoot = requireNotNull(serverPreferences.getServerRoot()) {
            "尚未配置 PhotoTube 服务地址"
        }
        return serviceFactory.create(serverRoot).getSession().requireBody()
    }

    override suspend fun unlockPrivateAccess(password: String): SessionInfo {
        require(password.isNotEmpty()) { "请输入当前账号口令" }
        return privateAccessGate.unlock {
            val serverRoot = requireNotNull(serverPreferences.getServerRoot()) {
                "尚未配置 PhotoTube 服务地址"
            }
            val api = serviceFactory.create(serverRoot)
            api.unlockPrivateAccess(PrivateAccessRequest(password)).requireSuccess()
            api.getSession().requireBody().also { session ->
                check(session.privateAccessUnlocked) { "PhotoTube 没有确认私密访问已解锁" }
            }
        }
    }

    override suspend fun lockPrivateAccess() {
        val serverRoot = serverPreferences.getServerRoot()
        try {
            privateAccessGate.lock {
                if (serverRoot != null) {
                    serviceFactory.create(serverRoot).lockPrivateAccess().requireSuccess()
                }
            }
        } finally {
            onPrivateAccessLocked()
        }
    }

    override fun clearExpiredSession() {
        clearLocalSession()
    }

    override fun forgetServer() {
        clearLocalSession()
        serverPreferences.clear()
    }

    private fun clearLocalSession() {
        cookieJar.clear()
        onSessionCleared()
    }

    private fun verifyHealth(health: HealthResponse) {
        if (health.status !in setOf("ok", "degraded")) {
            throw IncompatibleServerException("地址可达，但响应不是兼容的 PhotoTube 服务")
        }
        if (!health.database.reachable) {
            throw IncompatibleServerException("PhotoTube 数据库当前不可用")
        }
        if (health.database.dirty) {
            throw IncompatibleServerException("PhotoTube 数据库迁移处于异常状态，请先在服务端处理")
        }
    }

    private fun Response<Unit>.requireSuccess() {
        if (!isSuccessful) throw toApiFailure(serviceFactory.moshi)
    }

    private fun <T : Any> Response<T>.requireBody(): T {
        if (!isSuccessful) throw toApiFailure(serviceFactory.moshi)
        return body() ?: throw IncompatibleServerException("PhotoTube 返回了空响应")
    }
}

internal class PrivateAccessOperationGate {
    private val mutex = Mutex()
    private val lockEpoch = AtomicLong(0)

    suspend fun <T> unlock(operation: suspend () -> T): T {
        val submittedEpoch = lockEpoch.get()
        return mutex.withLock {
            check(submittedEpoch == lockEpoch.get()) { "私密解锁请求已失效" }
            operation()
        }
    }

    suspend fun lock(operation: suspend () -> Unit) {
        lockEpoch.incrementAndGet()
        mutex.withLock { operation() }
    }
}

sealed interface BootstrapResult {
    data object NeedsServer : BootstrapResult

    data class Session(
        val serverRoot: ServerRoot,
        val sessionInfo: SessionInfo,
    ) : BootstrapResult
}

class IncompatibleServerException(message: String) : Exception(message)

data class ServerSwitchResult(
    val serverLogoutConfirmed: Boolean,
)
