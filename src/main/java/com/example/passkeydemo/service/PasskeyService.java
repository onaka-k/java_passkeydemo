package com.example.passkeydemo.service;

import com.example.passkeydemo.model.AppUser;
import com.example.passkeydemo.model.PasskeyCredential;
import com.example.passkeydemo.repo.PasskeyCredentialRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasskeyService {

    public static final int MAX_PASSKEYS_PER_USER = 5;

    private final PasskeyCredentialRepository credentialRepository;

    public List<PasskeyCredential> listByUserId(Long userId) {
        return credentialRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public long countByUserId(Long userId) {
        return credentialRepository.countByUserId(userId);
    }

    @Transactional
    public PasskeyCredential register(AppUser user, String credentialId, String label, String transports, String userHandle, String payloadJson) {
        long count = countByUserId(user.getId());
        if (count >= MAX_PASSKEYS_PER_USER) {
            throw new PasskeyLimitExceededException(MAX_PASSKEYS_PER_USER);
        }

        PasskeyCredential credential = new PasskeyCredential();
        credential.setUser(user);
        credential.setCredentialId(credentialId);
        credential.setLabel(label == null || label.isBlank() ? "Passkey" : label);
        credential.setSignCount(0L);
        credential.setTransports(transports);
        credential.setUserHandle(userHandle);
        credential.setRegistrationPayload(payloadJson);
        credential.setCreatedAt(Instant.now());
        credential.setUpdatedAt(Instant.now());

        return credentialRepository.save(credential);
    }

    @Transactional
    public void deleteById(Long userId, Long credentialDbId) {
        PasskeyCredential credential = credentialRepository.findById(credentialDbId)
                .orElseThrow(() -> new NoSuchElementException("Credential not found"));
        if (!credential.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Credential does not belong to user");
        }
        credentialRepository.delete(credential);
    }

    public PasskeyCredential findByCredentialId(String credentialId) {
        return credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new NoSuchElementException("Credential not found"));
    }

    @Transactional
    public void touchSignIn(String credentialId) {
        credentialRepository.findByCredentialId(credentialId).ifPresent(c -> {
            c.setSignCount(c.getSignCount() + 1);
            c.setUpdatedAt(Instant.now());
            credentialRepository.save(c);
        });
    }

    public static class PasskeyLimitExceededException extends RuntimeException {
        public PasskeyLimitExceededException(int max) {
            super("Passkey registration limit exceeded: " + max);
        }
    }
}
