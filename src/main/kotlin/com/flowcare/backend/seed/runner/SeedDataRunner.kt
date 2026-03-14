package com.flowcare.backend.seed.runner

import com.flowcare.backend.seed.service.SeedService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["seed.enabled"], havingValue = "true", matchIfMissing = false)
class SeedDataRunner(
    private val seedService: SeedService,
    @Value("\${seed.file-path}") private val filePath: String
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(SeedDataRunner::class.java)

    override fun run(args: ApplicationArguments) {
        log.info("Seed data runner triggered")
        seedService.seedDatabase(filePath)
    }
}
