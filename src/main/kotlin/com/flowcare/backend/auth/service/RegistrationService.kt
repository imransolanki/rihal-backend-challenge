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
    private val log = LoggerFactory.getLogger(RegistrationService::class.java)

    @Transactional
    fun registerCustomer(request: RegistrationRequest, idDocument: MultipartFile): RegistrationResponse {
        log.info("Registering new customer: {}", request.username)

        validateUniqueness(request)

        val userId = "usr_cust_${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}"
        val idDocumentPath = fileStorageService.storeIdDocument(idDocument, userId)

        val user = User(
            id = userId,
            username = request.username,
            password = passwordEncoder.encode(request.password),
            role = Role.CUSTOMER,
            fullName = request.fullName,
            email = request.email,
            phone = request.phone,
            idDocumentPath = idDocumentPath
        )

        val saved = userRepository.save(user)
        log.info("Customer registered: {}", saved.id)

        return RegistrationResponse(
            id = saved.id,
            username = saved.username,
            fullName = saved.fullName,
            email = saved.email,
            phone = saved.phone ?: ""
        )
    }

    private fun validateUniqueness(request: RegistrationRequest) {
        if (userRepository.findByUsername(request.username).isPresent) {
            throw UsernameAlreadyExistsException(request.username)
        }
        if (userRepository.findByEmail(request.email).isPresent) {
            throw EmailAlreadyExistsException(request.email)
        }
    }
}
