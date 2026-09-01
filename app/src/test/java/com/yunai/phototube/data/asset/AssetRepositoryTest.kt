package com.yunai.phototube.data.asset

import com.yunai.phototube.data.remote.BatchItemFailure
import com.yunai.phototube.data.remote.BatchOperationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AssetRepositoryTest {
    @Test
    fun http200PartialFailureIsStillAnOperationFailure() {
        val result = BatchOperationResult(
            succeeded = emptyList(),
            failed = listOf(
                BatchItemFailure(
                    target = "asset-1",
                    code = "VALIDATION_FAILED",
                    message = "资产在回收站里，先恢复再操作",
                ),
            ),
        )

        val failure = assertThrows(AssetMutationFailure::class.java) {
            result.requireAssetSucceeded("asset-1")
        }

        assertEquals("VALIDATION_FAILED", failure.item.code)
    }

    @Test
    fun matchingSucceededIdCompletesNormally() {
        BatchOperationResult(
            succeeded = listOf("asset-1"),
            failed = emptyList(),
        ).requireAssetSucceeded("asset-1")
    }

    @Test
    fun batchValidationRejectsEmptyAndMoreThanFiveHundred() {
        assertThrows(IllegalArgumentException::class.java) { emptyList<String>().validatedBatch() }
        assertThrows(IllegalArgumentException::class.java) {
            (1..501).map(Int::toString).validatedBatch()
        }
        assertEquals(listOf("a", "b"), listOf("a", "a", "b").validatedBatch())
    }
}
