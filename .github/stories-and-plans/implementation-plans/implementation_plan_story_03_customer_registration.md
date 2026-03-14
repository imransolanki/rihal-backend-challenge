# Story 3: Customer Registration with ID Verification - Implementation Plan

## Overview
Implement customer registration API endpoint that accepts user details and ID document image upload. The system must validate file type and size, store the file securely on the filesystem, hash the password, and create a customer user account that can authenticate immediately.

## Architecture
POST /api/auth/register (multipart/form-data) → RegistrationController → RegistrationService → Validate file → Store to filesystem → Hash password → Save User entity → Return success
- Public endpoint (no authentication required)
- Local filesystem storage with configurable directory
- File validation: type (JPEG, PNG, GIF, BMP) and size (max 5 MB)
- UUID-based user ID generation
- BCrypt password hashing

## Implementation Phases

### Phase 1: Database Schema Update for ID Document
**Files**: 
- `src/main/resources/db/migration/V3__add_id_document_to_users.sql`
- `src/main/kotlin/com/flowcare/backend/auth/model/User.kt` (update)

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/auth/model/UserIdDocumentTest.kt`

Add id_document_path column to users table and update the User entity to support storing ID document references.

**Key code changes:**

```sql
-- src/main/resources/db/migration/V3__add_id_document_to_users.sql
ALTER TABLE users 
ADD COLUMN id_document_path VARCHAR(500);

