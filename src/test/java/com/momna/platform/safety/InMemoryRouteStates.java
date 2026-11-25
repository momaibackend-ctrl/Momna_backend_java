package com.momna.platform.safety;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A route-state repository over a map: the flow's persistence behaviour without a database. */
final class InMemoryRouteStates {
    final Map<String, MedicalSafetyRouteStateEntity> rows = new LinkedHashMap<>();
    final MedicalSafetyRouteStateRepository repository = mock(MedicalSafetyRouteStateRepository.class);
    final List<MedicalSafetyFactRevisionEntity> revisionRows = new ArrayList<>();
    final MedicalSafetyFactRevisionRepository revisions = mock(MedicalSafetyFactRevisionRepository.class);

    InMemoryRouteStates() {
        when(repository.findByUserId(anyString())).thenAnswer(call -> rows.values().stream()
            .filter(r -> r.getUserId().equals(call.getArgument(0))).toList());
        when(repository.findByUserIdAndRouteId(anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(
            rows.get(call.getArgument(0) + ":" + call.getArgument(1))));
        when(repository.findByUserIdAndSessionId(anyString(), anyString())).thenAnswer(call -> rows.values().stream()
            .filter(r -> r.getUserId().equals(call.getArgument(0))
                && Objects.equals(r.getSessionId(), call.getArgument(1)))
            .toList());
        when(repository.save(any(MedicalSafetyRouteStateEntity.class))).thenAnswer(call -> store(call.getArgument(0)));
        when(repository.saveAndFlush(any(MedicalSafetyRouteStateEntity.class)))
            .thenAnswer(call -> store(call.getArgument(0)));
        when(revisions.save(any(MedicalSafetyFactRevisionEntity.class))).thenAnswer(call -> {
            revisionRows.add(call.getArgument(0));
            return call.getArgument(0);
        });
    }

    private MedicalSafetyRouteStateEntity store(MedicalSafetyRouteStateEntity row) {
        rows.put(row.getUserId() + ":" + row.getRouteId(), row);
        return row;
    }

    MedicalSafetyRouteStateEntity row(String userId, String routeId) {
        return rows.get(userId + ":" + routeId);
    }
}
