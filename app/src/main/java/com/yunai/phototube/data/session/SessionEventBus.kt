package com.yunai.phototube.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionEventBus {
    private val mutableUnauthorizedPending = MutableStateFlow(false)

    val unauthorizedPending = mutableUnauthorizedPending.asStateFlow()

    fun notifyUnauthorized() {
        mutableUnauthorizedPending.value = true
    }

    /** 只有一个消费者能取得当前锁存事件；并发 401 会自然合并。 */
    fun consumeUnauthorized(): Boolean = mutableUnauthorizedPending.compareAndSet(
        expect = true,
        update = false,
    )
}