CREATE INDEX idx_users_id_document_path ON users(id_document_path);
```

```kotlin
// Update src/main/kotlin/com/flowcare/backend/auth/model/User.kt
package com.flowcare.backend.auth.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
data class User(
    @Id
    val id: String,
    
    @Column(unique = true, nullable = false)
    val username: String,
    
    @Column(nullable = false)
    val password: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role,
    
    @Column(name = "full_name", nullable = false)
    val fullName: String,
    
    @Column(unique = true, nullable = false)
    val email: String,
    
    val phone: String? = null,
    
    @Column(name = "branch_id")
    val branchId: String? = null,
    
    @Column(name = "id_document_path")
    val idDocumentPath: String? = null,
    
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

**Test cases for this phase:**
- Test migration V3 executes successfully
- Test User entity can be saved with id_document_path
- Test User entity can be saved without id_document_path (nullable)

```kotlin
// src/test/kotlin/com/flowcare/backend/auth/model/UserIdDocumentTest.kt
package com.flowcare.backend.auth.model

import com.flowcare.backend.auth.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.assertj.core.api.Assertions.assertThat

@DataJpaTest
class UserIdDocumentTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `should save user with id document path`() {
        val user = User(
            id = "test_001",
            username = "testuser",
            password = "password",
            role = Role.CUSTOMER,
            fullName = "Test User",
            email = "test@example.com",
            idDocumentPath = "/uploads/id_documents/test_001.jpg"
        )
        
        val saved = userRepository.save(user)
        assertThat(saved.idDocumentPath).isEqualTo("/uploads/id_documents/test_001.jpg")
    }

    @Test
    fun `should save user without id document path`() {
        val user = User(
            id = "test_002",
            username = "testuser2",
            password = "password",
            role = Role.STAFF,
            fullName = "Test Staff",
            email = "staff@example.com",
            idDocumentPath = null
        )
        
        val saved = userRepository.save(user)
        assertThat(saved.idDocumentPath).isNull()
    }
}
```

**Technical details:**
- id_document_path is nullable (only customers need it)
- Indexed for potential future queries
- Stores relative path from upload directory

---

### Phase 2: File Storage Service
**Files**: 
- `src/main/kotlin/com/flowcare/backend/storage/service/FileStorageService.kt`
- `src/main/kotlin/com/flowcare/backend/storage/exception/FileStorageException.kt`
- `src/main/kotlin/com/flowcare/backend/storage/exception/InvalidFileException.kt`
- `src/main/resources/application.yml` (add storage configuration)

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/storage/service/FileStorageServiceTest.kt`

Create a file storage service that validates and stores uploaded files to the local filesystem.

**Key code changes:**

```yaml
# Add to src/main/resources/application.yml
storage:
  upload-dir: ./uploads
  id-documents-dir: ${storage.upload-dir}/id_documents
  max-file-size: 5242880  # 5 MB in bytes
  allowed-image-types:
    - image/jpeg
    - image/png
    - image/gif
    - image/bmp
  allowed-extensions:
    - jpg
    - jpeg
    - png
    - gif
    - bmp
```

```kotlin
// src/main/kotlin/com/flowcare/backend/storage/exception/FileStorageException.kt
package com.flowcare.backend.storage.exception

class FileStorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/storage/exception/InvalidFileException.kt
package com.flowcare.backend.storage.exception

class InvalidFileException(message: String) : RuntimeException(message)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/storage/service/FileStorageService.kt
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
    private val logger = LoggerFactory.getLogger(FileStorageService::class.java)
    private val uploadPath: Path = Paths.get(idDocumentsDir).toAbsolutePath().normalize()

    init {
        try {
            Files.createDirectories(uploadPath)
            logger.info("Upload directory created/verified at: $uploadPath")
        } catch (e: IOException) {
            throw FileStorageException("Could not create upload directory", e)
        }
    }

    fun storeIdDocument(file: MultipartFile, userId: String): String {
        validateFile(file)
        
        val fileExtension = getFileExtension(file.originalFilename ?: "")
        val fileName = "${userId}_${UUID.randomUUID()}.$fileExtension"
        
        try {
            val targetLocation = uploadPath.resolve(fileName)
            Files.copy(file.inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING)
            
            val relativePath = "/uploads/id_documents/$fileName"
            logger.info("File stored successfully: $relativePath")
            return relativePath
        } catch (e: IOException) {
            throw FileStorageException("Failed to store file: ${file.originalFilename}", e)
        }
    }

    private fun validateFile(file: MultipartFile) {
        // Check if file is empty
        if (file.isEmpty) {
            throw InvalidFileException("File is empty")
        }
        
        // Check file size
        if (file.size > maxFileSize) {
            throw InvalidFileException("File size exceeds maximum limit of ${maxFileSize / 1024 / 1024} MB")
        }
        
        // Check MIME type
        val contentType = file.contentType
        if (contentType == null || !allowedImageTypes.contains(contentType)) {
            throw InvalidFileException("Invalid file type. Allowed types: ${allowedImageTypes.joinToString(", ")}")
        }
        
        // Check file extension
        val extension = getFileExtension(file.originalFilename ?: "")
        if (!allowedExtensions.contains(extension.lowercase())) {
            throw InvalidFileException("Invalid file extension. Allowed extensions: ${allowedExtensions.joinToString(", ")}")
        }
    }

    private fun getFileExtension(filename: String): String {
        return filename.substringAfterLast('.', "")
    }
}
```

**Test cases for this phase:**
- Test file storage with valid image file
- Test rejection of file exceeding 5 MB
- Test rejection of invalid MIME type
- Test rejection of invalid file extension
- Test rejection of empty file
- Test upload directory is created on initialization

```kotlin
// src/test/kotlin/com/flowcare/backend/storage/service/FileStorageServiceTest.kt
package com.flowcare.backend.storage.service

import com.flowcare.backend.storage.exception.InvalidFileException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Paths

@SpringBootTest
class FileStorageServiceTest {

    @Autowired
    private lateinit var fileStorageService: FileStorageService

    @Test
    fun `should store valid image file`() {
        val file = MockMultipartFile(
            "file",
            "test.jpg",
            "image/jpeg",
            "test image content".toByteArray()
        )
        
        val path = fileStorageService.storeIdDocument(file, "test_user_001")
        
        assertThat(path).startsWith("/uploads/id_documents/")
        assertThat(path).contains("test_user_001")
        assertThat(path).endsWith(".jpg")
    }

