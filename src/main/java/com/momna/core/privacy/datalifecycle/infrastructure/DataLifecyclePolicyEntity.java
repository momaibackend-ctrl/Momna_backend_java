package com.momna.core.privacy.datalifecycle.infrastructure;

import com.momna.core.privacy.PrivacyScope;
import com.momna.core.privacy.datalifecycle.*;
import jakarta.persistence.*;

@Entity
@Table(name = "data_lifecycle_policies", schema = "momna")
@IdClass(DataLifecyclePolicyId.class)
public class DataLifecyclePolicyEntity {
    @Id
    @Column(name = "policy_key")
    private String policyKey;

    @Id
    @Column(name = "policy_version")
    private int policyVersion;

    @Column(nullable = false)
    private String owner;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy_scope", nullable = false)
    private PrivacyScope privacyScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "retention_mode", nullable = false)
    private RetentionMode retentionMode;

    @Column(name = "retain_for_seconds")
    private Long retainForSeconds;

    @Column(name = "archive_after_seconds")
    private Long archiveAfterSeconds;

    @Column(name = "delete_after_seconds")
    private Long deleteAfterSeconds;

    @Column(name = "archive_allowed", nullable = false)
    private boolean archiveAllowed;

    @Column(name = "restore_allowed", nullable = false)
    private boolean restoreAllowed;

    @Column(name = "export_allowed", nullable = false)
    private boolean exportAllowed;

    @Column(name = "deletion_allowed", nullable = false)
    private boolean deletionAllowed;

    @Enumerated(EnumType.STRING)
    @Column(name = "deletion_action", nullable = false)
    private DeletionAction deletionAction;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DataLifecyclePolicyEntity() {}

    public String getPolicyKey() { return policyKey; }
    public int getPolicyVersion() { return policyVersion; }
    public String getOwner() { return owner; }
    public String getResourceType() { return resourceType; }
    public PrivacyScope getPrivacyScope() { return privacyScope; }
    public RetentionMode getRetentionMode() { return retentionMode; }
    public Long getRetainForSeconds() { return retainForSeconds; }
    public Long getArchiveAfterSeconds() { return archiveAfterSeconds; }
    public Long getDeleteAfterSeconds() { return deleteAfterSeconds; }
    public boolean isArchiveAllowed() { return archiveAllowed; }
    public boolean isRestoreAllowed() { return restoreAllowed; }
    public boolean isExportAllowed() { return exportAllowed; }
    public boolean isDeletionAllowed() { return deletionAllowed; }
    public DeletionAction getDeletionAction() { return deletionAction; }
    public boolean isLegalHold() { return legalHold; }
    public long getRowVersion() { return rowVersion; }
}
