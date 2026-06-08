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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Integration tests for API key authentication (enabled).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"spring-data-jpa", "h2"})
@TestPropertySource(properties = {
    "petclinic.apikey.enabled=true"
})
@ExtendWith(OutputCaptureExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiKeyAuthEnabledTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;
    private ObjectMapper objectMapper;
    private HttpHeaders adminHeaders;
    private int ownerId;
    private int petTypeId;
    private int specialtyId;
    private int vetId;
    private int petId;
    private int visitId;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + port + "/petclinic";
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        adminHeaders = new HttpHeaders();
        String credentials = Base64.getEncoder().encodeToString("admin:admin".getBytes());
        adminHeaders.set("Authorization", "Basic " + credentials);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));

        ownerId = createOwner("Api", "KeyOwner");
        petTypeId = createPetType("ApiKeyType");
        specialtyId = createSpecialty("ApiKeySpecialty");
        vetId = createVet("Api", "Vet", specialtyId);
        petId = createPet(ownerId, petTypeId, "ApiPet");
        visitId = createVisit(ownerId, petId, "Api Visit");
    }

    @Test
    void testValidApiKeyAccess() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Valid Key", null);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testApiKeyAccessNonAdminEndpoints() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Non Admin Access", null);
        String[] endpoints = {
            "/api/owners/" + ownerId,
            "/api/pets/" + petId,
            "/api/visits/" + visitId,
            "/api/pettypes/" + petTypeId,
            "/api/specialties/" + specialtyId,
            "/api/vets/" + vetId
        };

        for (String endpoint : endpoints) {
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + endpoint,
                HttpMethod.GET,
                new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
                String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @ParameterizedTest
    @MethodSource("mutatingNonAdminRequests")
    void testApiKeyCanMutateNonAdminEndpoints(NonAdminMutation mutation) throws Exception {
        ApiKeyInfo keyInfo = createApiKey(mutation.keyName(), null);
        Map<String, Object> request = mutation.requestSupplier().get();
        String requestJson = request == null ? "" : objectMapper.writeValueAsString(request);

        HttpHeaders headers = mutation.requiresJson()
            ? apiKeyJsonHeaders(keyInfo.key())
            : apiKeyHeaders(keyInfo.key());

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + mutation.endpointSupplier().get(),
            mutation.method(),
            new HttpEntity<>(requestJson, headers),
            String.class
        );

        var status = response.getStatusCode();
        assertThat(status.value())
            .as("Auth failed for mutation '%s': %s", mutation.keyName(), status)
            .isNotIn(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.TOO_MANY_REQUESTS.value()
            );
        assertThat(status.is5xxServerError())
            .as("Server error for mutation '%s': %s", mutation.keyName(), status)
            .isFalse();
    }

    @Test
    void testApiKeyCannotAccessUsersEndpoint() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Users Forbidden", null);

        Map<String, Object> request = new HashMap<>();
        request.put("username", "api-key-user");
        request.put("password", "secret");
        request.put("enabled", true);

        String requestJson = objectMapper.writeValueAsString(request);
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/users",
            HttpMethod.POST,
            new HttpEntity<>(requestJson, apiKeyJsonHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testInvalidApiKeyUnauthorized() {
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders("invalid-key")),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testNoAuthenticationUnauthorized() {
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testMissingApiKeyBasicAuthWorks() {
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testRevokedKeyUnauthorized() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Key To Revoke", null);

        ResponseEntity<String> revokeResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + keyInfo.id() + "/revoke",
            HttpMethod.POST,
            new HttpEntity<>(adminHeaders),
            String.class
        );
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode revokeBody = objectMapper.readTree(revokeResponse.getBody());
        assertThat(revokeBody.hasNonNull("id")).isTrue();
        assertThat(revokeBody.hasNonNull("name")).isTrue();
        assertThat(revokeBody.hasNonNull("createdAt")).isTrue();
        assertThat(revokeBody.hasNonNull("createdBy")).isTrue();
        assertThat(revokeBody.hasNonNull("keyPrefix")).isTrue();
        assertThat(revokeBody.hasNonNull("revokedAt")).isTrue();
        boolean keyAbsent = !revokeBody.has("key");
        boolean keyNull = revokeBody.has("key") && revokeBody.get("key").isNull();
        assertThat(keyAbsent || keyNull).isTrue();

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testRotateRevokesOldKey() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Key To Rotate", null);

        Map<String, Boolean> rotateRequest = new HashMap<>();
        rotateRequest.put("revokeOldKey", true);
        String rotateJson = objectMapper.writeValueAsString(rotateRequest);

        ResponseEntity<String> rotateResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + keyInfo.id() + "/rotate",
            HttpMethod.POST,
            new HttpEntity<>(rotateJson, adminHeaders),
            String.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode rotateBody = objectMapper.readTree(rotateResponse.getBody());
        assertThat(rotateBody.hasNonNull("id")).isTrue();
        assertThat(rotateBody.hasNonNull("name")).isTrue();
        assertThat(rotateBody.hasNonNull("createdAt")).isTrue();
        assertThat(rotateBody.hasNonNull("createdBy")).isTrue();
        assertThat(rotateBody.hasNonNull("key")).isTrue();
        assertThat(rotateBody.hasNonNull("keyPrefix")).isTrue();

        String newKey = rotateBody.get("key").asText();
        ResponseEntity<String> oldKeyResponse = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );
        assertThat(oldKeyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> newKeyResponse = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(newKey)),
            String.class
        );
        assertThat(newKeyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testRotateKeepsOldKeyWhenRevokeFalse() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Key To Rotate Keep Old", null);

        Map<String, Boolean> rotateRequest = new HashMap<>();
        rotateRequest.put("revokeOldKey", false);
        String rotateJson = objectMapper.writeValueAsString(rotateRequest);

        ResponseEntity<String> rotateResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + keyInfo.id() + "/rotate",
            HttpMethod.POST,
            new HttpEntity<>(rotateJson, adminHeaders),
            String.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode rotateBody = objectMapper.readTree(rotateResponse.getBody());
        String newKey = rotateBody.get("key").asText();

        ResponseEntity<String> oldKeyResponse = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );
        assertThat(oldKeyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> newKeyResponse = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(newKey)),
            String.class
        );
        assertThat(newKeyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testRotateDefaultsToRevokeOldKey() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Key To Rotate Default", null);

        ResponseEntity<String> rotateResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + keyInfo.id() + "/rotate",
            HttpMethod.POST,
            new HttpEntity<>("{}", adminHeaders),
            String.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode rotateBody = objectMapper.readTree(rotateResponse.getBody());
        String newKey = rotateBody.get("key").asText();

        ResponseEntity<String> oldKeyResponse = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );
        assertThat(oldKeyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> newKeyResponse = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(newKey)),
            String.class
        );
        assertThat(newKeyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testExpiredKeyUnauthorized() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.now().minusDays(1).withNano(0);
        ApiKeyInfo keyInfo = createApiKey("Expired Key", expiresAt);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }


    @Test
    void testApiKeyForbiddenOnAdminEndpoints() throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Api Key Non Admin", null);
        ApiKeyInfo targetKey = createApiKey("Target For Admin Ops", null);

        Map<String, String> createRequest = new HashMap<>();
        createRequest.put("name", "Should Be Forbidden");
        String createJson = objectMapper.writeValueAsString(createRequest);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            new HttpEntity<>(createJson, apiKeyJsonHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Boolean> rotateRequest = new HashMap<>();
        rotateRequest.put("revokeOldKey", true);
        String rotateJson = objectMapper.writeValueAsString(rotateRequest);

        ResponseEntity<String> rotateResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + targetKey.id() + "/rotate",
            HttpMethod.POST,
            new HttpEntity<>(rotateJson, apiKeyJsonHeaders(keyInfo.key())),
            String.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> revokeResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + targetKey.id() + "/revoke",
            HttpMethod.POST,
            new HttpEntity<>(apiKeyJsonHeaders(keyInfo.key())),
            String.class
        );
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testCreateApiKeyResponseFormat() throws Exception {
        JsonNode responseBody = createApiKeyResponse("Format Check", null);

        assertThat(responseBody.hasNonNull("id")).isTrue();
        assertThat(responseBody.hasNonNull("name")).isTrue();
        assertThat(responseBody.hasNonNull("createdAt")).isTrue();
        assertThat(responseBody.hasNonNull("createdBy")).isTrue();
        assertThat(responseBody.hasNonNull("key")).isTrue();
        assertThat(responseBody.hasNonNull("keyPrefix")).isTrue();

        String key = responseBody.get("key").asText();
        String keyPrefix = responseBody.get("keyPrefix").asText();
        assertThat(key.length()).isGreaterThanOrEqualTo(32);
        assertThat(key.startsWith(keyPrefix)).isTrue();
        assertThat(responseBody.get("name").asText()).isEqualTo("Format Check");
    }

    @Test
    void testRotateRevokeNotFound() throws Exception {
        Map<String, Boolean> rotateRequest = new HashMap<>();
        rotateRequest.put("revokeOldKey", true);
        String rotateJson = objectMapper.writeValueAsString(rotateRequest);

        ResponseEntity<String> rotateResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/99999/rotate",
            HttpMethod.POST,
            new HttpEntity<>(rotateJson, adminHeaders),
            String.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> revokeResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/99999/revoke",
            HttpMethod.POST,
            new HttpEntity<>(adminHeaders),
            String.class
        );
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testCreateApiKeyWithoutNameBadRequest() throws Exception {
        String requestJson = objectMapper.writeValueAsString(new HashMap<>());
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            new HttpEntity<>(requestJson, adminHeaders),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testFutureExpiresAtAllowsAccess() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(10).withNano(0);
        ApiKeyInfo keyInfo = createApiKey("Future Expiry", expiresAt);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testExpiresAtValidFormatAccepted() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Valid ExpiresAt");
        request.put("expiresAt", "2026-12-31T23:59:59");

        String requestJson = objectMapper.writeValueAsString(request);
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            new HttpEntity<>(requestJson, adminHeaders),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.hasNonNull("expiresAt")).isTrue();
    }

    @Test
    void testExpiresAtInvalidFormatRejected() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Invalid ExpiresAt");
        request.put("expiresAt", "2026-12-31T23:59:59Z");

        String requestJson = objectMapper.writeValueAsString(request);
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            new HttpEntity<>(requestJson, adminHeaders),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testSuspiciousActivitySignal() {
        String prefix = "suspici0"; // 8 chars

        for (int i = 0; i < 4; i++) {
            String invalidKey = prefix + String.format("%056d", i);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/owners/" + ownerId,
                HttpMethod.GET,
                new HttpEntity<>(apiKeyHeaders(invalidKey)),
                String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        String invalidKey = prefix + String.format("%056d", 99);
        ResponseEntity<String> suspiciousResponse = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(invalidKey)),
            String.class
        );

        assertThat(suspiciousResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(suspiciousResponse.getHeaders().getFirst("X-Suspicious-Activity")).isEqualTo("true");
    }

    @Test
    void testAuditLogFormatAndNoPlaintextKey(CapturedOutput output) throws Exception {
        ApiKeyInfo keyInfo = createApiKey("Audit Log", null);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(keyInfo.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String logs = output.getOut() + output.getErr();
        String prefix = keyInfo.key().substring(0, 8);

        assertThat(logs).contains("key_prefix=" + prefix)
            .contains("success=true")
            .contains("method=GET")
            .contains("path=/petclinic/api/owners/" + ownerId)
            .contains("client_ip=")
            .contains("user_agent=")
            .contains("failure_reason=")
            .contains("timestamp=")
            .doesNotContain(keyInfo.key());
    }

    @Test
    void testAuditLogForFailedAuthentication(CapturedOutput output) {
        String prefix = "badkey00";
        String invalidKey = prefix + String.format("%056d", 1);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(invalidKey)),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String logs = output.getOut() + output.getErr();
        assertThat(logs).contains("key_prefix=" + prefix)
            .contains("success=false")
            .contains("failure_reason=")
            .doesNotContain(invalidKey);
    }

    private ApiKeyInfo createApiKey(String name, LocalDateTime expiresAt) throws Exception {
        JsonNode responseBody = createApiKeyResponse(name, expiresAt);
        return new ApiKeyInfo(responseBody.get("id").asInt(), responseBody.get("key").asText());
    }

    private JsonNode createApiKeyResponse(String name, LocalDateTime expiresAt) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        if (expiresAt != null) {
            request.put("expiresAt", expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody());
    }
    private int createOwner(String firstName, String lastName) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("firstName", firstName);
        request.put("lastName", lastName);
        request.put("address", "123 Main St");
        request.put("city", "Madison");
        request.put("telephone", "1234567890");

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private int createPetType(String name) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/pettypes",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private int createSpecialty(String name) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/specialties",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private int createVet(String firstName, String lastName, int specialtyId) throws Exception {
        Map<String, Object> specialty = new HashMap<>();
        specialty.put("id", specialtyId);
        specialty.put("name", "ApiKeySpecialty");

        Map<String, Object> request = new HashMap<>();
        request.put("firstName", firstName);
        request.put("lastName", lastName);
        request.put("specialties", java.util.Collections.singletonList(specialty));

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/vets",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private int createPet(int ownerId, int petTypeId, String name) throws Exception {
        Map<String, Object> petType = new HashMap<>();
        petType.put("id", petTypeId);
        petType.put("name", "ApiKeyType");

        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("birthDate", "2015-01-01");
        request.put("type", petType);

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId + "/pets",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private int createVisit(int ownerId, int petId, String description) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("date", "2015-02-02");
        request.put("description", description);

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId + "/pets/" + petId + "/visits",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return responseBody.get("id").asInt();
    }

    private HttpHeaders apiKeyHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        return headers;
    }

    private HttpHeaders apiKeyJsonHeaders(String apiKey) {
        HttpHeaders headers = apiKeyHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private List<NonAdminMutation> mutatingNonAdminRequests() {
        return List.of(
            new NonAdminMutation(
                "POST /api/owners",
                HttpMethod.POST,
                true,
                () -> "/api/owners",
                () -> {
                    Map<String, Object> request = new HashMap<>();
                    request.put("firstName", "Api");
                    request.put("lastName", "KeyOwner2");
                    request.put("address", "789 Create St");
                    request.put("city", "Madison");
                    request.put("telephone", "1234567890");
                    return request;
                }
            ),
            new NonAdminMutation(
                "POST /api/pettypes",
                HttpMethod.POST,
                true,
                () -> "/api/pettypes",
                () -> {
                    Map<String, Object> request = new HashMap<>();
                    request.put("name", "ApiKeyType-" + System.currentTimeMillis());
                    return request;
                }
            ),
            new NonAdminMutation(
                "POST /api/specialties",
                HttpMethod.POST,
                true,
                () -> "/api/specialties",
                () -> {
                    Map<String, Object> request = new HashMap<>();
                    request.put("name", "ApiKeySpecialty-" + System.currentTimeMillis());
                    return request;
                }
            ),
            new NonAdminMutation(
                "POST /api/vets",
                HttpMethod.POST,
                true,
                () -> "/api/vets",
                () -> {
                    Map<String, Object> specialty = new HashMap<>();
                    specialty.put("name", "ApiKeySpecialty");

                    Map<String, Object> request = new HashMap<>();
                    request.put("firstName", "Api");
                    request.put("lastName", "Vet2");
                    request.put("specialties", Collections.singletonList(specialty));
                    return request;
                }
            ),
            new NonAdminMutation(
                "POST /api/owners/{ownerId}/pets",
                HttpMethod.POST,
                true,
                () -> "/api/owners/" + ownerId + "/pets",
                () -> {
                    Map<String, Object> petType = new HashMap<>();
                    petType.put("id", petTypeId);
                    petType.put("name", "ApiKeyType");

                    Map<String, Object> request = new HashMap<>();
                    request.put("name", "ApiPet-" + System.currentTimeMillis());
                    request.put("birthDate", "2015-01-01");
                    request.put("type", petType);
                    return request;
                }
            ),
            new NonAdminMutation(
                "POST /api/owners/{ownerId}/pets/{petId}/visits",
                HttpMethod.POST,
                true,
                () -> "/api/owners/" + ownerId + "/pets/" + petId + "/visits",
                () -> {
                    Map<String, Object> request = new HashMap<>();
                    request.put("date", "2015-02-02");
                    request.put("description", "Api Visit " + System.currentTimeMillis());
                    return request;
                }
            ),
            new NonAdminMutation(
                "POST /api/visits",
                HttpMethod.POST,
                true,
                () -> "/api/visits",
                () -> {
                    Map<String, Object> request = new HashMap<>();
                    request.put("date", "2015-02-02");
                    request.put("description", "Api Visit " + System.currentTimeMillis());
                    request.put("petId", petId);
                    return request;
                }
            ),
            new NonAdminMutation(
                "PUT /api/owners/{ownerId}",
                HttpMethod.PUT,
                true,
                () -> "/api/owners/" + ownerId,
                () -> {
                    Map<String, Object> request = new HashMap<>();
                    request.put("id", ownerId);
                    request.put("firstName", "Api");
                    request.put("lastName", "KeyOwner");
                    request.put("address", "456 Updated St");
                    request.put("city", "Madison");
                    request.put("telephone", "1234567890");
                    return request;
                }
            ),
            new NonAdminMutation(
                "PUT /api/owners/{ownerId}/pets/{petId}",
                HttpMethod.PUT,
                true,
                () -> "/api/owners/" + ownerId + "/pets/" + petId,
                () -> {
                    Map<String, Object> petType = new HashMap<>();
                    petType.put("id", petTypeId);
                    petType.put("name", "ApiKeyType");

                    Map<String, Object> request = new HashMap<>();
                    request.put("name", "ApiPetUpdated");
                    request.put("birthDate", "2016-01-01");
                    request.put("type", petType);
                    return request;
                }
            ),
            new NonAdminMutation(
                "PUT /api/pets/{petId}",
                HttpMethod.PUT,
                true,
                () -> "/api/pets/" + petId,
                () -> {
                    Map<String, Object> petType = new HashMap<>();
                    petType.put("id", petTypeId);
                    petType.put("name", "ApiKeyType");

                    Map<String, Object> request = new HashMap<>();
                    request.put("name", "ApiPetUpdated2");
                    request.put("birthDate", "2017-01-01");
                    request.put("type", petType);
                    return request;
                }
            ),
            new NonAdminMutation(
                "PUT /api/visits/{visitId}",
                HttpMethod.PUT,
                true,
                () -> "/api/visits/" + visitId,
                () -> {
                    Map<String, Object> request = new HashMap<>();
                    request.put("date", "2016-02-02");
                    request.put("description", "Updated visit");
                    return request;
                }
            ),
            new NonAdminMutation(
                "PUT /api/vets/{vetId}",
                HttpMethod.PUT,
                true,
                () -> "/api/vets/" + vetId,
                () -> {
                    Map<String, Object> specialty = new HashMap<>();
                    specialty.put("name", "ApiKeySpecialty");

                    Map<String, Object> request = new HashMap<>();
                    request.put("firstName", "Api");
                    request.put("lastName", "VetUpdated");
                    request.put("specialties", Collections.singletonList(specialty));
                    return request;
                }
            ),
            new NonAdminMutation(
                "PUT /api/pettypes/{petTypeId}",
                HttpMethod.PUT,
                true,
                () -> "/api/pettypes/" + petTypeId,
                () -> {
                    Map<String, Object> request = new HashMap<>();
                    request.put("name", "ApiKeyTypeUpdated");
                    return request;
                }
            ),
            new NonAdminMutation(
                "PUT /api/specialties/{specialtyId}",
                HttpMethod.PUT,
                true,
                () -> "/api/specialties/" + specialtyId,
                () -> {
                    Map<String, Object> request = new HashMap<>();
                    request.put("name", "ApiKeySpecialtyUpdated");
                    return request;
                }
            ),
            new NonAdminMutation(
                "DELETE /api/owners/{ownerId}",
                HttpMethod.DELETE,
                false,
                () -> {
                    try {
                        int deleteOwnerId = createOwner("To", "Delete");
                        return "/api/owners/" + deleteOwnerId;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                },
                () -> null
            ),
            new NonAdminMutation(
                "DELETE /api/pets/{petId}",
                HttpMethod.DELETE,
                false,
                () -> {
                    try {
                        int owner = createOwner("Pet", "Delete");
                        int petType = createPetType("DeleteType-" + System.currentTimeMillis());
                        int pet = createPet(owner, petType, "DeletePet");
                        return "/api/pets/" + pet;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                },
                () -> null
            ),
            new NonAdminMutation(
                "DELETE /api/visits/{visitId}",
                HttpMethod.DELETE,
                false,
                () -> {
                    try {
                        int owner = createOwner("Visit", "Delete");
                        int petType = createPetType("DeleteType-" + System.currentTimeMillis());
                        int pet = createPet(owner, petType, "DeletePet");
                        int visit = createVisit(owner, pet, "Delete Visit");
                        return "/api/visits/" + visit;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                },
                () -> null
            ),
            new NonAdminMutation(
                "DELETE /api/vets/{vetId}",
                HttpMethod.DELETE,
                false,
                () -> {
                    try {
                        int spec = createSpecialty("DeleteSpecialty-" + System.currentTimeMillis());
                        int vet = createVet("To", "Delete", spec);
                        return "/api/vets/" + vet;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                },
                () -> null
            ),
            new NonAdminMutation(
                "DELETE /api/pettypes/{petTypeId}",
                HttpMethod.DELETE,
                false,
                () -> {
                    try {
                        int petType = createPetType("DeleteType-" + System.currentTimeMillis());
                        return "/api/pettypes/" + petType;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                },
                () -> null
            ),
            new NonAdminMutation(
                "DELETE /api/specialties/{specialtyId}",
                HttpMethod.DELETE,
                false,
                () -> {
                    try {
                        int spec = createSpecialty("DeleteSpecialty-" + System.currentTimeMillis());
                        return "/api/specialties/" + spec;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                },
                () -> null
            )
        );
    }

    private record NonAdminMutation(
        String keyName,
        HttpMethod method,
        boolean requiresJson,
        java.util.function.Supplier<String> endpointSupplier,
        java.util.function.Supplier<Map<String, Object>> requestSupplier
    ) {}

    private record ApiKeyInfo(int id, String key) {}
}
