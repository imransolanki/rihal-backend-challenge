package com.flowcare.backend.storage.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "storage")
data class StorageProperties(
    val uploadDir: String = "./uploads",
    val idDocumentsDir: String = "./uploads/id_documents",
    val maxFileSize: Long = 5242880,
    val allowedImageTypes: List<String> = listOf("image/jpeg", "image/png", "image/gif", "image/bmp", "application/pdf"),
    val allowedExtensions: List<String> = listOf("jpg", "jpeg", "png", "gif", "bmp", "pdf")
)
