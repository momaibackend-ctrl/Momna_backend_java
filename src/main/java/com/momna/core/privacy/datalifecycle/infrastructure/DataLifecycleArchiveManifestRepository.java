package com.momna.core.privacy.datalifecycle.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataLifecycleArchiveManifestRepository
    extends JpaRepository<DataLifecycleArchiveManifestEntity, UUID> {}