    @Test
    fun `should reject file exceeding size limit`() {
        val largeContent = ByteArray(6 * 1024 * 1024) // 6 MB
        val file = MockMultipartFile(
            "file",
            "large.jpg",
            "image/jpeg",
            largeContent
        )
        
        val exception = assertThrows<InvalidFileException> {
            fileStorageService.storeIdDocument(file, "test_user_002")
        }
        
        assertThat(exception.message).contains("exceeds maximum limit")
    }

    @Test
    fun `should reject invalid MIME type`() {
        val file = MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "test content".toByteArray()
        )
        
        val exception = assertThrows<InvalidFileException> {
            fileStorageService.storeIdDocument(file, "test_user_003")
        }
        
        assertThat(exception.message).contains("Invalid file type")
    }

    @Test
    fun `should reject invalid file extension`() {
        val file = MockMultipartFile(
            "file",
            "test.txt",
            "image/jpeg",
            "test content".toByteArray()
        )
        
        val exception = assertThrows<InvalidFileException> {
            fileStorageService.storeIdDocument(file, "test_user_004")
        }
        
        assertThat(exception.message).contains("Invalid file extension")
    }

    @Test
    fun `should reject empty file`() {
        val file = MockMultipartFile(
            "file",
            "empty.jpg",
            "image/jpeg",
            ByteArray(0)
        )
        
        val exception = assertThrows<InvalidFileException> {
            fileStorageService.storeIdDocument(file, "test_user_005")
        }
        
        assertThat(exception.message).contains("File is empty")
    }
}
```

**Technical details:**
- Files stored with pattern: {userId}_{UUID}.{extension}
- Upload directory created automatically on service initialization
- Both MIME type and extension validated for security
- Configurable via application.yml
- Returns relative path for database storage


---

### Phase 3: Registration DTOs and Service
**Files**: 
- `src/main/kotlin/com/flowcare/backend/auth/dto/RegistrationRequest.kt`
- `src/main/kotlin/com/flowcare/backend/auth/dto/RegistrationResponse.kt`
- `src/main/kotlin/com/flowcare/backend/auth/service/RegistrationService.kt`
- `src/main/kotlin/com/flowcare/backend/auth/exception/EmailAlreadyExistsException.kt`
- `src/main/kotlin/com/flowcare/backend/auth/exception/UsernameAlreadyExistsException.kt`

**Test Files**: 
- `src/main/kotlin/com/flowcare/backend/auth/service/RegistrationServiceTest.kt`

Create DTOs for registration request/response and implement the registration service with business logic.

**Key code changes:**

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/dto/RegistrationRequest.kt
package com.flowcare.backend.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegistrationRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String,
    
    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String,
    
    @field:NotBlank(message = "Full name is required")
    @field:Size(max = 200, message = "Full name must not exceed 200 characters")
    val fullName: String,
    
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,
    
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone number must be valid")
    val phone: String
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/dto/RegistrationResponse.kt
package com.flowcare.backend.auth.dto

data class RegistrationResponse(
    val id: String,
    val username: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val message: String = "Registration successful"
)
```

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/exception/EmailAlreadyExistsException.kt
package com.flowcare.backend.auth.exception

class EmailAlreadyExistsException(email: String) : RuntimeException("Email already exists: $email")
```

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/exception/UsernameAlreadyExistsException.kt
package com.flowcare.backend.auth.exception

class UsernameAlreadyExistsException(username: String) : RuntimeException("Username already exists: $username")
```

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/service/RegistrationService.kt
package com.flowcare.backend.auth.service

import com.flowcare.backend.auth.dto.RegistrationRequest
import com.flowcare.backend.auth.dto.RegistrationResponse
import com.flowcare.backend.auth.exception.EmailAlreadyExistsException
import com.flowcare.backend.auth.exception.UsernameAlreadyExistsException
import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.repository.UserRepository
import com.flowcare.backend.storage.service.FileStorageService
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.*

