package com.example.passkeydemo.repo;

import com.example.passkeydemo.model.PasskeyCredential;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, Long> {

    List<PasskeyCredential> findByUserIdOrderByCreatedAtAsc(Long userId);

    long countByUserId(Long userId);

    Optional<PasskeyCredential> findByUserIdAndCredentialId(Long userId, String credentialId);

    Optional<PasskeyCredential> findByCredentialId(String credentialId);
}
