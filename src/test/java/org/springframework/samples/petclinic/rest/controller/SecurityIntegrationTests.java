/*
 * Copyright 2016-2024 the original author or authors.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.mapper.VetMapper;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.rest.dto.PetFieldsDto;
import org.springframework.samples.petclinic.rest.dto.PetTypeDto;
import org.springframework.samples.petclinic.rest.dto.VisitFieldsDto;
import org.springframework.samples.petclinic.service.ClinicService;

import java.time.LocalDate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for security role hierarchy and dynamic ownership checks.
 * Tests verify:
 * - Role hierarchy is correctly enforced
 * - Dynamic ownership checks work for ROLE_VET and ROLE_OWNER
 * - Access denials are properly handled
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"hsqldb", "spring-data-jpa"})
class SecurityIntegrationTests {

    /**
     * Expected security log marker as per requirements:
     * "Each denial log entry must include the marker SECURITY_ACCESS_DENIED"
     */
    private static final String SECURITY_LOG_MARKER = "SECURITY_ACCESS_DENIED";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClinicService clinicService;

    @Autowired
    private VetMapper vetMapper;

    @Autowired
    private ObjectMapper objectMapper;

    // Test data with usernames is pre-loaded from test/resources/db/hsqldb/data.sql:
    // - owner1 (id=1, username='owner1', ROLE_OWNER)
    // - owner2 (id=2, username='owner2', ROLE_OWNER)
    // - vet1 (id=1, username='vet1', ROLE_VET)
    // - vet2 (id=2, username='vet2', ROLE_VET)
    // - user1 (ROLE_USER)
    // - admin (ROLE_ADMIN, ROLE_VET_ADMIN, ROLE_OWNER_ADMIN)

    @Nested
    @DisplayName("Role Hierarchy Tests")
    @ExtendWith(OutputCaptureExtension.class)
    class RoleHierarchyTests {

        @Test
        @DisplayName("ADMIN can access VET_ADMIN endpoints via hierarchy")
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        void adminCanAccessVetAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/vets")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("OWNER_ADMIN can access USER endpoints via hierarchy")
        @WithMockUser(username = "owneradmin", roles = {"OWNER_ADMIN"})
        void ownerAdminCanAccessUserEndpoints() throws Exception {
            mockMvc.perform(get("/api/pettypes")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("VET_ADMIN cannot access OWNER_ADMIN endpoints")
        @WithMockUser(username = "vetadmin", roles = {"VET_ADMIN"})
        void vetAdminCannotAccessOwnerAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/owners")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OWNER_ADMIN cannot access VET_ADMIN endpoints + log verification")
        @WithMockUser(username = "owneradmin", roles = {"OWNER_ADMIN"})
        void ownerAdminCannotAccessVetAdminEndpoints(CapturedOutput output) throws Exception {
            mockMvc.perform(get("/api/vets")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

            assertThat(output.getAll()).contains(SECURITY_LOG_MARKER);
            assertThat(output.getAll()).contains("owneradmin");
        }

        @Test
        @DisplayName("OWNER cannot access VET_ADMIN endpoints + log verification")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCannotAccessVetAdminEndpoints(CapturedOutput output) throws Exception {
            mockMvc.perform(get("/api/vets")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

            assertThat(output.getAll()).contains(SECURITY_LOG_MARKER);
            assertThat(output.getAll()).contains("owner1");
        }
    }

    @Nested
    @DisplayName("Dynamic VET Ownership Tests")
    @ExtendWith(OutputCaptureExtension.class)
    class VetOwnershipTests {

        @Test
        @DisplayName("VET can access own profile")
        @WithMockUser(username = "vet1", roles = {"VET"})
        void vetCanAccessOwnProfile() throws Exception {
            mockMvc.perform(get("/api/vets/1")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("VET cannot access other vet's profile + log verification")
        @WithMockUser(username = "vet1", roles = {"VET"})
        void vetCannotAccessOtherVetProfile(CapturedOutput output) throws Exception {
            mockMvc.perform(get("/api/vets/2")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

            assertThat(output.getAll()).contains(SECURITY_LOG_MARKER);
            assertThat(output.getAll()).contains("vet1");
        }

        @Test
        @DisplayName("VET can update own profile")
        @WithMockUser(username = "vet1", roles = {"VET"})
        void vetCanUpdateOwnProfile() throws Exception {
            Vet vet = clinicService.findVetById(1);
            String vetJson = objectMapper.writeValueAsString(vetMapper.toVetDto(vet));

            mockMvc.perform(put("/api/vets/1")
                    .content(vetJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("VET cannot list all vets")
        @WithMockUser(username = "vet1", roles = {"VET"})
        void vetCannotListAllVets() throws Exception {
            mockMvc.perform(get("/api/vets")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("VET cannot update other vet's profile + log verification")
        @WithMockUser(username = "vet1", roles = {"VET"})
        void vetCannotUpdateOtherVetProfile(CapturedOutput output) throws Exception {
            Vet vet2 = clinicService.findVetById(2);
            String vetJson = objectMapper.writeValueAsString(vetMapper.toVetDto(vet2));

            mockMvc.perform(put("/api/vets/2")
                    .content(vetJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

            assertThat(output.getAll()).contains(SECURITY_LOG_MARKER);
            assertThat(output.getAll()).contains("vet1");
        }
    }

    @Nested
    @DisplayName("Dynamic OWNER Ownership Tests")
    @ExtendWith(OutputCaptureExtension.class)
    class OwnerOwnershipTests {

        @Test
        @DisplayName("OWNER can access own profile")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCanAccessOwnProfile() throws Exception {
            mockMvc.perform(get("/api/owners/1")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("OWNER cannot access other owner's profile + log verification")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCannotAccessOtherOwnerProfile(CapturedOutput output) throws Exception {
            mockMvc.perform(get("/api/owners/2")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

            assertThat(output.getAll()).contains(SECURITY_LOG_MARKER);
            assertThat(output.getAll()).contains("owner1");
        }

        @Test
        @DisplayName("OWNER can access own pet")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCanAccessOwnPet() throws Exception {
            // Pet with id=1 belongs to owner with id=1
            mockMvc.perform(get("/api/pets/1")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("OWNER cannot access other owner's pet + log verification")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCannotAccessOtherOwnerPet(CapturedOutput output) throws Exception {
            // Pet with id=2 belongs to owner with id=2
            mockMvc.perform(get("/api/pets/2")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

            assertThat(output.getAll()).contains(SECURITY_LOG_MARKER);
            assertThat(output.getAll()).contains("owner1");
        }

        @Test
        @DisplayName("OWNER cannot list all owners")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCannotListAllOwners() throws Exception {
            mockMvc.perform(get("/api/owners")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OWNER cannot list all pets")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCannotListAllPets() throws Exception {
            mockMvc.perform(get("/api/pets")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        }

        // --- Priority 2: CRUD Tests for Pets ---

        private PetTypeDto createPetType(int id, String name) {
            PetTypeDto petType = new PetTypeDto();
            petType.setId(id);
            petType.setName(name);
            return petType;
        }

        @Test
        @DisplayName("OWNER can update own pet")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCanUpdateOwnPet() throws Exception {
            PetFieldsDto petFieldsDto = new PetFieldsDto();
            petFieldsDto.setName("Leo Updated");
            petFieldsDto.setBirthDate(LocalDate.of(2010, 9, 7));
            petFieldsDto.setType(createPetType(1, "cat"));
            String petJson = objectMapper.writeValueAsString(petFieldsDto);

            mockMvc.perform(put("/api/owners/1/pets/1")
                    .content(petJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("OWNER cannot update other owner's pet + log verification")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCannotUpdateOtherOwnerPet(CapturedOutput output) throws Exception {
            PetFieldsDto petFieldsDto = new PetFieldsDto();
            petFieldsDto.setName("Hacked Pet");
            petFieldsDto.setBirthDate(LocalDate.of(2012, 8, 6));
            petFieldsDto.setType(createPetType(6, "hamster"));
            String petJson = objectMapper.writeValueAsString(petFieldsDto);

            // owner1 tries to update pet 2 (belongs to owner2)
            mockMvc.perform(put("/api/owners/2/pets/2")
                    .content(petJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

            assertThat(output.getAll()).contains(SECURITY_LOG_MARKER);
            assertThat(output.getAll()).contains("owner1");
        }

        @Test
        @DisplayName("OWNER can create pet for own profile")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCanCreatePetForOwnProfile() throws Exception {
            PetFieldsDto petFieldsDto = new PetFieldsDto();
            petFieldsDto.setName("New Pet");
            petFieldsDto.setBirthDate(LocalDate.of(2023, 1, 1));
            petFieldsDto.setType(createPetType(2, "dog"));
            String petJson = objectMapper.writeValueAsString(petFieldsDto);

            mockMvc.perform(post("/api/owners/1/pets")
                    .content(petJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("OWNER cannot create pet for other owner + log verification")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCannotCreatePetForOtherOwner(CapturedOutput output) throws Exception {
            PetFieldsDto petFieldsDto = new PetFieldsDto();
            petFieldsDto.setName("Injected Pet");
            petFieldsDto.setBirthDate(LocalDate.of(2023, 1, 1));
            petFieldsDto.setType(createPetType(2, "dog"));
            String petJson = objectMapper.writeValueAsString(petFieldsDto);

            // owner1 tries to create pet for owner2
            mockMvc.perform(post("/api/owners/2/pets")
                    .content(petJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

            assertThat(output.getAll()).contains(SECURITY_LOG_MARKER);
            assertThat(output.getAll()).contains("owner1");
        }

        // --- Priority 3: Visit Ownership Tests ---

        @Test
        @DisplayName("OWNER can create visit for own pet")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCanCreateVisitForOwnPet() throws Exception {
            VisitFieldsDto visitFieldsDto = new VisitFieldsDto();
            visitFieldsDto.setDate(LocalDate.of(2024, 6, 15));
            visitFieldsDto.setDescription("Annual checkup");
            String visitJson = objectMapper.writeValueAsString(visitFieldsDto);

            mockMvc.perform(post("/api/owners/1/pets/1/visits")
                    .content(visitJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("OWNER cannot create visit for other owner's pet + log verification")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void ownerCannotCreateVisitForOtherOwnerPet(CapturedOutput output) throws Exception {
            VisitFieldsDto visitFieldsDto = new VisitFieldsDto();
            visitFieldsDto.setDate(LocalDate.of(2024, 6, 15));
            visitFieldsDto.setDescription("Malicious visit");
            String visitJson = objectMapper.writeValueAsString(visitFieldsDto);

            // owner1 tries to create visit for pet 2 (belongs to owner2)
            mockMvc.perform(post("/api/owners/2/pets/2/visits")
                    .content(visitJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

            assertThat(output.getAll()).contains(SECURITY_LOG_MARKER);
            assertThat(output.getAll()).contains("owner1");
        }
    }

    @Nested
    @DisplayName("Admin Bypass Tests")
    class AdminBypassTests {

        @Test
        @DisplayName("OWNER_ADMIN can access any owner profile")
        @WithMockUser(username = "owneradmin", roles = {"OWNER_ADMIN"})
        void ownerAdminCanAccessAnyOwnerProfile() throws Exception {
            mockMvc.perform(get("/api/owners/1")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("OWNER_ADMIN has full access to owner endpoints")
        @WithMockUser(username = "owneradmin", roles = {"OWNER_ADMIN"})
        void ownerAdminFullAccessToOwnerEndpoints() throws Exception {
            // GET owners
            mockMvc.perform(get("/api/owners")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            // GET pets
            mockMvc.perform(get("/api/pets")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            // GET visits
            mockMvc.perform(get("/api/visits")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("VET_ADMIN has full access to vet endpoints")
        @WithMockUser(username = "vetadmin", roles = {"VET_ADMIN"})
        void vetAdminFullAccessToVetEndpoints() throws Exception {
            // GET vets
            mockMvc.perform(get("/api/vets")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            // GET specialties
            mockMvc.perform(get("/api/specialties")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            // GET pettypes
            mockMvc.perform(get("/api/pettypes")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN has full system access")
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        void adminFullSystemAccess() throws Exception {
            // ADMIN should be able to access VET_ADMIN endpoints via hierarchy
            mockMvc.perform(get("/api/vets")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            // ADMIN should be able to access OWNER_ADMIN endpoints via hierarchy
            mockMvc.perform(get("/api/owners")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("ROLE_USER Tests")
    class RoleUserTests {

        @Test
        @DisplayName("ROLE_USER can read pet types")
        @WithMockUser(username = "user1", roles = {"USER"})
        void userCanReadPetTypes() throws Exception {
            mockMvc.perform(get("/api/pettypes")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ROLE_USER cannot modify pet types")
        @WithMockUser(username = "user1", roles = {"USER"})
        void userCannotModifyPetTypes() throws Exception {
            PetTypeDto petTypeDto = new PetTypeDto();
            petTypeDto.setName("newtype");
            String petTypeJson = objectMapper.writeValueAsString(petTypeDto);

            mockMvc.perform(post("/api/pettypes")
                    .content(petTypeJson)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Unauthenticated Access Tests")
    class UnauthenticatedAccessTests {

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedRequestReturns401() throws Exception {
            mockMvc.perform(get("/api/vets")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Security Audit Logging Tests")
    @ExtendWith(OutputCaptureExtension.class)
    class SecurityAuditLoggingTests {

        @Test
        @DisplayName("Access denied logs SECURITY_ACCESS_DENIED marker with principal name")
        @WithMockUser(username = "owner1", roles = {"OWNER"})
        void accessDeniedLogsSecurityMarkerWithPrincipal(CapturedOutput output) throws Exception {
            mockMvc.perform(get("/api/owners")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

            assertThat(output.getAll()).contains(SECURITY_LOG_MARKER);
            assertThat(output.getAll()).contains("owner1");
        }

        @Test
        @DisplayName("Unauthenticated access logs SECURITY_ACCESS_DENIED with anonymous")
        void unauthenticatedAccessLogsAnonymous(CapturedOutput output) throws Exception {
            mockMvc.perform(get("/api/owners")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

            assertThat(output.getAll()).contains(SECURITY_LOG_MARKER);
            assertThat(output.getAll()).contains("anonymous");
        }
    }
}

