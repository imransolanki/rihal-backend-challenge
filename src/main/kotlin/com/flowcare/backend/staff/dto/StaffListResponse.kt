package com.flowcare.backend.staff.dto

data class StaffListResponse(
    val id: String,
    val fullName: String,
    val email: String,
    val branchId: String,
    val branchName: String,
    val isActive: Boolean
)

data class StaffDetailResponse(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val branchId: String,
    val branchName: String,
    val isActive: Boolean,
    val serviceAssignments: List<ServiceAssignmentDto>
)

data class ServiceAssignmentDto(
    val serviceTypeId: String,
    val serviceTypeName: String
)
