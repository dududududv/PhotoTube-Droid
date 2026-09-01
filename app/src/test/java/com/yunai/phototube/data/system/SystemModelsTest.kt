package com.yunai.phototube.data.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SystemModelsTest {
    @Test
    fun statusAndCacheModelsPreserveContractValues() {
        val status = SystemStatus(
            generatedAt = "2026-09-01T03:30:00Z",
            alerts = listOf(
                SystemAlert(
                    code = SystemAlertCode.DERIVATIVE_CACHE_HIGH,
                    subject = "派生缓存",
                    message = "派生缓存已接近容量上限",
                    currentValue = 900,
                    thresholdValue = 1_000,
                    unit = SystemAlertUnit.BYTES,
                ),
            ),
        )
        val cache = LocalCacheSnapshot(
            memoryBytes = 10,
            memoryMaxBytes = 20,
            imageDiskBytes = 30,
            httpDiskBytes = 40,
            httpDiskMaxBytes = 50,
        )

        assertEquals(SystemAlertCode.DERIVATIVE_CACHE_HIGH, status.alerts.single().code)
        assertEquals(70L, cache.storedBytes)
    }

    @Test
    fun malformedStatusFactsAreRejectedAtModelBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            SystemStatus("not-a-date", emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            SystemAlert(
                SystemAlertCode.CONSECUTIVE_JOB_FAILURES,
                "任务",
                "连续失败",
                currentValue = 3,
                thresholdValue = 0,
                unit = SystemAlertUnit.COUNT,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalCacheSnapshot(0, 0, -1, 0, 0)
        }
    }
}
