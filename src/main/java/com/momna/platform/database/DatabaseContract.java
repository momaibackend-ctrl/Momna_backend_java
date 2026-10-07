package com.momna.platform.database;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class DatabaseContract {
    private DatabaseContract() {}

    public enum PersistenceErrorCode {
        NOT_FOUND,
        VERSION_CONFLICT,
        DUPLICATE_IDEMPOTENCY_KEY,
        VALIDATION_ERROR,
        TIMEOUT,
        PROVIDER_UNAVAILABLE
    }

    public static class PersistenceException extends RuntimeException {
        private final PersistenceErrorCode code;

        public PersistenceException(PersistenceErrorCode code, String message) {
            super(message);
            this.code = code;
        }

        public PersistenceException(
            PersistenceErrorCode code,
            String message,
            Throwable cause
        ) {
            super(message, cause);
            this.code = code;
        }

        public PersistenceErrorCode code() {
            return code;
        }
    }

    public record ExpectedVersion(long value) {
        public ExpectedVersion {
            if (value < 0) {
                throw new IllegalArgumentException(
                    "Expected version must be non-negative"
                );
            }
        }
    }

    public record IdempotencyKey(String value) {
        public IdempotencyKey {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                    "Idempotency key must not be blank"
                );
            }
        }
    }

    public enum SortDirection {
        ASC, DESC
    }

    public record SortSpec(String field, SortDirection direction) {
        public SortSpec {
            if (field == null || !field.matches("[a-zA-Z0-9_]+")) {
                throw new IllegalArgumentException("Invalid sort field");
            }
            if (direction == null) direction = SortDirection.ASC;
        }
    }

    public record PageRequest(
        int limit,
        String cursor,
        List<SortSpec> sort
    ) {
        public PageRequest {
            if (limit < 1 || limit > 200) {
                throw new IllegalArgumentException(
                    "Page limit must be between 1 and 200"
                );
            }
            sort = sort == null ? List.of() : List.copyOf(sort);
        }

        public static PageRequest defaultPage() {
            return new PageRequest(50, null, List.of());
        }
    }

    public record Page<T>(
        List<T> items,
        String nextCursor
    ) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public enum WriteSemantics {
        CREATE, UPSERT
    }

    @FunctionalInterface
    public interface TransactionContract {
        <T> T inTransaction(Supplier<T> block);
    }

    public interface RepositoryContract {}

    public interface FindByIdRepository<ID, ENTITY>
        extends RepositoryContract {
        ENTITY findById(ID id);
    }

    public interface SaveRepository<ENTITY>
        extends RepositoryContract {
        ENTITY save(ENTITY entity);
    }

    public record PersistenceMetadata(
        UUID id,
        Instant createdAt,
        Instant updatedAt,
        Long version
    ) {}
}
