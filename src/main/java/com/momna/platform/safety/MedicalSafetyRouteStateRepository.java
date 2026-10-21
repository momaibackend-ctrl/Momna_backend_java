package com.momna.platform.safety;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalSafetyRouteStateRepository
    extends JpaRepository<MedicalSafetyRouteStateEntity, String> {
    List<MedicalSafetyRouteStateEntity> findByUserId(String userId);
    Optional<MedicalSafetyRouteStateEntity> findByUserIdAndRouteId(String userId, String routeId);
    List<MedicalSafetyRouteStateEntity> findByUserIdAndSessionId(String userId, String sessionId);
}
