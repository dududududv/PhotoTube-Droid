package com.yunai.phototube.ui.system

import com.yunai.phototube.data.system.SystemAlertUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemFormattingTest {
    @Test
    fun bytesAndCountsUseStableHumanReadableUnits() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1.00 KB", formatBytes(1_024))
        assertEquals("10.0 MB", formatBytes(10L * 1_024 * 1_024))
        assertEquals("3 次", formatAlertValue(3, SystemAlertUnit.COUNT))
    }
}
