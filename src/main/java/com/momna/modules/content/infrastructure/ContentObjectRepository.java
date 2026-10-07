package com.momna.modules.content.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentObjectRepository extends JpaRepository<ContentObjectEntity, String> {
    Optional<ContentObjectEntity> findByContentKey(String contentKey);
}
