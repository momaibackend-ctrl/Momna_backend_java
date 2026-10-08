package com.momna.modules.lifecycle.application;

import com.momna.modules.lifecycle.domain.*;
import com.momna.modules.lifecycle.infrastructure.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LifecycleQueryService {
    private final LifecyclePeriodHistoryRepository historyRepository;
    private final LifecycleContextRepository contextRepository;

    public LifecycleQueryService(
        LifecyclePeriodHistoryRepository historyRepository,
        LifecycleContextRepository contextRepository
    ) {
        this.historyRepository = historyRepository;
        this.contextRepository = contextRepository;
    }

    public LifecycleSnapshot current(String userId, Instant at) {
        requireUserId(userId);
        var primary = historyRepository.findCurrent(userId, at).stream()
            .findFirst()
            .map(this::toDomain)
            .filter(entry -> !postpartumWindowEnded(entry, at))
            .orElse(null);

        var contexts = contextRepository.findActive(userId, at).stream()
            .map(this::toDomain)
            .toList();

        return new LifecycleSnapshot(primary, contexts);
    }

    public List<LifecycleEntry> history(String userId) {
        requireUserId(userId);
        return historyRepository.findByUserIdOrderByEffectiveFromAsc(userId).stream()
            .map(this::toDomain)
            .toList();
    }

    private LifecycleEntry toDomain(LifecyclePeriodHistoryEntity e) {
        return new LifecycleEntry(
            e.getId(), e.getUserId(), e.getPeriod(), e.getSubstage(),
            e.getEffectiveFrom(), e.getEffectiveTo(), e.getSource(),
            e.getConfidence(), e.isSelectedManually()
        );
    }

    private LifecycleContext toDomain(LifecycleContextEntity e) {
        return new LifecycleContext(
            e.getId(), e.getUserId(), e.getContextType(),
            e.getValidFrom(), e.getValidTo(), e.getSource(), e.getConfidence()
        );
    }

    private boolean postpartumWindowEnded(LifecycleEntry entry, Instant at) {
        return entry.period() == LifecyclePeriod.POSTPARTUM
            && !at.isBefore(entry.effectiveFrom().atZone(ZoneOffset.UTC).plusMonths(12).toInstant());
    }

    private void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
    }
}
