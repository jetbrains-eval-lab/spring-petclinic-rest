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

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * Integration tests for API key authentication when disabled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"spring-data-jpa", "h2"})
@TestPropertySource(properties = "petclinic.apikey.enabled=false")
class ApiKeyAuthDisabledTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;
    private ObjectMapper objectMapper;
    private HttpHeaders adminHeaders;
    private int ownerId;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + port + "/petclinic";
        objectMapper = new ObjectMapper();

        adminHeaders = new HttpHeaders();
        String credentials = Base64.getEncoder().encodeToString("admin:admin".getBytes());
        adminHeaders.set("Authorization", "Basic " + credentials);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));

        ownerId = createOwner("Disabled", "ApiKey");
    }

    @Test
    void testApiKeyDoesNotWorkWhenDisabled() throws Exception {
        ApiKeyCreation created = createApiKey("Disabled Mode Key");

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(apiKeyHeaders(created.key())),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testBasicAuthStillWorksWhenDisabled() {
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/owners/" + ownerId,
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders),
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testAdminRotateAndRevokeStillWorkWhenDisabled() throws Exception {
        ApiKeyCreation created = createApiKey("Disabled Mode Admin Ops");

        ResponseEntity<String> rotateResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + created.id() + "/rotate",
            HttpMethod.POST,
            new HttpEntity<>("{}", adminHeaders),
            String.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> revokeResponse = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys/" + created.id() + "/revoke",
            HttpMethod.POST,
            new HttpEntity<>(adminHeaders),
            String.class
        );
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private int createOwner(String firstName, String lastName) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("firstName", firstName);
        request.put("lastName", lastName);
        request.put("address", "456 State St");
        request.put("city", "Madison");
        request.put("telephone", "1234567891");

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

    private ApiKeyCreation createApiKey(String name) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, adminHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/admin/apikeys",
            HttpMethod.POST,
            entity,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        return new ApiKeyCreation(
            responseBody.get("id").asInt(),
            responseBody.get("key").asText()
        );
    }

    private record ApiKeyCreation(int id, String key) {}

    private HttpHeaders apiKeyHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        return headers;
    }
}
