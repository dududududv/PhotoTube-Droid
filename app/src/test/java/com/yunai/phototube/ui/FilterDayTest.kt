package com.yunai.phototube.ui

import com.yunai.phototube.ui.remote.filterDayInstant
import java.time.*
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterDayTest {
    @Test fun localBoundsRespectDaylightSavingAndLeapDay() {
        val zone=ZoneId.of("America/New_York")
        assertEquals("2026-03-08T05:00:00Z",filterDayInstant(LocalDate.of(2026,3,8),false,zone))
        assertEquals("2026-03-09T03:59:59.999999999Z",filterDayInstant(LocalDate.of(2026,3,8),true,zone))
        assertEquals("2026-11-02T04:59:59.999999999Z",filterDayInstant(LocalDate.of(2026,11,1),true,zone))
        assertEquals("2024-02-29T15:59:59.999999999Z",filterDayInstant(LocalDate.of(2024,2,29),true,ZoneId.of("Asia/Shanghai")))
    }
}
