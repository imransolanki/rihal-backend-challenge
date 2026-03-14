package com.flowcare.backend.slot.repository

import com.flowcare.backend.slot.model.Slot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SlotRepository : JpaRepository<Slot, String>
