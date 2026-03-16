package com.flowcare.backend.seed.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class SeedData(
    val users: Users,
    val branches: List<BranchDto>,
    @JsonProperty("service_types") val serviceTypes: List<ServiceTypeDto>,
    @JsonProperty("staff_service_types") val staffServiceTypes: List<StaffServiceTypeDto>,
    val slots: List<SlotDto>,
    val appointments: List<AppointmentDto>,
    @JsonProperty("audit_logs") val auditLogs: List<AuditLogDto>
)

data class Users(
    val admin: List<UserDto>,
    @JsonProperty("branch_managers") val branchManagers: List<UserDto>,
    val staff: List<UserDto>,
    val customers: List<UserDto>
)

data class UserDto(
    val id: String,
    val username: String,
    val password: String,
    val role: String,
    @JsonProperty("full_name") val fullName: String,
    val email: String,
    val phone: String? = null,
    @JsonProperty("branch_id") val branchId: String? = null,
    @JsonProperty("is_active") val isActive: Boolean = true
)

data class BranchDto(
    val id: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String,
    @JsonProperty("is_active") val isActive: Boolean = true
)

data class ServiceTypeDto(
    val id: String,
    @JsonProperty("branch_id") val branchId: String,
    val name: String,
    val description: String,
    @JsonProperty("duration_minutes") val durationMinutes: Int,
    @JsonProperty("is_active") val isActive: Boolean = true
)

data class StaffServiceTypeDto(
    @JsonProperty("staff_id") val staffId: String,
    @JsonProperty("service_type_id") val serviceTypeId: String
)

data class SlotDto(
    val id: String,
    @JsonProperty("branch_id") val branchId: String,
    @JsonProperty("service_type_id") val serviceTypeId: String,
    @JsonProperty("staff_id") val staffId: String? = null,
    @JsonProperty("start_at") val startAt: String,
    @JsonProperty("end_at") val endAt: String,
    val capacity: Int = 1,
    @JsonProperty("booked_count") val bookedCount: Int = 0,
    @JsonProperty("is_active") val isActive: Boolean = true
)

data class AppointmentDto(
    val id: String,
    @JsonProperty("customer_id") val customerId: String,
    @JsonProperty("branch_id") val branchId: String,
    @JsonProperty("service_type_id") val serviceTypeId: String,
    @JsonProperty("slot_id") val slotId: String,
    @JsonProperty("staff_id") val staffId: String? = null,
    val status: String,
    @JsonProperty("created_at") val createdAt: String
)

data class AuditLogDto(
    val id: String,
    @JsonProperty("actor_id") val actorId: String,
    @JsonProperty("actor_role") val actorRole: String,
    @JsonProperty("action_type") val actionType: String,
    @JsonProperty("entity_type") val entityType: String,
    @JsonProperty("entity_id") val entityId: String,
    val timestamp: String,
    val metadata: Map<String, Any>? = null
)