@Service
class RegistrationService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val fileStorageService: FileStorageService
) {
    private val logger = LoggerFactory.getLogger(RegistrationService::class.java)

    @Transactional
    fun registerCustomer(request: RegistrationRequest, idDocument: MultipartFile): RegistrationResponse {
        logger.info("Registering new customer: ${request.username}")
        
        // Check if username already exists
        if (userRepository.findByUsername(request.username).isPresent) {
            throw UsernameAlreadyExistsException(request.username)
        }
        
        // Check if email already exists
        if (userRepository.findByEmail(request.email).isPresent) {
            throw EmailAlreadyExistsException(request.email)
        }
        
        // Generate user ID
        val userId = "usr_cust_${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}"
        
        // Store ID document
        val idDocumentPath = fileStorageService.storeIdDocument(idDocument, userId)
        
        // Hash password
        val hashedPassword = passwordEncoder.encode(request.password)
        
        // Create user entity
        val user = User(
            id = userId,
            username = request.username,
            password = hashedPassword,
            role = Role.CUSTOMER,
            fullName = request.fullName,
            email = request.email,
            phone = request.phone,
            idDocumentPath = idDocumentPath,
            isActive = true
        )
        
        // Save user
        val savedUser = userRepository.save(user)
        
        logger.info("Customer registered successfully: ${savedUser.id}")
        
        return RegistrationResponse(
            id = savedUser.id,
            username = savedUser.username,
            fullName = savedUser.fullName,
            email = savedUser.email,
            phone = savedUser.phone!!
        )
    }
}
```

**Test cases for this phase:**
- Test successful customer registration
- Test rejection when username already exists
- Test rejection when email already exists
- Test password is hashed correctly
- Test user ID is generated with correct format
- Test customer role is automatically assigned
- Test transaction rollback on file storage failure

```kotlin
// src/test/kotlin/com/flowcare/backend/auth/service/RegistrationServiceTest.kt
package com.flowcare.backend.auth.service

import com.flowcare.backend.auth.dto.RegistrationRequest
import com.flowcare.backend.auth.exception.EmailAlreadyExistsException
import com.flowcare.backend.auth.exception.UsernameAlreadyExistsException
import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import com.flowcare.backend.auth.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.crypto.password.PasswordEncoder
import org.assertj.core.api.Assertions.assertThat

@SpringBootTest
class RegistrationServiceTest {

    @Autowired
    private lateinit var registrationService: RegistrationService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun cleanup() {
        userRepository.deleteAll()
    }

    @Test
    fun `should register customer successfully`() {
        val request = RegistrationRequest(
            username = "newcustomer",
            password = "password123",
            fullName = "New Customer",
            email = "new@example.com",
            phone = "+96890000001"
        )
        
        val idDocument = MockMultipartFile(
            "idDocument",
            "id.jpg",
            "image/jpeg",
            "test image".toByteArray()
        )
        
        val response = registrationService.registerCustomer(request, idDocument)
        
        assertThat(response.username).isEqualTo("newcustomer")
        assertThat(response.email).isEqualTo("new@example.com")
        assertThat(response.id).startsWith("usr_cust_")
        
        val savedUser = userRepository.findByUsername("newcustomer").orElseThrow()
        assertThat(savedUser.role).isEqualTo(Role.CUSTOMER)
        assertThat(savedUser.idDocumentPath).isNotNull()
        assertThat(passwordEncoder.matches("password123", savedUser.password)).isTrue()
    }

    @Test
    fun `should reject registration with existing username`() {
        val existingUser = User(
            id = "test_001",
            username = "existing",
            password = "password",
            role = Role.CUSTOMER,
            fullName = "Existing User",
            email = "existing@example.com"
        )
        userRepository.save(existingUser)
        
        val request = RegistrationRequest(
            username = "existing",
            password = "password123",
            fullName = "New User",
            email = "new@example.com",
            phone = "+96890000001"
        )
        
        val idDocument = MockMultipartFile(
            "idDocument",
            "id.jpg",
            "image/jpeg",
            "test image".toByteArray()
        )
        
        assertThrows<UsernameAlreadyExistsException> {
            registrationService.registerCustomer(request, idDocument)
        }
    }

