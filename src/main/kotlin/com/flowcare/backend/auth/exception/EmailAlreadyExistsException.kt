package com.flowcare.backend.auth.exception

class EmailAlreadyExistsException(email: String) : RuntimeException("Email already exists: $email")
