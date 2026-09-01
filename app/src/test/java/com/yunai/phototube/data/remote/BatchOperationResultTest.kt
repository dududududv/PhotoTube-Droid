package com.yunai.phototube.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BatchOperationResultTest {
    @Test
    fun partialSuccessIsValidWhenEveryRequestedTargetIsAccountedFor() {
        val result = BatchOperationResult(
            succeeded = listOf("asset-1"),
            failed = listOf(BatchItemFailure("asset-2", "OFFLINE", "原文件离线")),
        )

        assertEquals(result, result.validatedAgainst(listOf("asset-1", "asset-2")))
    }

    @Test
    fun duplicateOrOverlappingOutcomesAreRejected() {
        assertThrows(IllegalStateException::class.java) {
            BatchOperationResult(
                succeeded = listOf("asset-1", "asset-1"),
                failed = emptyList(),
            ).validatedAgainst(listOf("asset-1"))
        }
        assertThrows(IllegalStateException::class.java) {
            BatchOperationResult(
                succeeded = listOf("asset-1"),
                failed = listOf(BatchItemFailure("asset-1", "FAILED", "冲突结果")),
            ).validatedAgainst(listOf("asset-1"))
        }
    }

    @Test
    fun missingAndUnexpectedTargetsAreRejected() {
        assertThrows(IllegalStateException::class.java) {
            BatchOperationResult(
                succeeded = listOf("asset-1"),
                failed = emptyList(),
            ).validatedAgainst(listOf("asset-1", "asset-2"))
        }
        assertThrows(IllegalStateException::class.java) {
            BatchOperationResult(
                succeeded = listOf("asset-1", "asset-other"),
                failed = emptyList(),
            ).validatedAgainst(listOf("asset-1"))
        }
    }
}