    @Test
    fun `should reject registration with existing email`() {
        val existingUser = User(
            id = "test_002",
            username = "user1",
            password = "password",
            role = Role.CUSTOMER,
            fullName = "User One",
            email = "existing@example.com"
        )
        userRepository.save(existingUser)
        
        val request = RegistrationRequest(
            username = "newuser",
            password = "password123",
            fullName = "New User",
            email = "existing@example.com",
            phone = "+96890000001"
        )
        
        val idDocument = MockMultipartFile(
            "idDocument",
            "id.jpg",
            "image/jpeg",
            "test image".toByteArray()
        )
        
        assertThrows<EmailAlreadyExistsException> {
            registrationService.registerCustomer(request, idDocument)
        }
    }
}
```

**Technical details:**
- UUID-based ID generation with "usr_cust_" prefix
- Jakarta validation annotations for request validation
- Transactional to ensure atomicity (rollback if file storage fails)
- Password hashing before storage
- Automatic CUSTOMER role assignment


---

### Phase 4: Registration Controller and Security Configuration
**Files**: 
- `src/main/kotlin/com/flowcare/backend/auth/controller/RegistrationController.kt`
- `src/main/kotlin/com/flowcare/backend/config/SecurityConfig.kt` (update)
- `src/main/kotlin/com/flowcare/backend/common/exception/GlobalExceptionHandler.kt` (update)

**Test Files**: 
- `src/test/kotlin/com/flowcare/backend/auth/controller/RegistrationControllerTest.kt`

Create the registration REST controller and update security configuration to allow public access to the registration endpoint.

**Key code changes:**

```kotlin
// src/main/kotlin/com/flowcare/backend/auth/controller/RegistrationController.kt
package com.flowcare.backend.auth.controller

import com.flowcare.backend.auth.dto.RegistrationRequest
import com.flowcare.backend.auth.dto.RegistrationResponse
import com.flowcare.backend.auth.service.RegistrationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/auth")
class RegistrationController(
    private val registrationService: RegistrationService
) {
    
    @PostMapping(
        "/register",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun register(
        @Valid @ModelAttribute request: RegistrationRequest,
        @RequestParam("idDocument") idDocument: MultipartFile
    ): ResponseEntity<RegistrationResponse> {
        val response = registrationService.registerCustomer(request, idDocument)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}
```

```kotlin
// Update src/main/kotlin/com/flowcare/backend/config/SecurityConfig.kt
package com.flowcare.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {
    
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
    
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/health").permitAll()
                    .requestMatchers("/api/auth/register").permitAll()
                    .anyRequest().authenticated()
            }
            .httpBasic { }
            .sessionManagement { 
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) 
            }
        
        return http.build()
    }
}
```

```kotlin
// Update src/main/kotlin/com/flowcare/backend/common/exception/GlobalExceptionHandler.kt
package com.flowcare.backend.common.exception

