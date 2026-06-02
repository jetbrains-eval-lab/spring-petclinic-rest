/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.samples.petclinic.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.rest.dto.OwnerDto;
import org.springframework.samples.petclinic.rest.dto.PetDto;
import org.springframework.samples.petclinic.rest.dto.VetDto;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration test for N+1 problem analysis and optimization.
 * This test verifies N+1 problems across multiple entity relationships:
 * - Pet->Visit->PetType (optimized)
 * - Owner → Pet (optimized)
 * - Vet → Specialty (optimized)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"petclinic.security.enable=false"})
@ActiveProfiles({"hsqldb", "spring-data-jpa"})
@Import(N1TestConfiguration.class)
public class N1OptimizedRestControllerTest {

    private static final Logger logger = LoggerFactory.getLogger(N1OptimizedRestControllerTest.class);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SqlQueryInterceptor sqlQueryInterceptor;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/petclinic/api";
        sqlQueryInterceptor.reset();
    }

    @Test
    void testPetListEndpoint_analyzePetVisitPetTypeN1Problem() {
        // Start capturing SQL queries
        sqlQueryInterceptor.startCapturing();

        // Call REST endpoint that loads all pets (should now be optimized)
        ResponseEntity<PetDto[]> response = restTemplate.getForEntity(
            baseUrl + "/pets", PetDto[].class);

        // Stop capturing
        sqlQueryInterceptor.stopCapturing();

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        PetDto[] pets = response.getBody();
        assertThat(pets.length).isGreaterThan(0);

        // Get query metrics
        SqlQueryInterceptor.QueryMetrics metrics = sqlQueryInterceptor.getMetrics();

        // Log results for verification
        logger.info("=".repeat(80));
        logger.info("OPTIMIZED REST ENDPOINT: GET /api/pets");
        logger.info("=".repeat(80));
        logger.info("Number of pets loaded: {}", pets.length);
        logger.info("Total SQL queries executed: {}", metrics.getQueryCount());
        logger.info("SELECT queries: {}", metrics.getQueryTypeAnalysis().getSelectCount());
        logger.info("JOIN queries: {}", metrics.getQueryTypeAnalysis().getJoinCount());
        logger.info("Execution time: {}ms", metrics.getExecutionTimeMs());
        logger.info("Likely N+1 problem: {}", metrics.getQueryTypeAnalysis().isLikelyN1Problem());
        logger.info("-".repeat(80));
        logger.info("SQL Queries executed:");
        for (int i = 0; i < metrics.getQueries().size(); i++) {
            logger.info("{}. {}", (i + 1), metrics.getQueries().get(i));
        }
        logger.info("=".repeat(80));
        logger.info("OPTIMIZATION RESULT:");
        logger.info("Expected: 1 query with all JOINs (pets, visits, types, owners)");
        logger.info("Previous unoptimized: 11 queries (1 + 10 for owners)");
        logger.info("Current optimized: {} queries", metrics.getQueryCount());
        if (metrics.getQueryCount() == 1) {
            logger.info("✅ SUCCESS: N+1 problem FIXED! 91% query reduction achieved.");
        } else {
            logger.warn("❌ ISSUE: Expected 1 query, got {}", metrics.getQueryCount());
        }
        logger.info("=".repeat(80));

        // The key assertion: should be no more than 2 queries
        assertThat(metrics.getQueryCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void testOwnerListEndpoint_analyzeOwnerPetN1Problem() {
        // Start capturing SQL queries
        sqlQueryInterceptor.startCapturing();

        // Call REST endpoint that loads all owners with their pets
        ResponseEntity<OwnerDto[]> response = restTemplate.getForEntity(
            baseUrl + "/owners", OwnerDto[].class);

        // Stop capturing
        sqlQueryInterceptor.stopCapturing();

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        OwnerDto[] owners = response.getBody();
        assertThat(owners.length).isGreaterThan(0);

        // Get query metrics
        SqlQueryInterceptor.QueryMetrics metrics = sqlQueryInterceptor.getMetrics();

        // Count owners with pets
        int ownersWithPets = 0;
        int totalPets = 0;
        for (OwnerDto owner : owners) {
            if (owner.getPets() != null && !owner.getPets().isEmpty()) {
                ownersWithPets++;
                totalPets += owner.getPets().size();
            }
        }

        // Log detailed analysis
        logger.info("=".repeat(80));
        logger.info("OWNER->PET N+1 ANALYSIS: GET /api/owners");
        logger.info("=".repeat(80));
        logger.info("Number of owners loaded: {}", owners.length);
        logger.info("Owners with pets: {}", ownersWithPets);
        logger.info("Total pets loaded: {}", totalPets);
        logger.info("Total SQL queries executed: {}", metrics.getQueryCount());
        logger.info("SELECT queries: {}", metrics.getQueryTypeAnalysis().getSelectCount());
        logger.info("JOIN queries: {}", metrics.getQueryTypeAnalysis().getJoinCount());
        logger.info("Execution time: {}ms", metrics.getExecutionTimeMs());
        logger.info("Likely N+1 problem: {}", metrics.getQueryTypeAnalysis().isLikelyN1Problem());
        logger.info("-".repeat(80));
        logger.info("SQL Queries executed:");
        for (int i = 0; i < metrics.getQueries().size(); i++) {
            logger.info("{}. {}", (i + 1), metrics.getQueries().get(i));
        }
        logger.info("=".repeat(80));
        logger.info("N+1 ANALYSIS RESULT:");
        logger.info("Expected for optimal: 1 query with JOINs (owners + pets)");
        logger.info("Expected for N+1 problem: 1 + N queries (1 for owners + {} for pets)", ownersWithPets);
        logger.info("Actual queries executed: {}", metrics.getQueryCount());

        // Assert that query count is no more than 2
        assertThat(metrics.getQueryCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void testVetListEndpoint_analyzeVetSpecialtyN1Problem() {
        // Start capturing SQL queries
        sqlQueryInterceptor.startCapturing();

        // Call REST endpoint that loads all vets with their specialties
        ResponseEntity<VetDto[]> response = restTemplate.getForEntity(
            baseUrl + "/vets", VetDto[].class);

        // Stop capturing
        sqlQueryInterceptor.stopCapturing();

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        VetDto[] vets = response.getBody();
        assertThat(vets.length).isGreaterThan(0);

        // Get query metrics
        SqlQueryInterceptor.QueryMetrics metrics = sqlQueryInterceptor.getMetrics();

        // Count vets with specialties
        int vetsWithSpecialties = 0;
        int totalSpecialties = 0;
        for (VetDto vet : vets) {
            if (vet.getSpecialties() != null && !vet.getSpecialties().isEmpty()) {
                vetsWithSpecialties++;
                totalSpecialties += vet.getSpecialties().size();
            }
        }

        // Log detailed analysis
        logger.info("=".repeat(80));
        logger.info("VET->SPECIALTY N+1 ANALYSIS: GET /api/vets");
        logger.info("=".repeat(80));
        logger.info("Number of vets loaded: {}", vets.length);
        logger.info("Vets with specialties: {}", vetsWithSpecialties);
        logger.info("Total specialties loaded: {}", totalSpecialties);
        logger.info("Total SQL queries executed: {}", metrics.getQueryCount());
        logger.info("SELECT queries: {}", metrics.getQueryTypeAnalysis().getSelectCount());
        logger.info("JOIN queries: {}", metrics.getQueryTypeAnalysis().getJoinCount());
        logger.info("Execution time: {}ms", metrics.getExecutionTimeMs());
        logger.info("Likely N+1 problem: {}", metrics.getQueryTypeAnalysis().isLikelyN1Problem());
        logger.info("-".repeat(80));
        logger.info("SQL Queries executed:");
        for (int i = 0; i < metrics.getQueries().size(); i++) {
            logger.info("{}. {}", (i + 1), metrics.getQueries().get(i));
        }
        logger.info("=".repeat(80));
        logger.info("N+1 ANALYSIS RESULT:");
        logger.info("Expected for optimal: 1 query with JOINs (vets + specialties)");
        logger.info("Expected for N+1 problem: 1 + N queries (1 for vets + {} for specialties)", vetsWithSpecialties);
        logger.info("Actual queries executed: {}", metrics.getQueryCount());

        // Assert that query count is no more than 2
        assertThat(metrics.getQueryCount()).isLessThanOrEqualTo(2);
    }
}
