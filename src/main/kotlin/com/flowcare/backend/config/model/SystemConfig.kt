package com.flowcare.backend.config.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "system_config")
data class SystemConfig(
    @Id
    val key: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var value: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
