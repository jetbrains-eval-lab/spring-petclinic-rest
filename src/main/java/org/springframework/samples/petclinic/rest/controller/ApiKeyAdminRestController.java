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
package org.springframework.samples.petclinic.rest.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.samples.petclinic.model.ApiKey;
import org.springframework.samples.petclinic.rest.dto.ApiKeyResponseDto;
import org.springframework.samples.petclinic.rest.dto.CreateApiKeyRequestDto;
import org.springframework.samples.petclinic.rest.dto.RotateApiKeyRequestDto;
import org.springframework.samples.petclinic.service.ApiKeyService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
/**
 * REST controller for API key management (admin endpoints).
 * All endpoints require ROLE_ADMIN.
 *
 * @author Spring PetClinic Team
 */
@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("/api/admin/apikeys")
@Profile("spring-data-jpa")
public class ApiKeyAdminRestController {

    private final ApiKeyService apiKeyService;

    public ApiKeyAdminRestController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * Create a new API key.
     *
     * @param request the create request containing name and optional expiresAt
     * @return the created API key with full key (only time it's returned)
     */
    @PreAuthorize("hasRole(@roles.ADMIN)")
    @PostMapping
    public ResponseEntity<ApiKeyResponseDto> createApiKey(@Valid @RequestBody CreateApiKeyRequestDto request) {
        String createdBy = getCurrentUsername();
        LocalDateTime expiresAt = parseExpiresAt(request.getExpiresAt());
        var result = apiKeyService.createApiKey(request.getName(), createdBy, expiresAt);

        ApiKeyResponseDto response = toDto(result.getApiKey());
        response.setKey(result.getFullKey()); // Only time full key is returned

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(UriComponentsBuilder.newInstance()
            .path("/api/admin/apikeys/{id}").buildAndExpand(result.getApiKey().getId()).toUri());

        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }

    /**
     * Rotate an API key (generate a new key, optionally revoke the old one).
     *
     * @param id the ID of the key to rotate
     * @param request the rotate request containing optional revokeOldKey flag
     * @return the new API key with full key (only time it's returned)
     */
    @PreAuthorize("hasRole(@roles.ADMIN)")
    @PostMapping("/{id}/rotate")
    public ResponseEntity<ApiKeyResponseDto> rotateApiKey(
            @PathVariable Integer id,
            @RequestBody(required = false) RotateApiKeyRequestDto request) {

        boolean revokeOldKey = request != null && request.getRevokeOldKey() != null 
            ? request.getRevokeOldKey() : true;

        String createdBy = getCurrentUsername();

        try {
            var result = apiKeyService.rotateApiKey(id, createdBy, revokeOldKey);

            ApiKeyResponseDto response = toDto(result.getNewApiKey());
            response.setKey(result.getNewFullKey()); // Only time full key is returned

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (ObjectRetrievalFailureException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Revoke an API key.
     *
     * @param id the ID of the key to revoke
     * @return the updated API key with revokedAt set
     */
    @PreAuthorize("hasRole(@roles.ADMIN)")
    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiKeyResponseDto> revokeApiKey(@PathVariable Integer id) {
        try {
            apiKeyService.revokeApiKey(id);

            var keyOpt = apiKeyService.findById(id);
            if (keyOpt.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            ApiKey key = keyOpt.get();
            ApiKeyResponseDto response = toDto(key);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (ObjectRetrievalFailureException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Convert ApiKey entity to DTO (without full key).
     *
     * @param apiKey the API key entity
     * @return the DTO
     */
    private ApiKeyResponseDto toDto(ApiKey apiKey) {
        ApiKeyResponseDto dto = new ApiKeyResponseDto();
        dto.setId(apiKey.getId());
        dto.setKeyPrefix(apiKey.getKeyPrefix());
        dto.setName(apiKey.getName());
        dto.setCreatedAt(apiKey.getCreatedAt());
        dto.setExpiresAt(apiKey.getExpiresAt());
        dto.setCreatedBy(apiKey.getCreatedBy());
        dto.setRevokedAt(apiKey.getRevokedAt());
        // Note: key field is NOT set (only set on creation/rotation)
        return dto;
    }

    /**
     * Get the current authenticated username.
     *
     * @return the username
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            return authentication.getName();
        }
        return "system"; // Fallback
    }

    private LocalDateTime parseExpiresAt(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(expiresAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "expiresAt must be ISO-8601 local date-time without timezone",
                e
            );
        }
    }
}
