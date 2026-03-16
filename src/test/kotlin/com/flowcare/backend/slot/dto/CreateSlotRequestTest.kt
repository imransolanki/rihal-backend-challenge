package com.flowcare.backend.slot.dto

import jakarta.validation.Validation
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class CreateSlotRequestTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `should validate valid request`() {
        val request = CreateSlotRequest(
            branchId = "branch1", serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        assertTrue(validator.validate(request).isEmpty())
    }

    @Test
    fun `should reject empty branchId`() {
        val request = CreateSlotRequest(
            branchId = "", serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        assertTrue(validator.validate(request).any { it.message.contains("Branch ID") })
    }

    @Test
    fun `should reject empty serviceTypeId`() {
        val request = CreateSlotRequest(
            branchId = "branch1", serviceTypeId = "",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1)
        )
        assertTrue(validator.validate(request).any { it.message.contains("Service type ID") })
    }

    @Test
    fun `should reject capacity less than 1`() {
        val request = CreateSlotRequest(
            branchId = "branch1", serviceTypeId = "service1",
            startAt = OffsetDateTime.now().plusDays(1),
            endAt = OffsetDateTime.now().plusDays(1).plusHours(1),
            capacity = 0
        )
        assertTrue(validator.validate(request).any { it.message.contains("Capacity") })
    }
}
