package com.spendwise.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceApiKeyServiceImpl implements DeviceApiKeyService {

    private final DeviceApiKeyRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceApiKeyServiceImpl(DeviceApiKeyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public String registerNewKey(UUID userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawKey = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.insert(userId, hash(rawKey));
        return rawKey;
    }

    @Override
    @Transactional
    public boolean validate(String rawKey, UUID userId) {
        if (rawKey == null || userId == null) {
            return false;
        }
        String incomingHash = hash(rawKey);
        List<DeviceApiKey> activeKeys = repository.findActiveForUser(userId);
        return activeKeys.stream()
                .filter(key -> key.keyHash().equals(incomingHash))
                .findFirst()
                .map(key -> {
                    repository.markLastUsed(key.id(), userId);
                    return true;
                })
                .orElse(false);
    }

    static String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
