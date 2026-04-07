package org.springframework.samples.petclinic.rest.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.rest.dto.OwnerDto;
import org.springframework.samples.petclinic.rest.dto.PetDto;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pass-to-pass regression guardrails for owner REST endpoints.
 */
abstract class AbstractOwnerRestControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void listOwners_shouldReturnPetsWithVisits() throws Exception {
        // Regression test: listing owners must still serialize pets and visits.
        MvcResult result = mockMvc.perform(get("/petclinic/api/owners")
                .contextPath("/petclinic")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        List<OwnerDto> owners = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<List<OwnerDto>>() { }
        );

        assertThat(owners).hasSize(10);

        OwnerDto george = ownerById(owners, 1);
        assertThat(george.getFirstName()).isEqualTo("George");
        assertThat(george.getLastName()).isEqualTo("Franklin");
        PetDto leo = petById(george.getPets(), 1);
        assertThat(leo.getType().getName()).isEqualTo("cat");
        assertThat(leo.getVisits()).isEmpty();

        OwnerDto jean = ownerById(owners, 6);
        assertThat(jean.getFirstName()).isEqualTo("Jean");
        assertThat(jean.getLastName()).isEqualTo("Coleman");
        assertThat(jean.getPets()).hasSize(2);
        assertPetHasVisits(jean.getPets(), 7, "Samantha", 2);
        assertPetHasVisits(jean.getPets(), 8, "Max", 2);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void getOwner_shouldReturnPetsWithVisits() throws Exception {
        // Regression test: fetching a single owner must still serialize pets and visits.
        MvcResult result = mockMvc.perform(get("/petclinic/api/owners/6")
                .contextPath("/petclinic")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        OwnerDto owner = objectMapper.readValue(result.getResponse().getContentAsByteArray(), OwnerDto.class);

        assertThat(owner.getId()).isEqualTo(6);
        assertThat(owner.getFirstName()).isEqualTo("Jean");
        assertThat(owner.getLastName()).isEqualTo("Coleman");
        assertThat(owner.getPets()).hasSize(2);
        assertPetHasVisits(owner.getPets(), 7, "Samantha", 2);
        assertPetHasVisits(owner.getPets(), 8, "Max", 2);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void listOwnersByLastName_shouldReturnMatchedOwnerWithPetVisits() throws Exception {
        // Regression test: owner search must still serialize pets and visits.
        MvcResult result = mockMvc.perform(get("/petclinic/api/owners")
                .contextPath("/petclinic")
                .queryParam("lastName", "Coleman")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        List<OwnerDto> owners = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<List<OwnerDto>>() { }
        );

        assertThat(owners).hasSize(1);
        OwnerDto owner = ownerById(owners, 6);
        assertThat(owner.getFirstName()).isEqualTo("Jean");
        assertThat(owner.getLastName()).isEqualTo("Coleman");
        assertThat(owner.getPets()).hasSize(2);
        assertPetHasVisits(owner.getPets(), 7, "Samantha", 2);
        assertPetHasVisits(owner.getPets(), 8, "Max", 2);
    }

    private OwnerDto ownerById(List<OwnerDto> owners, int id) {
        return owners.stream()
            .filter(owner -> owner.getId() != null && owner.getId() == id)
            .findFirst()
            .orElseThrow();
    }

    private PetDto petById(List<PetDto> pets, int id) {
        return pets.stream()
            .filter(pet -> pet.getId() != null && pet.getId() == id)
            .findFirst()
            .orElseThrow();
    }

    private void assertPetHasVisits(List<PetDto> pets, int petId, String expectedName, int expectedVisitCount) {
        PetDto pet = petById(pets, petId);
        assertThat(pet.getName()).isEqualTo(expectedName);
        assertThat(pet.getVisits()).hasSize(expectedVisitCount);
    }
}
