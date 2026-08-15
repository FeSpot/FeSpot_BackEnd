package com.api.fespot.domain.auth.repository.jpa;

import com.api.fespot.domain.auth.entity.LocalCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<LocalCredential> findByUserEmail(String email);
}
