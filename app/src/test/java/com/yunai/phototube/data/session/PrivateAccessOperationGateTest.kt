package com.yunai.phototube.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateAccessOperationGateTest {
    @Test
    fun `后提交的锁定会等待活动解锁并最终执行`() = runTest {
        val gate = PrivateAccessOperationGate()
        val unlockStarted = CompletableDeferred<Unit>()
        val releaseUnlock = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        supervisorScope {
            val unlock = async(start = CoroutineStart.UNDISPATCHED) {
                gate.unlock {
                    events += "unlock-start"
                    unlockStarted.complete(Unit)
                    releaseUnlock.await()
                    events += "unlock-end"
                }
            }
            unlockStarted.await()
            val lock = async(start = CoroutineStart.UNDISPATCHED) {
                gate.lock { events += "lock" }
            }

            assertEquals(listOf("unlock-start"), events)
            releaseUnlock.complete(Unit)
            unlock.await()
            lock.await()
        }

        assertEquals(listOf("unlock-start", "unlock-end", "lock"), events)
    }

    @Test
    fun `锁定提交后会淘汰仍在排队的旧解锁`() = runTest {
        val gate = PrivateAccessOperationGate()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var queuedUnlockExecuted = false
        var lockExecuted = false

        supervisorScope {
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                gate.unlock {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
            }
            firstStarted.await()
            val queued = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching {
                    gate.unlock { queuedUnlockExecuted = true }
                }
            }
            val lock = async(start = CoroutineStart.UNDISPATCHED) {
                gate.lock { lockExecuted = true }
            }

            releaseFirst.complete(Unit)
            first.await()
            assertTrue(queued.await().isFailure)
            lock.await()
        }

        assertTrue(!queuedUnlockExecuted)
        assertTrue(lockExecuted)
    }
}
