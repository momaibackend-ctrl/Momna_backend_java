package com.momna.modules.checkin.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinIdempotencyRepository
    extends JpaRepository<CheckinIdempotencyEntity, CheckinIdempotencyId> {}
