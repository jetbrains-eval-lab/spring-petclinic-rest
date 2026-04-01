package org.springframework.samples.petclinic.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "petclinic.security.enable=true")
@Transactional
class SecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    // --- Role hierarchy tests ---

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanAccessOwners() throws Exception {
        mockMvc.perform(get("/api/owners").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanAccessVets() throws Exception {
        mockMvc.perform(get("/api/vets").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanAccessPetTypes() throws Exception {
        mockMvc.perform(get("/api/pettypes").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanAccessUsers() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"password\":\"pass\",\"enabled\":true,\"roles\":[{\"name\":\"ROLE_USER\"}]}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated());
    }

    // --- USER role tests ---

    @Test
    @WithMockUser(username = "user1", roles = {"USER"})
    void userCanAccessPetTypes() throws Exception {
        mockMvc.perform(get("/api/pettypes").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user1", roles = {"USER"})
    void userCannotAccessOwners() throws Exception {
        mockMvc.perform(get("/api/owners").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user1", roles = {"USER"})
    void userCannotAccessVets() throws Exception {
        mockMvc.perform(get("/api/vets").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user1", roles = {"USER"})
    void userCannotCreatePetType() throws Exception {
        mockMvc.perform(post("/api/pettypes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"fish\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user1", roles = {"USER"})
    void userCannotCreateUser() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"hacker\",\"password\":\"pass\",\"enabled\":true,\"roles\":[{\"name\":\"ROLE_ADMIN\"}]}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    // --- VET role tests ---

    @Test
    @WithMockUser(username = "vet1", roles = {"VET"})
    void vetCanAccessOwnProfile() throws Exception {
        mockMvc.perform(get("/api/vets/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "vet1", roles = {"VET"})
    void vetCannotAccessOtherVetProfile() throws Exception {
        mockMvc.perform(get("/api/vets/2").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "vet1", roles = {"VET"})
    void vetCannotListAllVets() throws Exception {
        mockMvc.perform(get("/api/vets").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "vet1", roles = {"VET"})
    void vetCanAccessPetTypesBecauseOfHierarchy() throws Exception {
        mockMvc.perform(get("/api/pettypes").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    // --- OWNER role tests ---

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCanAccessOwnProfile() throws Exception {
        mockMvc.perform(get("/api/owners/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCannotAccessOtherOwnerProfile() throws Exception {
        mockMvc.perform(get("/api/owners/2").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCannotListAllOwners() throws Exception {
        mockMvc.perform(get("/api/owners").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCanAccessPetTypesBecauseOfHierarchy() throws Exception {
        mockMvc.perform(get("/api/pettypes").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    // --- ADMIN role hierarchy tests ---

    @Test
    @WithMockUser(username = "admin", roles = {"OWNER_ADMIN"})
    void ownerAdminCanAccessAnyOwner() throws Exception {
        mockMvc.perform(get("/api/owners/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"VET_ADMIN"})
    void vetAdminCanAccessAnyVet() throws Exception {
        mockMvc.perform(get("/api/vets/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"VET_ADMIN"})
    void vetAdminCanCreatePetType() throws Exception {
        mockMvc.perform(post("/api/pettypes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"fish\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated());
    }

    // --- Unauthenticated tests ---

    @Test
    void unauthenticatedCannotAccessOwners() throws Exception {
        mockMvc.perform(get("/api/owners").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedCannotAccessPetTypes() throws Exception {
        mockMvc.perform(get("/api/pettypes").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    // --- Cross-role denial tests ---

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCannotAccessVets() throws Exception {
        mockMvc.perform(get("/api/vets").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "vet1", roles = {"VET"})
    void vetCannotAccessOwners() throws Exception {
        mockMvc.perform(get("/api/owners").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"OWNER_ADMIN"})
    void ownerAdminCannotAccessVets() throws Exception {
        mockMvc.perform(get("/api/vets").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"VET_ADMIN"})
    void vetAdminCannotAccessOwners() throws Exception {
        mockMvc.perform(get("/api/owners").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }
}
