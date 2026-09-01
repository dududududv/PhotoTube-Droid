package com.yunai.phototube.data.system

import java.time.OffsetDateTime

data class SystemStatus(
    val generatedAt: String,
    val alerts: List<SystemAlert>,
) {
    init {
        require(runCatching { OffsetDateTime.parse(generatedAt) }.isSuccess) {
            "系统状态时间必须是 RFC 3339"
        }
    }
}

data class SystemAlert(
    val code: SystemAlertCode,
    val subject: String,
    val message: String,
    val currentValue: Long,
    val thresholdValue: Long,
    val unit: SystemAlertUnit,
) {
    init {
        require(subject.isNotBlank()) { "告警对象不能为空" }
        require(message.isNotBlank()) { "告警说明不能为空" }
        require(currentValue >= 0) { "告警当前值不能为负数" }
        require(thresholdValue > 0) { "告警阈值必须大于 0" }
    }
}

enum class SystemAlertCode {
    DERIVATIVE_DISK_FREE_LOW,
    LIBRARY_DISK_FREE_LOW,
    DERIVATIVE_CACHE_HIGH,
    CONSECUTIVE_JOB_FAILURES,
}

enum class SystemAlertUnit { BYTES, COUNT }

data class LocalCacheSnapshot(
    val memoryBytes: Long,
    val memoryMaxBytes: Long,
    val imageDiskBytes: Long,
    val httpDiskBytes: Long,
    val httpDiskMaxBytes: Long,
) {
    init {
        require(memoryBytes >= 0 && memoryMaxBytes >= 0) { "内存缓存大小不能为负数" }
        require(imageDiskBytes >= 0 && httpDiskBytes >= 0 && httpDiskMaxBytes >= 0) {
            "磁盘缓存大小不能为负数"
        }
    }

    val storedBytes: Long
        get() = imageDiskBytes + httpDiskBytes
}
