package com.flowcare.backend.common.controller

import com.flowcare.backend.auth.model.UserPrincipal
import com.flowcare.backend.common.dto.TestResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
class TestController {

    @GetMapping("/protected")
    fun protectedEndpoint(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<TestResponse> {
        return ResponseEntity.ok(
            TestResponse(
                message = "Access granted to protected endpoint",
                username = userPrincipal.username,
                role = userPrincipal.getUser().role.name
            )
        )
    }
}
