package com.momna.platform.storage.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ObjectMetadataRepository
    extends JpaRepository<ObjectMetadataEntity, ObjectMetadataId> {}
