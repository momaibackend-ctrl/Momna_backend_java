package com.momna.modules.flow.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowOperationResultRepository
    extends JpaRepository<FlowOperationResultEntity, FlowOperationResultId> {}
