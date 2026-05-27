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

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.samples.petclinic.model.ApiKey;

/**
 * Service interface for API key management operations.
 *
 * @author Spring PetClinic Team
 */
public interface ApiKeyService {

    /**
     * Generate a new API key (64-char hex string) and extract the prefix (first 8 chars).
     *
     * @return the generated API key string
     */
    String generateApiKey();

    /**
     * Hash an API key using BCrypt.
     *
     * @param apiKey the API key to hash
     * @return the BCrypt hash of the API key
     */
    String hashApiKey(String apiKey);

    /**
     * Validate an API key and return the ApiKey entity if valid.
     *
     * @param apiKey the API key to validate
     * @return Optional containing the ApiKey if valid, empty if invalid
     * @throws DataAccessException if data access fails
     */
    Optional<ApiKey> validateApiKey(String apiKey) throws DataAccessException;

    /**
     * Create a new API key.
     *
     * @param name the name for the API key
     * @param createdBy the username of the user creating the key
     * @param expiresAt optional expiration timestamp
     * @return ApiKeyCreationResult containing the full API key string (only time it's returned) and the ApiKey entity
     * @throws DataAccessException if data access fails
     */
    ApiKeyCreationResult createApiKey(String name, String createdBy, LocalDateTime expiresAt) throws DataAccessException;

    /**
     * Rotate an API key (generate a new key, optionally revoke the old one).
     *
     * @param id the ID of the key to rotate
     * @param createdBy the username of the user rotating the key
     * @param revokeOldKey whether to revoke the old key
     * @return ApiKeyRotationResult containing the new full API key string (only time it's returned) and the new ApiKey entity
     * @throws DataAccessException if data access fails
     */
    ApiKeyRotationResult rotateApiKey(Integer id, String createdBy, boolean revokeOldKey) throws DataAccessException;

    /**
     * Revoke an API key.
     *
     * @param id the ID of the key to revoke
     * @throws DataAccessException if data access fails
     */
    void revokeApiKey(Integer id) throws DataAccessException;

    /**
     * Update the last used timestamp for an API key.
     *
     * @param id the ID of the key
     * @throws DataAccessException if data access fails
     */
    void updateLastUsedAt(Integer id) throws DataAccessException;

    /**
     * Find an API key by ID.
     *
     * @param id the ID of the key
     * @return Optional containing the ApiKey if found
     * @throws DataAccessException if data access fails
     */
    Optional<ApiKey> findById(Integer id) throws DataAccessException;
}

