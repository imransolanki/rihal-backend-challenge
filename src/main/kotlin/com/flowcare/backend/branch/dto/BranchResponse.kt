package com.flowcare.backend.branch.dto

data class BranchResponse(
    val id: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String
)
