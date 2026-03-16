package com.flowcare.backend.storage.service

import com.flowcare.backend.storage.config.StorageProperties
import com.flowcare.backend.storage.exception.FileStorageException
import com.flowcare.backend.storage.exception.InvalidFileException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

@Service
class FileStorageService(private val properties: StorageProperties) {

    private val log = LoggerFactory.getLogger(FileStorageService::class.java)
    private val uploadPath: Path = Paths.get(properties.idDocumentsDir).toAbsolutePath().normalize()

    init {
        try {
            Files.createDirectories(uploadPath)
            log.info("Upload directory initialized at: {}", uploadPath)
        } catch (e: IOException) {
            throw FileStorageException("Could not create upload directory", e)
        }
    }

    fun storeIdDocument(file: MultipartFile, userId: String): String {
        validateFile(file)

        val extension = getFileExtension(file.originalFilename ?: "")
        val fileName = "${userId}_${UUID.randomUUID()}.$extension"

        try {
            Files.copy(file.inputStream, uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING)
            val relativePath = "id_documents/$fileName"
            log.info("File stored: {}", relativePath)
            return relativePath
        } catch (e: IOException) {
            throw FileStorageException("Failed to store file: ${file.originalFilename}", e)
        }
    }

    fun storeAppointmentAttachment(file: MultipartFile, appointmentId: String): String {
        validateFile(file)
        val extension = getFileExtension(file.originalFilename ?: "")
        val fileName = "appt_${appointmentId}_${UUID.randomUUID()}.$extension"
        val attachmentPath = Paths.get(properties.uploadDir, "attachments").toAbsolutePath().normalize()
        
        try {
            Files.createDirectories(attachmentPath)
            Files.copy(file.inputStream, attachmentPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING)
            val relativePath = "attachments/$fileName"
            log.info("Attachment stored: {}", relativePath)
            return relativePath
        } catch (e: IOException) {
            throw FileStorageException("Failed to store attachment: ${file.originalFilename}", e)
        }
    }

    fun loadFile(relativePath: String): Path {
        val basePath = Paths.get(properties.uploadDir).toAbsolutePath().normalize()
        return basePath.resolve(relativePath).normalize()
    }

    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw InvalidFileException("File is empty")
        }
        if (file.size > properties.maxFileSize) {
            throw InvalidFileException("File size exceeds maximum limit of ${properties.maxFileSize / 1024 / 1024} MB")
        }
        val contentType = file.contentType
        if (contentType == null || contentType !in properties.allowedImageTypes) {
            throw InvalidFileException("Invalid file type. Allowed types: ${properties.allowedImageTypes.joinToString(", ")}")
        }
        val extension = getFileExtension(file.originalFilename ?: "").lowercase()
        if (extension !in properties.allowedExtensions) {
            throw InvalidFileException("Invalid file extension. Allowed extensions: ${properties.allowedExtensions.joinToString(", ")}")
        }
    }

    private fun getFileExtension(filename: String): String = filename.substringAfterLast('.', "")
}
