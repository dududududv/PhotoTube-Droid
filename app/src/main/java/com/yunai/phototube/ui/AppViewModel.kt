package com.yunai.phototube.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.JsonDataException
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.SessionUser
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.session.BootstrapResult
import com.yunai.phototube.data.session.AppSessionGateway
import com.yunai.phototube.data.session.IncompatibleServerException
import com.yunai.phototube.data.session.SessionEventBus
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    private val sessionRepository: AppSessionGateway,
    sessionEventBus: SessionEventBus,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = mutableUiState.asStateFlow()
    private var sessionGeneration = 0L

    init {
        val handledPendingUnauthorized = sessionEventBus.consumeUnauthorized()
        if (handledPendingUnauthorized) handleUnauthorized()
        viewModelScope.launch {
            sessionEventBus.unauthorizedPending.filter { it }.collect {
                if (sessionEventBus.consumeUnauthorized()) handleUnauthorized()
            }
        }
        if (!handledPendingUnauthorized) refreshSession()
    }

    fun onServerAddressChanged(value: String) {
        mutableUiState.update { it.copy(serverAddress = value, error = null) }
    }

    fun onUsernameChanged(value: String) {
        mutableUiState.update { it.copy(username = value, error = null) }
    }

    fun onPasswordChanged(value: String) {
        mutableUiState.update { it.copy(password = value, error = null) }
    }

    fun connect() = launchOperation {
        sessionRepository.connect(mutableUiState.value.serverAddress)
    }

    fun login() {
        val state = mutableUiState.value
        if (state.username.isBlank() || state.password.isEmpty()) {
            mutableUiState.update { it.copy(error = UiError("请输入用户名和口令")) }
            return
        }
        launchOperation { sessionRepository.login(state.username, state.password) }
    }

    fun retry() = refreshSession()

    fun chooseAnotherServer() {
        sessionRepository.forgetServer()
        mutableUiState.value = AppUiState(stage = AppStage.ServerSetup)
    }

    fun logout() {
        if (mutableUiState.value.isBusy) return
        mutableUiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { sessionRepository.logout() }
                .onSuccess {
                    mutableUiState.update {
                        it.copy(
                            stage = AppStage.Login,
                            isBusy = false,
                            error = null,
                            password = "",
                            user = null,
                        )
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    mutableUiState.update {
                        it.copy(isBusy = false, error = failure.toUiError())
                    }
                }
        }
    }

    fun switchServer() {
        if (mutableUiState.value.isBusy) return
        mutableUiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { sessionRepository.switchServer() }
                .onSuccess { result ->
                    mutableUiState.value = AppUiState(
                        stage = AppStage.ServerSetup,
                        error = if (result.serverLogoutConfirmed) null else {
                            UiError("本机会话与缓存已清除，但旧服务器没有确认登出")
                        },
                    )
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    mutableUiState.value = AppUiState(
                        stage = AppStage.ServerSetup,
                        error = UiError("服务器已切换，但本机认证媒体缓存未能完整清除，请重试清理"),
                    )
                }
        }
    }

    private fun refreshSession() {
        val currentServer = sessionRepository.currentServer()
        if (currentServer != null) {
            mutableUiState.update {
                it.copy(serverRoot = currentServer, serverAddress = currentServer.value)
            }
        }
        launchOperation { sessionRepository.bootstrap() }
    }

    private fun launchOperation(block: suspend () -> BootstrapResult) {
        if (mutableUiState.value.isBusy) return
        val operationGeneration = sessionGeneration
        mutableUiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { result ->
                    if (operationGeneration == sessionGeneration) applyBootstrapResult(result)
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (operationGeneration != sessionGeneration) return@onFailure
                    mutableUiState.update {
                        val targetStage = if (it.serverRoot == null) AppStage.ServerSetup else it.stage
                        it.copy(
                            stage = if (targetStage == AppStage.Loading) AppStage.ServerSetup else targetStage,
                            isBusy = false,
                            error = failure.toUiError(),
                        )
                    }
                }
        }
    }

    private fun handleUnauthorized() {
        val state = mutableUiState.value
        if (state.stage == AppStage.Login && state.user == null && state.error?.message == SESSION_EXPIRED) {
            return
        }
        sessionGeneration += 1
        sessionRepository.clearExpiredSession()
        mutableUiState.update {
            it.copy(
                stage = AppStage.Login,
                isBusy = false,
                error = UiError(SESSION_EXPIRED),
                user = null,
                password = "",
            )
        }
    }

    private fun applyBootstrapResult(result: BootstrapResult) {
        when (result) {
            BootstrapResult.NeedsServer -> mutableUiState.value = AppUiState(stage = AppStage.ServerSetup)
            is BootstrapResult.Session -> {
                val info = result.sessionInfo
                mutableUiState.update {
                    it.copy(
                        stage = when {
                            info.authenticated -> AppStage.Content
                            info.passwordSet -> AppStage.Login
                            else -> AppStage.PasswordNotInitialized
                        },
                        serverRoot = result.serverRoot,
                        serverAddress = result.serverRoot.value,
                        isBusy = false,
                        error = null,
                        password = "",
                        user = info.user,
                    )
                }
            }
        }
    }

    companion object {
        private const val SESSION_EXPIRED = "会话已过期，请重新登录"

        fun factory(
            sessionRepository: AppSessionGateway,
            sessionEventBus: SessionEventBus,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(sessionRepository, sessionEventBus) }
        }
    }
}

data class AppUiState(
    val stage: AppStage = AppStage.Loading,
    val serverRoot: ServerRoot? = null,
    val serverAddress: String = "",
    val username: String = "admin",
    val password: String = "",
    val isBusy: Boolean = false,
    val error: UiError? = null,
    val user: SessionUser? = null,
)

enum class AppStage { Loading, ServerSetup, Login, PasswordNotInitialized, Content }

data class UiError(
    val message: String,
    val logId: String? = null,
    val retryable: Boolean = false,
)

private fun Throwable.toUiError(): UiError = when (this) {
    is ApiFailure -> UiError(
        message = error.message,
        logId = error.logId.takeIf(String::isNotBlank),
        retryable = error.retryable && error.code != "RATE_LIMITED",
    )
    is IllegalArgumentException -> UiError(message ?: "服务地址不正确")
    is JsonDataException -> UiError(message ?: "这不是兼容的 PhotoTube 服务")
    is IncompatibleServerException -> UiError(message ?: "这不是兼容的 PhotoTube 服务")
    is IOException -> UiError("无法连接 PhotoTube，请检查地址、网络与服务状态", retryable = true)
    else -> UiError("连接失败，请稍后重试", retryable = true)
}
