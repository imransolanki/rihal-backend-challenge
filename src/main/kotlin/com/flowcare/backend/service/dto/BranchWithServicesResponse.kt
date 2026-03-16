package com.flowcare.backend.service.dto

import com.flowcare.backend.branch.dto.BranchResponse

data class BranchWithServicesResponse(
    val branch: BranchResponse,
    val services: List<ServiceTypeResponse>
)
