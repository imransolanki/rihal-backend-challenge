package com.flowcare.backend.auth.exception

class UsernameAlreadyExistsException(username: String) : RuntimeException("Username already exists: $username")
