package com.flowcare.backend.common.exception

import com.flowcare.backend.appointment.exception.AppointmentNotFoundException
import com.flowcare.backend.appointment.exception.InvalidAppointmentStateException
import com.flowcare.backend.appointment.exception.SlotNotAvailableException
import com.flowcare.backend.auth.exception.EmailAlreadyExistsException
import com.flowcare.backend.auth.exception.UsernameAlreadyExistsException
import com.flowcare.backend.common.dto.ErrorResponse
import com.flowcare.backend.storage.exception.FileStorageException
import com.flowcare.backend.storage.exception.InvalidFileException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException): ResponseEntity<ErrorResponse> {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.message ?: "Authentication failed")
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.message ?: "Access denied")
    }

    @ExceptionHandler(UsernameAlreadyExistsException::class)
    fun handleUsernameAlreadyExists(ex: UsernameAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.message ?: "Username already exists")
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailAlreadyExists(ex: EmailAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.message ?: "Email already exists")
    }

    @ExceptionHandler(SlotNotAvailableException::class)
    fun handleSlotNotAvailable(ex: SlotNotAvailableException): ResponseEntity<ErrorResponse> {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.message ?: "Slot not available")
    }

    @ExceptionHandler(AppointmentNotFoundException::class)
    fun handleAppointmentNotFound(ex: AppointmentNotFoundException): ResponseEntity<ErrorResponse> {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.message ?: "Appointment not found")
    }

    @ExceptionHandler(InvalidAppointmentStateException::class)
    fun handleInvalidAppointmentState(ex: InvalidAppointmentStateException): ResponseEntity<ErrorResponse> {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.message ?: "Invalid appointment state")
    }

    @ExceptionHandler(InvalidFileException::class)
    fun handleInvalidFile(ex: InvalidFileException): ResponseEntity<ErrorResponse> {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.message ?: "Invalid file")
    }

    @ExceptionHandler(FileStorageException::class)
    fun handleFileStorage(ex: FileStorageException): ResponseEntity<ErrorResponse> {
        log.error("File storage error", ex)
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "File storage failed")
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(ex: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", "File size exceeds maximum limit")
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(ex: MissingServletRequestPartException): ResponseEntity<ErrorResponse> {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", "Required part '${ex.requestPartName}' is not present")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", "Validation failed: $errors")
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", ex)
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred")
    }

    private fun buildResponse(status: HttpStatus, error: String, message: String): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(status).body(
            ErrorResponse(error, message, LocalDateTime.now().toString())
        )
    }
}
