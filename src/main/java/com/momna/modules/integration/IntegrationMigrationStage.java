package com.momna.modules.integration;

public enum IntegrationMigrationStage {
    INVENTORIED,
    CONTRACT_MAPPED,
    DUAL_READ_IF_DATA_EXISTS,
    BACKFILL_IF_NEEDED,
    PARITY_VERIFIED,
    CUTOVER,
    LEGACY_REMOVED_AFTER_PARITY,
    CORE_NATIVE
}
