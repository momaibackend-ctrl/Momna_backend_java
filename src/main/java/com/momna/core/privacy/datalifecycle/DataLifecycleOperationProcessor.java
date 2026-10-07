package com.momna.core.privacy.datalifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.core.privacy.datalifecycle.infrastructure.*;
import com.momna.platform.storage.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataLifecycleOperationProcessor {
    public static final String CONTRACT_VERSION = "data-lifecycle.v1";
    public static final int EXPORT_SCHEMA_VERSION = 1;
    public static final String PRIVATE_EXPORT_BUCKET = "momna-lifecycle-export";

    private final DataLifecycleOperationRepository operations;
    private final DataLifecycleRetentionService retention;
    private final Map<String, DataLifecycleOwnerAdapter> owners;
    private final ObjectProvider<PrivateObjectStorage> storage;
    private final ObjectMapper mapper;

    public DataLifecycleOperationProcessor(
        DataLifecycleOperationRepository operations,
        DataLifecycleRetentionService retention,
        ObjectProvider<DataLifecycleOwnerAdapter> owners,
        ObjectProvider<PrivateObjectStorage> storage,
        ObjectMapper mapper
    ) {
        this.operations = operations;
        this.retention = retention;
        this.owners = owners.orderedStream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(DataLifecycleOwnerAdapter::owner, x -> x)
        );
        this.storage = storage;
        this.mapper = mapper;
    }

    @Transactional
    public DataLifecycleOperationEntity process(UUID operationId) {
        var operation = operations.findById(operationId)
            .orElseThrow(() -> new DataLifecycleRetentionService.DataLifecycleException(
                "RESOURCE_NOT_FOUND", "Lifecycle operation not found"
            ));
        if (operation.getState() == DataLifecycleOperationState.SUCCEEDED) return operation;

        operation.markRunning();
        operations.saveAndFlush(operation);

        try {
            switch (operation.getKind()) {
                case EXPORT -> processExport(operation);
                case ARCHIVE -> processArchive(operation);
                case RESTORE, DELETION -> throw new DataLifecycleRetentionService.DataLifecycleException(
                    "DEPENDENCY_UNAVAILABLE",
                    "Lifecycle operation owner execution is not migrated yet"
                );
            }
            operation.markSucceeded();
            return operations.saveAndFlush(operation);
        } catch (RuntimeException failure) {
            var code = failure instanceof DataLifecycleRetentionService.DataLifecycleException lifecycle
                ? lifecycle.code()
                : "PROCESSING_FAILED";
            operation.markFailed(code);
            operations.saveAndFlush(operation);
            throw failure;
        }
    }

    private void processArchive(DataLifecycleOperationEntity operation) {
        var objectStorage = storage.getIfAvailable();
        if (objectStorage == null) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "DEPENDENCY_UNAVAILABLE", "Private object storage is unavailable"
            );
        }
        if (operation.getResourceOwner() == null || operation.getResourceType() == null
            || operation.getResourceId() == null || operation.getPrivacyScope() == null
            || operation.getRetentionAnchorAt() == null) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "VALIDATION_ERROR", "Archive resource is incomplete"
            );
        }

        var policy = retention.resolvePolicy(
            operation.getResourceOwner(),
            operation.getResourceType(),
            operation.getPolicyVersion()
        );
        if (!policy.isArchiveAllowed()) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "ARCHIVE_NOT_ALLOWED", "Archive is not allowed by the current retention policy"
            );
        }

        var adapter = owners.get(operation.getResourceOwner());
        if (adapter == null) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "DEPENDENCY_UNAVAILABLE", "Lifecycle owner adapter is unavailable"
            );
        }

        var resource = new DataLifecycleOwnerAdapter.Resource(
            operation.getResourceOwner(),
            operation.getResourceType(),
            operation.getResourceId(),
            operation.getSubjectUserId(),
            operation.getPrivacyScope(),
            operation.getRetentionAnchorAt(),
            operation.getValidFrom(),
            operation.getValidUntil(),
            operation.getTimezoneAtEvent(),
            operation.getLocalDateAtEvent()
        );
        var fragment = adapter.archiveFragment(resource, operation.getRequestedAt());
        var extension = switch (fragment.mediaType()) {
            case "application/json" -> "json";
            case "application/pdf" -> "pdf";
            case "text/plain" -> "txt";
            default -> "bin";
        };

        var stored = objectStorage.put(
            operation.getSubjectUserId(),
            "momna-lifecycle-archive",
            "archive/" + operation.getOperationId() + "." + extension,
            fragment.bytes(),
            fragment.mediaType(),
            operation.getPrivacyScope() == com.momna.core.privacy.PrivacyScope.MEDICAL_PRIVATE
                ? AssetSensitivity.MEDICAL_PRIVATE
                : AssetSensitivity.PRIVATE
        );
        var artifact = new DataLifecycleOwnerAdapter.ArtifactRef(
            stored.bucket(), stored.key(), stored.contentType(), stored.sizeBytes()
        );
        var result = adapter.markArchived(
            resource,
            artifact,
            operation.getOperationId() + ":archive"
        );
        if (!Set.of("APPLIED", "ALREADY_APPLIED").contains(result.status())) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "VALIDATION_ERROR", "Owner did not accept archive state"
            );
        }
        operation.attachArtifact(
            stored.bucket(),
            stored.key(),
            stored.contentType(),
            stored.sizeBytes()
        );
    }

    private void processExport(DataLifecycleOperationEntity operation) {
        var objectStorage = storage.getIfAvailable();
        if (objectStorage == null) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "DEPENDENCY_UNAVAILABLE", "Private object storage is unavailable"
            );
        }

        var resources = new ArrayList<Map<String, Object>>();
        var includedOwners = new LinkedHashSet<String>();
        int included = 0;
        int excluded = 0;

        for (var adapter : owners.values().stream()
            .sorted(Comparator.comparing(DataLifecycleOwnerAdapter::owner))
            .toList()) {
            var inventory = adapter.inventory(operation.getSubjectUserId(), operation.getRequestedAt())
                .stream()
                .sorted(Comparator
                    .comparing(DataLifecycleOwnerAdapter.Resource::resourceType)
                    .thenComparing(DataLifecycleOwnerAdapter.Resource::resourceId))
                .toList();

            for (var resource : inventory) {
                var policy = retention.resolvePolicy(resource.owner(), resource.resourceType(), null);
                if (!policy.isExportAllowed()) {
                    excluded++;
                    continue;
                }

                var fragment = adapter.exportFragment(resource, operation.getRequestedAt());
                resources.add(Map.of(
                    "owner", resource.owner(),
                    "resourceType", resource.resourceType(),
                    "resourceId", resource.resourceId(),
                    "schemaVersion", fragment.schemaVersion(),
                    "mediaType", fragment.mediaType(),
                    "dataBase64", Base64.getEncoder().encodeToString(fragment.bytes())
                ));
                includedOwners.add(resource.owner());
                included++;
            }
        }

        var payload = new LinkedHashMap<String, Object>();
        payload.put("contractVersion", CONTRACT_VERSION);
        payload.put("schemaVersion", EXPORT_SCHEMA_VERSION);
        payload.put("snapshotAt", operation.getRequestedAt().toString());
        payload.put("resources", resources);

        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(payload);
        } catch (Exception failure) {
            throw new DataLifecycleRetentionService.DataLifecycleException(
                "VALIDATION_ERROR", "Export payload could not be serialized"
            );
        }

        var stored = objectStorage.put(
            operation.getSubjectUserId(),
            PRIVATE_EXPORT_BUCKET,
            "export/" + operation.getOperationId() + ".json",
            bytes,
            "application/json",
            AssetSensitivity.MEDICAL_PRIVATE
        );
        operation.attachArtifact(
            stored.bucket(),
            stored.key(),
            stored.contentType(),
            stored.sizeBytes()
        );
    }
}
