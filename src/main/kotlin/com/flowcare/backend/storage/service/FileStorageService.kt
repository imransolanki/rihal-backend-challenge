package com.flowcare.backend.storage.service

import com.flowcare.backend.storage.exception.FileStorageException
import com.flowcare.backend.storage.exception.InvalidFileException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

@Service
class FileStorageService(
    @Value("\${storage.id-documents-dir}") private val idDocumentsDir: String,
    @Value("\${storage.max-file-size}") private val maxFileSize: Long,
    @Value("\${storage.allowed-image-types}") private val allowedImageTypes: List<String>,
    @Value("\${storage.allowed-extensions}") private val allowedExtensions: List<String>
) {
    private val log = LoggerFactory.getLogger(FileStorageService::class.java)
    private val uploadPath: Path = Paths.get(idDocumentsDir).toAbsolutePath().normalize()

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

    fun loadFile(relativePath: String): Path {
        val basePath = Paths.get(idDocumentsDir).toAbsolutePath().normalize().parent
        return basePath.resolve(relativePath).normalize()
    }

    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw InvalidFileException("File is empty")
        }
        if (file.size > maxFileSize) {
            throw InvalidFileException("File size exceeds maximum limit of ${maxFileSize / 1024 / 1024} MB")
        }
        val contentType = file.contentType
        if (contentType == null || contentType !in allowedImageTypes) {
            throw InvalidFileException("Invalid file type. Allowed types: ${allowedImageTypes.joinToString(", ")}")
        }
        val extension = getFileExtension(file.originalFilename ?: "").lowercase()
        if (extension !in allowedExtensions) {
            throw InvalidFileException("Invalid file extension. Allowed extensions: ${allowedExtensions.joinToString(", ")}")
        }
    }

    private fun getFileExtension(filename: String): String = filename.substringAfterLast('.', "")
}