import com.flowcare.backend.auth.exception.EmailAlreadyExistsException
import com.flowcare.backend.auth.exception.UsernameAlreadyExistsException
import com.flowcare.backend.common.dto.ErrorResponse
import com.flowcare.backend.storage.exception.FileStorageException
import com.flowcare.backend.storage.exception.InvalidFileException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {
    
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(
                ErrorResponse(
                    error = "Unauthorized",
                    message = ex.message ?: "Authentication failed",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
    
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(
                ErrorResponse(
                    error = "Forbidden",
                    message = ex.message ?: "Access denied",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
    
    @ExceptionHandler(UsernameAlreadyExistsException::class)
    fun handleUsernameAlreadyExistsException(ex: UsernameAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                ErrorResponse(
                    error = "Conflict",
                    message = ex.message ?: "Username already exists",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
    
    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailAlreadyExistsException(ex: EmailAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                ErrorResponse(
                    error = "Conflict",
                    message = ex.message ?: "Email already exists",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
    
    @ExceptionHandler(InvalidFileException::class)
    fun handleInvalidFileException(ex: InvalidFileException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    error = "Bad Request",
                    message = ex.message ?: "Invalid file",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
    
    @ExceptionHandler(FileStorageException::class)
    fun handleFileStorageException(ex: FileStorageException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    error = "Internal Server Error",
                    message = ex.message ?: "File storage failed",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceededException(ex: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    error = "Bad Request",
                    message = "File size exceeds maximum limit",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
    
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors
            .map { "${it.field}: ${it.defaultMessage}" }
            .joinToString(", ")
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    error = "Bad Request",
                    message = "Validation failed: $errors",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
    
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    error = "Internal Server Error",
                    message = ex.message ?: "An unexpected error occurred",
                    timestamp = LocalDateTime.now().toString()
                )
            )
    }
}
```

**Test cases for this phase:**
- Test successful registration via API
- Test registration without authentication (public endpoint)
- Test rejection with duplicate username returns 409
- Test rejection with duplicate email returns 409
- Test rejection with invalid file type returns 400
- Test rejection with file exceeding size limit returns 400
- Test rejection with missing required fields returns 400
- Test validation error messages include field names
- Test newly registered user can authenticate

```kotlin
// src/test/kotlin/com/flowcare/backend/auth/controller/RegistrationControllerTest.kt
package com.flowcare.backend.auth.controller

import com.flowcare.backend.auth.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun cleanup() {
        userRepository.deleteAll()
    }

    @Test
    fun `should register customer successfully`() {
        val idDocument = MockMultipartFile(
            "idDocument",
            "id.jpg",
            "image/jpeg",
            "test image content".toByteArray()
        )
        
        mockMvc.perform(
            multipart("/api/auth/register")
                .file(idDocument)
                .param("username", "newcustomer")
                .param("password", "password123")
                .param("fullName", "New Customer")
                .param("email", "new@example.com")
                .param("phone", "+96890000001")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.username").value("newcustomer"))
            .andExpect(jsonPath("$.email").value("new@example.com"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.message").value("Registration successful"))
    }

    @Test
    fun `should allow registration without authentication`() {
        val idDocument = MockMultipartFile(
            "idDocument",
            "id.jpg",
            "image/jpeg",
            "test image content".toByteArray()
        )
        
        // No authentication provided - should still work
        mockMvc.perform(
            multipart("/api/auth/register")
                .file(idDocument)
                .param("username", "publicuser")
                .param("password", "password123")
                .param("fullName", "Public User")
                .param("email", "public@example.com")
                .param("phone", "+96890000002")
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `should reject registration with invalid file type`() {
        val invalidFile = MockMultipartFile(
            "idDocument",
            "document.pdf",
            "application/pdf",
            "test content".toByteArray()
        )
        
        mockMvc.perform(
            multipart("/api/auth/register")
                .file(invalidFile)
                .param("username", "testuser")
                .param("password", "password123")
                .param("fullName", "Test User")
                .param("email", "test@example.com")
                .param("phone", "+96890000003")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid file type")))
    }

    @Test
    fun `should reject registration with missing required fields`() {
        val idDocument = MockMultipartFile(
            "idDocument",
            "id.jpg",
            "image/jpeg",
            "test image content".toByteArray()
        )
        
        // Missing username and email
        mockMvc.perform(
            multipart("/api/auth/register")
                .file(idDocument)
                .param("password", "password123")
                .param("fullName", "Test User")
                .param("phone", "+96890000005")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Validation failed")))
    }

    @Test
    fun `should allow newly registered user to authenticate`() {
        // Register user
        val idDocument = MockMultipartFile(
            "idDocument",
            "id.jpg",
            "image/jpeg",
            "test image content".toByteArray()
        )
        
        mockMvc.perform(
            multipart("/api/auth/register")
                .file(idDocument)
                .param("username", "authtest")
                .param("password", "password123")
                .param("fullName", "Auth Test")
                .param("email", "authtest@example.com")
                .param("phone", "+96890000004")
        )
            .andExpect(status().isCreated)
        
        // Try to authenticate
        mockMvc.perform(
            get("/api/test/protected")
                .with(httpBasic("authtest", "password123"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("authtest"))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
    }
}
```

**Technical details:**
- Multipart/form-data for file upload with form fields
- Public endpoint (no authentication required)
- Proper HTTP status codes (201 Created, 409 Conflict, 400 Bad Request)
- Comprehensive exception handling for all error scenarios
- Validation error messages include field names


---

### Phase 5: Documentation and Configuration
**Files**: 
- `README_SETUP.md` (update)
- `src/main/resources/application.yml` (add multipart configuration)
- `.gitignore` (update to exclude uploads directory)

**Test Files**: None (documentation phase)

Update documentation with registration API usage examples and configure multipart file upload settings.

**Key code changes:**

```yaml
# Add to src/main/resources/application.yml
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 5MB
      max-request-size: 10MB
```

```gitignore
# Add to .gitignore
# Uploads
uploads/
```

Add to README_SETUP.md:

```markdown
### Customer Registration

Customers can register by providing their details and uploading an ID document image.

#### Registration Endpoint

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -F "username=johndoe" \
  -F "password=SecurePass123" \
  -F "fullName=John Doe" \
  -F "email=john.doe@example.com" \
  -F "phone=+96890000001" \
  -F "idDocument=@/path/to/id_document.jpg"
```

Expected response (201 Created):
```json
{
  "id": "usr_cust_a1b2c3d4e5f6",
  "username": "johndoe",
  "fullName": "John Doe",
  "email": "john.doe@example.com",
  "phone": "+96890000001",
  "message": "Registration successful"
}
```

#### File Requirements

- **Required**: ID document image must be uploaded
- **Formats**: JPEG, PNG, GIF, BMP
- **Max Size**: 5 MB
- **Validation**: Both file extension and MIME type are checked

#### Validation Rules

| Field | Rules |
|-------|-------|
| username | Required, 3-50 characters, unique |
| password | Required, minimum 6 characters |
| fullName | Required, max 200 characters |
| email | Required, valid email format, unique |
| phone | Required, valid phone number format (+country code) |
| idDocument | Required, valid image file, max 5 MB |

#### Error Responses

**Duplicate Username (409 Conflict):**
```json
{
  "error": "Conflict",
  "message": "Username already exists: johndoe",
  "timestamp": "2026-03-13T16:00:00"
}
```

**Duplicate Email (409 Conflict):**
```json
{
  "error": "Conflict",
  "message": "Email already exists: john.doe@example.com",
  "timestamp": "2026-03-13T16:00:00"
}
```

**Invalid File Type (400 Bad Request):**
```json
{
  "error": "Bad Request",
  "message": "Invalid file type. Allowed types: image/jpeg, image/png, image/gif, image/bmp",
  "timestamp": "2026-03-13T16:00:00"
}
```

**File Too Large (400 Bad Request):**
```json
{
  "error": "Bad Request",
  "message": "File size exceeds maximum limit of 5 MB",
  "timestamp": "2026-03-13T16:00:00"
}
```

**Missing Required Fields (400 Bad Request):**
```json
{
  "error": "Bad Request",
  "message": "Validation failed: username: Username is required, email: Email is required",
  "timestamp": "2026-03-13T16:00:00"
}
```

#### Authentication After Registration

Once registered, customers can immediately authenticate using Basic Auth:

```bash
curl -u johndoe:SecurePass123 http://localhost:8080/api/test/protected
```

Expected response:
```json
{
  "message": "Access granted to protected endpoint",
  "username": "johndoe",
  "role": "CUSTOMER"
}
```

#### File Storage

- ID documents are stored in: `./uploads/id_documents/`
- File naming pattern: `{userId}_{UUID}.{extension}`
- Example: `usr_cust_a1b2c3d4e5f6_7g8h9i0j1k2l.jpg`
- The `uploads/` directory is excluded from git via `.gitignore`

#### Configuration

File upload settings can be configured in `application.yml`:

```yaml
storage:
  upload-dir: ./uploads
  id-documents-dir: ${storage.upload-dir}/id_documents
  max-file-size: 5242880  # 5 MB in bytes
  allowed-image-types:
    - image/jpeg
    - image/png
    - image/gif
    - image/bmp
  allowed-extensions:
    - jpg
    - jpeg
    - png
    - gif
    - bmp

spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 10MB
```

#### Troubleshooting

**Issue: "Could not create upload directory"**
- Ensure the application has write permissions to the directory
- Check that the path in `storage.upload-dir` is valid

**Issue: "File size exceeds maximum limit"**
- Check that the file is under 5 MB
- Verify `spring.servlet.multipart.max-file-size` configuration

**Issue: "Invalid file type"**
- Ensure the file is a valid image (JPEG, PNG, GIF, or BMP)
- Check that the file extension matches the actual file type
```

**Technical details:**
- Spring multipart configuration for file uploads
- Uploads directory excluded from version control
- Clear API documentation with curl examples
- Comprehensive error response examples
- Configuration reference for customization

---

## Technical Considerations

**Dependencies:**
- Story 1 (authentication and User entity)
- Spring Web (multipart file upload)
- Jakarta Validation for request validation
- BCrypt for password hashing

**Edge Cases:**
- Empty file upload (rejected)
- File with mismatched extension and MIME type (rejected)
- Concurrent registration with same username/email (database unique constraint)
- File storage failure during registration (transaction rollback)
- Very long filenames (handled by UUID naming)

**Testing Strategy:**
- Unit tests for FileStorageService validation logic
- Unit tests for RegistrationService business logic
- Integration tests for full registration flow
- Test authentication after registration
- Test all error scenarios (duplicate username, invalid file, etc.)

**Performance:**
- File I/O is synchronous (acceptable for MVP)
- Transaction ensures atomicity
- Indexed username and email for fast duplicate checks

**Security:**
- Passwords hashed with BCrypt before storage
- File type validation prevents malicious uploads
- File size limit prevents DoS attacks
- Unique constraints prevent duplicate accounts
- Uploads directory should have restricted permissions in production

## Success Criteria

- [ ] V3 migration adds id_document_path column to users table
- [ ] User entity supports storing ID document path
- [ ] FileStorageService validates file type (extension and MIME type)
- [ ] FileStorageService rejects files exceeding 5 MB
- [ ] FileStorageService stores files with unique names
- [ ] Upload directory is created automatically on startup
- [ ] RegistrationService checks for duplicate username
- [ ] RegistrationService checks for duplicate email
- [ ] RegistrationService hashes passwords with BCrypt
- [ ] RegistrationService generates unique user IDs
- [ ] RegistrationService assigns CUSTOMER role automatically
- [ ] Registration endpoint is public (no authentication required)
- [ ] Registration endpoint accepts multipart/form-data
- [ ] Registration returns 201 Created on success
- [ ] Registration returns 409 Conflict for duplicate username/email
- [ ] Registration returns 400 Bad Request for invalid file
- [ ] Registration returns 400 Bad Request for missing fields
- [ ] Newly registered customers can authenticate immediately
- [ ] Transaction rolls back if file storage fails
- [ ] All exception types have proper error responses
- [ ] README documents registration API with examples
- [ ] Uploads directory is excluded from git
- [ ] All tests pass successfully
