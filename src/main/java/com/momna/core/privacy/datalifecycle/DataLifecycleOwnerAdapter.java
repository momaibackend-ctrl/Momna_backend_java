package com.momna.core.privacy.datalifecycle;

import com.momna.core.privacy.PrivacyScope;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface DataLifecycleOwnerAdapter {
    String owner();
    List<Resource> inventory(String subjectUserId, Instant snapshotAt);
    Fragment exportFragment(Resource resource, Instant snapshotAt);
    Fragment archiveFragment(Resource resource, Instant snapshotAt);
    ActionResult applyDeletion(Resource resource, DeletionAction action, String operationKey);
    ActionResult restore(Resource resource, ArtifactRef artifactRef, String operationKey);

    default ActionResult markArchived(Resource resource, ArtifactRef artifactRef, String operationKey) {
        return new ActionResult("APPLIED", "ARCHIVE_RECORDED");
    }

    default ActionResult invalidateDerivedCopies(String subjectUserId, String operationKey) {
        return new ActionResult("APPLIED", "DERIVED_INVALIDATED");
    }

    record Resource(
        String owner,
        String resourceType,
        String resourceId,
        String subjectUserId,
        PrivacyScope privacyScope,
        Instant retentionAnchorAt,
        Instant validFrom,
        Instant validUntil,
        String timezoneAtEvent,
        LocalDate localDateAtEvent
    ) {}

    record Fragment(int schemaVersion, String mediaType, byte[] bytes) {
        public Fragment {
            if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
            if (mediaType == null || !mediaType.matches("[A-Za-z0-9.+/-]{3,120}")) {
                throw new IllegalArgumentException("Invalid fragment media type");
            }
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }
    }

    record ArtifactRef(String bucket, String key, String contentType, long sizeBytes) {}

    record ActionResult(String status, String resultCode) {
        public ActionResult {
            if (resultCode == null || !resultCode.matches("[A-Z][A-Z0-9_]{1,79}")) {
                throw new IllegalArgumentException("Invalid lifecycle result code");
            }
        }
    }
}
