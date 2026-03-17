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
        @RequestParam username: String,
        @RequestParam password: String,
        @RequestParam fullName: String,
        @RequestParam email: String,
        @RequestParam phone: String,
        @RequestParam("idDocument") idDocument: MultipartFile
    ): ResponseEntity<RegistrationResponse> {
        val request = RegistrationRequest(
            username = username,
            password = password,
            fullName = fullName,
            email = email,
            phone = phone
        )
        val response = registrationService.registerCustomer(request, idDocument)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}
