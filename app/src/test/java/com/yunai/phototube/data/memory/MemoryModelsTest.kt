package com.yunai.phototube.data.memory

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MemoryModelsTest {
    @Test
    fun dateRangeAndExistingPersonRulesPreserveContractShape() {
        val dateRange = MemoryExclusion(
            id = DATE_RULE_ID,
            kind = MemoryExclusionKind.DATE_RANGE,
            dateFrom = "2026-03-01",
            dateTo = "2026-03-31",
            personId = null,
            createdAt = "2026-09-01T08:00:00Z",
        )
        val person = MemoryExclusion(
            id = PERSON_RULE_ID,
            kind = MemoryExclusionKind.PERSON,
            dateFrom = null,
            dateTo = null,
            personId = "person-42",
            createdAt = "2026-09-01T08:01:00+08:00",
        )

        assertEquals(LocalDate.of(2026, 3, 1), dateRange.parsedDateFrom)
        assertEquals("person-42", person.personId)
        assertNull(person.parsedDateFrom)
    }

    @Test
    fun malformedOrReversedResponseRulesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MemoryExclusion(
                DATE_RULE_ID,
                MemoryExclusionKind.DATE_RANGE,
                "2026-04-01",
                "2026-03-01",
                null,
                "2026-09-01T08:00:00Z",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MemoryExclusion(
                PERSON_RULE_ID,
                MemoryExclusionKind.PERSON,
                null,
                null,
                null,
                "2026-09-01T08:00:00Z",
            )
        }
    }

    @Test
    fun androidCreateRequestIsDateRangeOnlyAndOrdered() {
        val request = CreateDateMemoryExclusionRequest(
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31),
        )

        assertEquals(MemoryExclusionKind.DATE_RANGE, request.kind)
        assertThrows(IllegalArgumentException::class.java) {
            CreateDateMemoryExclusionRequest(
                MemoryExclusionKind.PERSON,
                "2026-03-01",
                "2026-03-31",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CreateDateMemoryExclusionRequest(
                dateFrom = "2026-04-01",
                dateTo = "2026-03-01",
            )
        }
    }

    private companion object {
        const val DATE_RULE_ID = "11111111-1111-4111-8111-111111111111"
        const val PERSON_RULE_ID = "22222222-2222-4222-8222-222222222222"
    }
}
