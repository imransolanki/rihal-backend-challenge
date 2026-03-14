package com.flowcare.backend.auth.repository

import com.flowcare.backend.auth.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, String> {
    fun findByUsername(username: String): Optional<User>
    fun findByEmail(email: String): Optional<User>
}
