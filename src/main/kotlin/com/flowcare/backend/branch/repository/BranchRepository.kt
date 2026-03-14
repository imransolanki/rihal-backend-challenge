package com.flowcare.backend.branch.repository

import com.flowcare.backend.branch.model.Branch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BranchRepository : JpaRepository<Branch, String>
