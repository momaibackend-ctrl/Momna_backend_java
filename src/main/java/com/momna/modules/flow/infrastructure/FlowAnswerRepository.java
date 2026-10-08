package com.momna.modules.flow.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowAnswerRepository extends JpaRepository<FlowAnswerEntity, Long> {
    List<FlowAnswerEntity> findByInstanceIdAndActiveTrueOrderByAnsweredAtAscAnswerIdAsc(UUID instanceId);
    List<FlowAnswerEntity> findByInstanceIdOrderByAnsweredAtAscAnswerIdAsc(UUID instanceId);
}
