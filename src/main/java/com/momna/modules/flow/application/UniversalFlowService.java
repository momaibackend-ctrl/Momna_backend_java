package com.momna.modules.flow.application;

import com.momna.modules.flow.domain.*;
import com.momna.modules.flow.infrastructure.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UniversalFlowService {
    private final FlowDefinitionRepository definitions;
    private final FlowInstanceRepository instances;
    private final FlowAnswerRepository answers;
    private final FlowOperationResultRepository operations;
    private final Clock clock = Clock.systemUTC();

    public UniversalFlowService(
        FlowDefinitionRepository definitions,
        FlowInstanceRepository instances,
        FlowAnswerRepository answers,
        FlowOperationResultRepository operations
    ) {
        this.definitions = definitions;
        this.instances = instances;
        this.answers = answers;
        this.operations = operations;
    }

    @Transactional
    public FlowState start(
        String userId,
        String definitionKey,
        String operationId,
        Integer definitionVersion,
        String variant,
        String period,
        String substage,
        String transitionScopeId
    ) {
        requireText(userId, "userId");
        requireText(definitionKey, "definitionKey");
        requireText(operationId, "operationId");

        var replay = instances.findByUserIdAndDefinitionKeyAndStartOperationId(
            userId, definitionKey, operationId
        ).orElse(null);
        if (replay != null) return state(replay, true, Set.of());

        var definition = loadDefinition(definitionKey, definitionVersion);
        var model = FlowDefinitionModel.from(definition);
        var now = clock.instant();

        var instance = new FlowInstanceEntity(
            UUID.randomUUID(),
            definition.getDefinitionKey(),
            definition.getDefinitionVersion(),
            definition.getFlowType(),
            userId,
            variant == null ? definition.getVariant() : variant,
            period == null ? definition.getPeriod() : period,
            substage == null ? definition.getSubstage() : substage,
            transitionScopeId,
            FlowInstanceStatus.IN_PROGRESS,
            model.steps().getFirst().stepId(),
            operationId,
            now,
            now
        );

        try {
            instance = instances.saveAndFlush(instance);
        } catch (RuntimeException conflict) {
            var existing = instances.findByUserIdAndDefinitionKeyAndStartOperationId(
                userId, definitionKey, operationId
            ).orElseThrow(() -> conflict);
            return state(existing, true, Set.of());
        }

        return state(instance, false, Set.of());
    }

    @Transactional(readOnly = true)
    public FlowState resume(String userId, UUID instanceId) {
        return state(owned(userId, instanceId), false, Set.of());
    }

    @Transactional
    public FlowState answer(
        String userId,
        UUID instanceId,
        String fieldId,
        Map<String, Object> payload,
        boolean change,
        int expectedDefinitionVersion,
        long expectedRevision,
        String operationId
    ) {
        requireText(fieldId, "fieldId");
        requireText(operationId, "operationId");
        if (payload == null) throw new FlowException("VALIDATION", "value is required");

        var replay = operation(instanceId, operationId);
        if (replay != null) return replay.withReplay();

        var instance = mutable(userId, instanceId, expectedDefinitionVersion, expectedRevision);
        var definition = FlowDefinitionModel.from(loadDefinition(instance.getDefinitionKey(), instance.getDefinitionVersion()));
        var activeBefore = activeSteps(definition, instanceId);
        var step = activeBefore.stream()
            .filter(x -> x.fieldId().equals(fieldId))
            .findFirst()
            .orElseThrow(() -> new FlowException("INVALID_TRANSITION", "Field is not active in flow state"));

        var active = answers.findByInstanceIdAndActiveTrueOrderByAnsweredAtAscAnswerIdAsc(instanceId);
        var existing = active.stream().filter(x -> x.getFieldId().equals(fieldId)).findFirst().orElse(null);
        if (!change && existing != null && existing.getAnswerStatus() == FlowAnswerStatus.ANSWERED) {
            throw new FlowException("CONFLICT", "Answer already exists");
        }
        if (existing != null) {
            existing.deactivate(clock.instant());
            answers.save(existing);
        }

        answers.save(new FlowAnswerEntity(
            instanceId, fieldId, payload, FlowAnswerStatus.ANSWERED, operationId, clock.instant()
        ));

        var invalidated = deactivateInactive(definition, instanceId);
        var next = nextStep(definition, instanceId);
        instance.moveTo(next == null ? null : next.stepId(), clock.instant());
        saveVersioned(instance);

        var result = state(instance, false, invalidated);
        saveOperation(instanceId, operationId, result);
        return result;
    }

    @Transactional
    public FlowState skip(
        String userId,
        UUID instanceId,
        String stepId,
        int expectedDefinitionVersion,
        long expectedRevision,
        String operationId
    ) {
        requireText(stepId, "stepId");
        requireText(operationId, "operationId");

        var replay = operation(instanceId, operationId);
        if (replay != null) return replay.withReplay();

        var instance = mutable(userId, instanceId, expectedDefinitionVersion, expectedRevision);
        var definition = FlowDefinitionModel.from(loadDefinition(instance.getDefinitionKey(), instance.getDefinitionVersion()));
        var step = activeSteps(definition, instanceId).stream()
            .filter(x -> x.stepId().equals(stepId))
            .findFirst()
            .orElseThrow(() -> new FlowException("INVALID_TRANSITION", "Step is not active in flow state"));
        if (!step.skippable()) {
            throw new FlowException("INVALID_TRANSITION", "Step is not skippable");
        }

        var existing = answers.findByInstanceIdAndActiveTrueOrderByAnsweredAtAscAnswerIdAsc(instanceId)
            .stream().filter(x -> x.getFieldId().equals(step.fieldId())).findFirst().orElse(null);
        if (existing != null) {
            existing.deactivate(clock.instant());
            answers.save(existing);
        }

        answers.save(new FlowAnswerEntity(
            instanceId, step.fieldId(), null, FlowAnswerStatus.SKIPPED, operationId, clock.instant()
        ));

        var invalidated = deactivateInactive(definition, instanceId);
        var next = nextStep(definition, instanceId);
        instance.moveTo(next == null ? null : next.stepId(), clock.instant());
        saveVersioned(instance);

        var result = state(instance, false, invalidated);
        saveOperation(instanceId, operationId, result);
        return result;
    }

    @Transactional
    public FlowState back(
        String userId,
        UUID instanceId,
        int expectedDefinitionVersion,
        long expectedRevision,
        String operationId
    ) {
        var replay = operation(instanceId, operationId);
        if (replay != null) return replay.withReplay();

        var instance = mutable(userId, instanceId, expectedDefinitionVersion, expectedRevision);
        var definition = FlowDefinitionModel.from(loadDefinition(instance.getDefinitionKey(), instance.getDefinitionVersion()));
        var active = answers.findByInstanceIdAndActiveTrueOrderByAnsweredAtAscAnswerIdAsc(instanceId);
        var answered = active.stream().map(FlowAnswerEntity::getFieldId).collect(java.util.stream.Collectors.toSet());

        FlowDefinitionModel.Step target = null;
        var activeSteps = activeSteps(definition, instanceId);
        for (var step : activeSteps) {
            if (step.stepId().equals(instance.getCurrentStepId())) break;
            if (answered.contains(step.fieldId())) target = step;
        }
        if (target == null && !activeSteps.isEmpty()) target = activeSteps.getFirst();
        if (target == null) throw new FlowException("INVALID_TRANSITION", "Flow has no active steps");

        instance.moveTo(target.stepId(), clock.instant());
        saveVersioned(instance);

        var result = state(instance, false, Set.of());
        saveOperation(instanceId, operationId, result);
        return result;
    }

    @Transactional
    public FlowResult complete(
        String userId,
        UUID instanceId,
        int expectedDefinitionVersion,
        long expectedRevision,
        String operationId
    ) {
        var instance = mutable(userId, instanceId, expectedDefinitionVersion, expectedRevision);
        var definition = FlowDefinitionModel.from(loadDefinition(instance.getDefinitionKey(), instance.getDefinitionVersion()));
        var active = answers.findByInstanceIdAndActiveTrueOrderByAnsweredAtAscAnswerIdAsc(instanceId);
        var answered = active.stream()
            .filter(x -> x.getAnswerStatus() == FlowAnswerStatus.ANSWERED)
            .map(FlowAnswerEntity::getFieldId)
            .collect(java.util.stream.Collectors.toSet());

        var missing = activeSteps(definition, instanceId).stream()
            .filter(FlowDefinitionModel.Step::required)
            .filter(x -> !answered.contains(x.fieldId()))
            .map(FlowDefinitionModel.Step::fieldId)
            .toList();
        if (!missing.isEmpty()) {
            throw new FlowException("INVALID_TRANSITION", "Required answers are missing");
        }

        instance.complete(clock.instant());
        saveVersioned(instance);
        return result(userId, instanceId);
    }

    @Transactional(readOnly = true)
    public FlowResult result(String userId, UUID instanceId) {
        var instance = owned(userId, instanceId);
        var definition = FlowDefinitionModel.from(loadDefinition(instance.getDefinitionKey(), instance.getDefinitionVersion()));
        var values = new LinkedHashMap<String, Map<String, Object>>();
        for (var answer : answers.findByInstanceIdAndActiveTrueOrderByAnsweredAtAscAnswerIdAsc(instanceId)) {
            if (answer.getAnswerStatus() == FlowAnswerStatus.ANSWERED && answer.getPayload() != null) {
                values.put(answer.getFieldId(), answer.getPayload());
            }
        }
        return new FlowResult(
            instance.getInstanceId(),
            instance.getDefinitionKey(),
            instance.getDefinitionVersion(),
            definition.resultKind(),
            instance.getStatus() == FlowInstanceStatus.COMPLETED,
            Map.copyOf(values),
            instance.getTransitionScopeId()
        );
    }

    private FlowDefinitionEntity loadDefinition(String key, Integer version) {
        if (version != null) {
            return definitions.findById(new FlowDefinitionId(key, version))
                .orElseThrow(() -> new FlowException("NOT_FOUND", "Flow definition not found"));
        }
        return definitions.findByDefinitionKeyOrderByDefinitionVersionDesc(key).stream()
            .findFirst()
            .orElseThrow(() -> new FlowException("NOT_FOUND", "Flow definition not found"));
    }

    private FlowInstanceEntity owned(String userId, UUID instanceId) {
        var instance = instances.findById(instanceId)
            .orElseThrow(() -> new FlowException("NOT_FOUND", "Flow instance not found"));
        if (!instance.getUserId().equals(userId)) {
            throw new FlowException("FORBIDDEN", "Flow instance is not accessible");
        }
        return instance;
    }

    private FlowInstanceEntity mutable(
        String userId, UUID instanceId, int expectedDefinitionVersion, long expectedRevision
    ) {
        var instance = owned(userId, instanceId);
        if (instance.getDefinitionVersion() != expectedDefinitionVersion) {
            throw new FlowException("STALE_VERSION", "Flow definition version changed");
        }
        if (instance.getRowVersion() != expectedRevision) {
            throw new FlowException("CONFLICT", "Flow revision conflict");
        }
        if (instance.getStatus() != FlowInstanceStatus.IN_PROGRESS) {
            throw new FlowException("INVALID_TRANSITION", "Flow is not mutable");
        }
        return instance;
    }

    private FlowDefinitionModel.Step nextStep(FlowDefinitionModel definition, UUID instanceId) {
        var active = answers.findByInstanceIdAndActiveTrueOrderByAnsweredAtAscAnswerIdAsc(instanceId);
        var resolved = active.stream()
            .filter(x -> x.getAnswerStatus() == FlowAnswerStatus.ANSWERED
                || x.getAnswerStatus() == FlowAnswerStatus.SKIPPED)
            .map(FlowAnswerEntity::getFieldId)
            .collect(java.util.stream.Collectors.toSet());
        return activeSteps(definition, instanceId).stream()
            .filter(x -> !resolved.contains(x.fieldId()))
            .findFirst()
            .orElse(null);
    }

    private List<FlowDefinitionModel.Step> activeSteps(
        FlowDefinitionModel definition,
        UUID instanceId
    ) {
        var values = activeAnswerPayloads(instanceId);
        return definition.steps().stream()
            .filter(step -> FlowConditionEvaluator.matches(
                definition.conditions().get(step.fieldId()),
                values
            ))
            .toList();
    }

    private Map<String, Map<String, Object>> activeAnswerPayloads(UUID instanceId) {
        var values = new LinkedHashMap<String, Map<String, Object>>();
        for (var answer : answers.findByInstanceIdAndActiveTrueOrderByAnsweredAtAscAnswerIdAsc(instanceId)) {
            if (answer.getAnswerStatus() == FlowAnswerStatus.ANSWERED && answer.getPayload() != null) {
                values.put(answer.getFieldId(), answer.getPayload());
            }
        }
        return values;
    }

    private Set<String> deactivateInactive(
        FlowDefinitionModel definition,
        UUID instanceId
    ) {
        var activeFields = activeSteps(definition, instanceId).stream()
            .map(FlowDefinitionModel.Step::fieldId)
            .collect(java.util.stream.Collectors.toSet());
        var invalidated = new LinkedHashSet<String>();
        var now = clock.instant();

        for (var answer : answers.findByInstanceIdAndActiveTrueOrderByAnsweredAtAscAnswerIdAsc(instanceId)) {
            if (!activeFields.contains(answer.getFieldId())) {
                answer.deactivate(now);
                answers.save(answer);
                invalidated.add(answer.getFieldId());
            }
        }
        return Set.copyOf(invalidated);
    }

    private void saveVersioned(FlowInstanceEntity instance) {
        try {
            instances.saveAndFlush(instance);
        } catch (OptimisticLockingFailureException conflict) {
            throw new FlowException("CONFLICT", "Flow revision conflict");
        }
    }

    private FlowState state(FlowInstanceEntity instance, boolean replayed, Set<String> invalidated) {
        var definition = FlowDefinitionModel.from(loadDefinition(instance.getDefinitionKey(), instance.getDefinitionVersion()));
        var current = activeSteps(definition, instance.getInstanceId()).stream()
            .filter(x -> Objects.equals(x.stepId(), instance.getCurrentStepId()))
            .findFirst()
            .orElse(null);
        return new FlowState(
            instance.getInstanceId(),
            instance.getDefinitionKey(),
            instance.getDefinitionVersion(),
            instance.getFlowType().name(),
            instance.getPeriod(),
            instance.getRowVersion(),
            instance.getStatus().name(),
            current,
            Set.copyOf(invalidated),
            replayed
        );
    }

    private FlowState operation(UUID instanceId, String operationId) {
        return operations.findById(new FlowOperationResultId(instanceId, operationId))
            .map(x -> FlowState.fromMap(x.getResultJson()))
            .orElse(null);
    }

    private void saveOperation(UUID instanceId, String operationId, FlowState state) {
        if (!operations.existsById(new FlowOperationResultId(instanceId, operationId))) {
            operations.save(new FlowOperationResultEntity(instanceId, operationId, state.toMap()));
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new FlowException("VALIDATION", field + " is required");
        }
    }

    public record FlowState(
        UUID instanceId,
        String definitionKey,
        int definitionVersion,
        String flowType,
        String period,
        long revision,
        String status,
        FlowDefinitionModel.Step currentStep,
        Set<String> invalidatedFieldIds,
        boolean idempotentReplay
    ) {
        public FlowState withReplay() {
            return new FlowState(
                instanceId, definitionKey, definitionVersion, flowType, period,
                revision, status, currentStep, invalidatedFieldIds, true
            );
        }

        public Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("instanceId", instanceId.toString());
            map.put("definitionKey", definitionKey);
            map.put("definitionVersion", definitionVersion);
            map.put("flowType", flowType);
            if (period != null) map.put("period", period);
            map.put("revision", revision);
            map.put("status", status);
            if (currentStep != null) {
                map.put("currentStep", Map.of(
                    "stepId", currentStep.stepId(),
                    "fieldId", currentStep.fieldId(),
                    "required", currentStep.required(),
                    "skippable", currentStep.skippable()
                ));
            }
            map.put("invalidatedFieldIds", invalidatedFieldIds);
            map.put("idempotentReplay", idempotentReplay);
            return map;
        }

        @SuppressWarnings("unchecked")
        public static FlowState fromMap(Map<String, Object> map) {
            var step = (Map<String, Object>) map.get("currentStep");
            FlowDefinitionModel.Step current = step == null ? null : new FlowDefinitionModel.Step(
                String.valueOf(step.get("stepId")),
                String.valueOf(step.get("fieldId")),
                Boolean.TRUE.equals(step.get("required")),
                Boolean.TRUE.equals(step.get("skippable"))
            );
            var invalidated = map.get("invalidatedFieldIds") instanceof Collection<?> c
                ? c.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet())
                : Set.<String>of();
            return new FlowState(
                UUID.fromString(String.valueOf(map.get("instanceId"))),
                String.valueOf(map.get("definitionKey")),
                ((Number) map.get("definitionVersion")).intValue(),
                String.valueOf(map.get("flowType")),
                map.get("period") == null ? null : String.valueOf(map.get("period")),
                ((Number) map.get("revision")).longValue(),
                String.valueOf(map.get("status")),
                current,
                invalidated,
                Boolean.TRUE.equals(map.get("idempotentReplay"))
            );
        }
    }

    public record FlowResult(
        UUID instanceId,
        String definitionKey,
        int definitionVersion,
        String resultKind,
        boolean completed,
        Map<String, Map<String, Object>> answers,
        String transitionScopeId
    ) {}
}
