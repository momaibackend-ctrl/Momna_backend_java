package com.momna.modules.auth.infrastructure;

import com.momna.modules.auth.domain.AuthProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginIdentityRepository extends JpaRepository<LoginIdentityEntity, String> {
    Optional<LoginIdentityEntity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
    List<LoginIdentityEntity> findByUserIdOrderByCreatedAtAscIdentityIdAsc(String userId);
}
