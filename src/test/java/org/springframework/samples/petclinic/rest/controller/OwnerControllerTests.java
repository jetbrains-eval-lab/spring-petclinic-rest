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
class OwnerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    // --- OWNER accessing own profile ---

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCanGetOwnProfile() throws Exception {
        mockMvc.perform(get("/api/owners/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.firstName").value("George"));
    }

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCannotGetOtherProfile() throws Exception {
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
    void ownerCanUpdateOwnProfile() throws Exception {
        mockMvc.perform(put("/api/owners/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"George\",\"lastName\":\"Franklin\",\"address\":\"110 W. Liberty St.\",\"city\":\"Madison\",\"telephone\":\"6085551023\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCannotUpdateOtherProfile() throws Exception {
        mockMvc.perform(put("/api/owners/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Betty\",\"lastName\":\"Davis\",\"address\":\"638 Cardinal Ave.\",\"city\":\"Sun Prairie\",\"telephone\":\"6085551749\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCannotDeleteOwnProfile() throws Exception {
        mockMvc.perform(delete("/api/owners/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCannotCreateOwner() throws Exception {
        mockMvc.perform(post("/api/owners")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"New\",\"lastName\":\"Owner\",\"address\":\"123 St.\",\"city\":\"City\",\"telephone\":\"1234567890\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    // --- OWNER_ADMIN access ---

    @Test
    @WithMockUser(username = "admin", roles = {"OWNER_ADMIN"})
    void ownerAdminCanListAllOwners() throws Exception {
        mockMvc.perform(get("/api/owners").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"OWNER_ADMIN"})
    void ownerAdminCanGetAnyOwner() throws Exception {
        mockMvc.perform(get("/api/owners/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"OWNER_ADMIN"})
    void ownerAdminCanUpdateAnyOwner() throws Exception {
        mockMvc.perform(put("/api/owners/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Betty\",\"lastName\":\"Davis\",\"address\":\"638 Cardinal Ave.\",\"city\":\"Sun Prairie\",\"telephone\":\"6085551749\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"OWNER_ADMIN"})
    void ownerAdminCanDeleteOwner() throws Exception {
        mockMvc.perform(delete("/api/owners/10").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());
    }

    // --- USER role denied ---

    @Test
    @WithMockUser(username = "user1", roles = {"USER"})
    void userCannotAccessOwners() throws Exception {
        mockMvc.perform(get("/api/owners/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    // --- OWNER pet and visit management ---

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCanGetOwnPet() throws Exception {
        mockMvc.perform(get("/api/owners/1/pets/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Leo"));
    }

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCannotGetOtherOwnerPet() throws Exception {
        mockMvc.perform(get("/api/owners/2/pets/2").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCanAddVisitToOwnPet() throws Exception {
        mockMvc.perform(post("/api/owners/1/pets/1/visits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2024-01-01\",\"description\":\"checkup\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "owner1", roles = {"OWNER"})
    void ownerCannotAddVisitToOtherOwnerPet() throws Exception {
        mockMvc.perform(post("/api/owners/2/pets/2/visits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2024-01-01\",\"description\":\"checkup\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }
}
