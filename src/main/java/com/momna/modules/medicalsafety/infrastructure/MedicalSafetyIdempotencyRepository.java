package com.momna.modules.medicalsafety.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalSafetyIdempotencyRepository
    extends JpaRepository<MedicalSafetyIdempotencyEntity, MedicalSafetyIdempotencyId> {}
