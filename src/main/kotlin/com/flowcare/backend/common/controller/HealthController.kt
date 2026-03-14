package com.flowcare.backend.common.controller

import com.flowcare.backend.common.dto.HealthResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/health")
class HealthController {

    @GetMapping
    fun health(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "UP",
                timestamp = LocalDateTime.now().toString()
            )
        )
    }
}
