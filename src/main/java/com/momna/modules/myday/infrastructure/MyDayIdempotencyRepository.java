package com.momna.modules.myday.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MyDayIdempotencyRepository extends JpaRepository<MyDayIdempotencyEntity, MyDayIdempotencyId> {}
