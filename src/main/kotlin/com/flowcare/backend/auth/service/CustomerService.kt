package com.flowcare.backend.auth.service

import com.flowcare.backend.appointment.repository.AppointmentRepository
import com.flowcare.backend.auth.dto.CustomerDetailResponse
import com.flowcare.backend.auth.dto.CustomerListResponse
import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class CustomerService(
    private val userRepository: UserRepository,
    private val appointmentRepository: AppointmentRepository
) {
    
    fun listCustomers(pageable: Pageable): Page<CustomerListResponse> {
        return userRepository.findByRole(Role.CUSTOMER, pageable)
            .map { user ->
                CustomerListResponse(
                    id = user.id,
                    fullName = user.fullName,
                    email = user.email,
                    phone = user.phone,
                    registeredAt = user.createdAt
                )
            }
    }
    
    fun listCustomersByBranch(branchId: String, pageable: Pageable): Page<CustomerListResponse> {
        return userRepository.findCustomersWithAppointmentsAtBranch(branchId, pageable)
            .map { user ->
                CustomerListResponse(
                    id = user.id,
                    fullName = user.fullName,
                    email = user.email,
                    phone = user.phone,
                    registeredAt = user.createdAt
                )
            }
    }
    
    fun getCustomerDetail(customerId: String): CustomerDetailResponse {
        val user = userRepository.findById(customerId)
            .orElseThrow { IllegalArgumentException("Customer not found") }
        
        if (user.role != Role.CUSTOMER) {
            throw IllegalArgumentException("User is not a customer")
        }
        
        val appointmentCount = appointmentRepository.countByCustomerId(customerId)
        
        return CustomerDetailResponse(
            id = user.id,
            fullName = user.fullName,
            email = user.email,
            phone = user.phone,
            idDocumentPath = user.idDocumentPath,
            registeredAt = user.createdAt,
            appointmentCount = appointmentCount
        )
    }
}
