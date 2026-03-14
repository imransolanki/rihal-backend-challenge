package com.flowcare.backend.auth.repository

import com.flowcare.backend.auth.model.Role
import com.flowcare.backend.auth.model.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.assertj.core.api.Assertions.assertThat

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `should save and find user by username`() {
        val user = User(
            id = "test_001",
            username = "testuser",
            password = "password",
            role = Role.CUSTOMER,
            fullName = "Test User",
            email = "test@example.com"
        )
        userRepository.save(user)

        val found = userRepository.findByUsername("testuser")
        assertThat(found).isPresent
        assertThat(found.get().fullName).isEqualTo("Test User")
    }

    @Test
    fun `should find user by email`() {
        val user = User(
            id = "test_002",
            username = "testuser2",
            password = "password",
            role = Role.STAFF,
            fullName = "Test Staff",
            email = "staff@example.com"
        )
        userRepository.save(user)

        val found = userRepository.findByEmail("staff@example.com")
        assertThat(found).isPresent
        assertThat(found.get().role).isEqualTo(Role.STAFF)
    }
}
