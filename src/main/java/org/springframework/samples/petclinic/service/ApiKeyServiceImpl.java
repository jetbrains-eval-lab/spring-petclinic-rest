/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.samples.petclinic.model.ApiKey;
import org.springframework.samples.petclinic.repository.ApiKeyRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for API key management operations.
 *
 * @author Spring PetClinic Team
 */
@Service
@Profile("spring-data-jpa")
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;
    private final int keyLength;

    @Autowired
    public ApiKeyServiceImpl(ApiKeyRepository apiKeyRepository,
                              @Value("${petclinic.apikey.key-length:64}") int keyLength,
                              @Value("${petclinic.apikey.bcrypt-strength:12}") int bcryptStrength) {
        this.apiKeyRepository = apiKeyRepository;
        this.keyLength = keyLength;
        this.passwordEncoder = new BCryptPasswordEncoder(bcryptStrength);
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String generateApiKey() {
        byte[] randomBytes = new byte[keyLength / 2]; // Each byte produces 2 hex chars
        secureRandom.nextBytes(randomBytes);
        StringBuilder sb = new StringBuilder(keyLength);
        for (byte b : randomBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public String hashApiKey(String apiKey) {
        return passwordEncoder.encode(apiKey);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiKey> validateApiKey(String apiKey) throws DataAccessException {
        if (apiKey == null || apiKey.length() != keyLength) {
            return Optional.empty();
        }

        // Extract prefix (first 8 chars) for lookup optimization
        String keyPrefix = apiKey.substring(0, Math.min(8, apiKey.length()));

        // Find all active, non-revoked keys with matching prefix
        // Note: BCrypt produces different hashes each time (due to salt),
        // so we can't do direct hash lookup. We need to check candidates using matches().
        var candidateKeys = apiKeyRepository.findByKeyPrefixAndIsActiveTrueAndRevokedAtIsNull(keyPrefix);

        // For each candidate, verify using BCrypt matches()
        for (ApiKey candidate : candidateKeys) {
            if (passwordEncoder.matches(apiKey, candidate.getKeyHash())) {
                // Validate: is_active=true, revoked_at=NULL, expires_at=NULL OR expires_at > now()
                if (candidate.isValid()) {
                    return Optional.of(candidate);
                }
            }
        }

        return Optional.empty();
    }

    @Override
    @Transactional
    public ApiKeyCreationResult createApiKey(String name, String createdBy, LocalDateTime expiresAt) throws DataAccessException {
        // Generate random 64-char hex string
        String apiKey = generateApiKey();

        // Extract first 8 chars as key_prefix
        String keyPrefix = apiKey.substring(0, 8);

        // Hash full key with BCrypt (strength 12)
        String keyHash = hashApiKey(apiKey);

        // Create entity with metadata
        ApiKey apiKeyEntity = new ApiKey();
        apiKeyEntity.setKeyHash(keyHash);
        apiKeyEntity.setKeyPrefix(keyPrefix);
        apiKeyEntity.setName(name);
        apiKeyEntity.setCreatedBy(createdBy);
        apiKeyEntity.setCreatedAt(LocalDateTime.now());
        apiKeyEntity.setExpiresAt(expiresAt);
        apiKeyEntity.setIsActive(true);

        // Save to database
        apiKeyRepository.save(apiKeyEntity);

        // Return result with full key (only time visible) and entity
        return new ApiKeyCreationResult(apiKey, apiKeyEntity);
    }

    @Override
    @Transactional
    public ApiKeyRotationResult rotateApiKey(Integer id, String createdBy, boolean revokeOldKey) throws DataAccessException {
        // Find existing key by ID
        Optional<ApiKey> existingKeyOpt = apiKeyRepository.findById(id);
        if (existingKeyOpt.isEmpty()) {
            throw new ObjectRetrievalFailureException(ApiKey.class, id);
        }

        ApiKey existingKey = existingKeyOpt.get();

        // If revokeOldKey=true: set old key's revoked_at and is_active=false
        if (revokeOldKey) {
            existingKey.setRevokedAt(LocalDateTime.now());
            existingKey.setIsActive(false);
            apiKeyRepository.save(existingKey);
        }

        // Generate new key (same as creation)
        String newApiKey = generateApiKey();
        String keyPrefix = newApiKey.substring(0, 8);
        String keyHash = hashApiKey(newApiKey);

        // Create new key entity
        ApiKey newApiKeyEntity = new ApiKey();
        newApiKeyEntity.setKeyHash(keyHash);
        newApiKeyEntity.setKeyPrefix(keyPrefix);
        newApiKeyEntity.setName(existingKey.getName()); // Keep original name; clients distinguish by id/keyPrefix
        newApiKeyEntity.setCreatedBy(createdBy);
        newApiKeyEntity.setCreatedAt(LocalDateTime.now());
        newApiKeyEntity.setExpiresAt(existingKey.getExpiresAt());
        newApiKeyEntity.setIsActive(true);

        // Save new key
        apiKeyRepository.save(newApiKeyEntity);

        // Return result with new full key (only time visible) and entity
        return new ApiKeyRotationResult(newApiKey, newApiKeyEntity);
    }

    @Override
    @Transactional
    public void revokeApiKey(Integer id) throws DataAccessException {
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(id);
        if (keyOpt.isEmpty()) {
            throw new ObjectRetrievalFailureException(ApiKey.class, id);
        }

        ApiKey apiKey = keyOpt.get();
        apiKey.setRevokedAt(LocalDateTime.now());
        apiKey.setIsActive(false);
        apiKeyRepository.save(apiKey);
    }

    @Override
    @Transactional
    public void updateLastUsedAt(Integer id) throws DataAccessException {
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(id);
        if (keyOpt.isPresent()) {
            ApiKey apiKey = keyOpt.get();
            apiKey.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(apiKey);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiKey> findById(Integer id) throws DataAccessException {
        return apiKeyRepository.findById(id);
    }
}
