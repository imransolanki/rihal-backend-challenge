package com.flowcare.backend.branch.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "branches")
data class Branch(
    @Id
    val id: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val city: String,

    @Column(nullable = false)
    val address: String,

    @Column(nullable = false)
    val timezone: String = "Asia/Muscat",

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
