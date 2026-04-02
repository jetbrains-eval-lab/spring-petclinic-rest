package org.springframework.samples.petclinic.rest.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.rest.dto.PetDto;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractPetRestControllerN1IntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void listPets_shouldReturnDistinctPetDtosWithoutOwnerNPlusOne() throws Exception {
        MvcResult result = mockMvc.perform(get("/petclinic/api/pets")
                .contextPath("/petclinic")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        List<PetDto> pets = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<List<PetDto>>() { }
        );

        assertThat(pets).hasSize(13);
        assertThat(pets).extracting(PetDto::getId).doesNotHaveDuplicates();
        assertThat(pets).allSatisfy(pet -> {
            assertThat(pet.getOwnerId()).as("ownerId for pet %s", pet.getId()).isNotNull();
            assertThat(pet.getType()).as("type for pet %s", pet.getId()).isNotNull();
            assertThat(pet.getType().getName()).as("type name for pet %s", pet.getId()).isNotBlank();
            assertThat(pet.getVisits()).as("visits for pet %s", pet.getId()).isNotNull();
        });

        PetDto leo = petById(pets, 1);
        assertThat(leo.getOwnerId()).isEqualTo(1);
        assertThat(leo.getType().getName()).isEqualTo("cat");
        assertThat(leo.getVisits()).isEmpty();

        PetDto samantha = petById(pets, 7);
        assertThat(samantha.getOwnerId()).isEqualTo(6);
        assertThat(samantha.getType().getName()).isEqualTo("cat");
        assertThat(samantha.getVisits()).hasSize(2);

        PetDto max = petById(pets, 8);
        assertThat(max.getOwnerId()).isEqualTo(6);
        assertThat(max.getType().getName()).isEqualTo("cat");
        assertThat(max.getVisits()).hasSize(2);

        assertThat(statistics.getPrepareStatementCount())
            .as("all entities are fetched in a single join fetch query")
            .isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void getPet_shouldFetchAllAssociationsInSingleQuery() throws Exception {
        MvcResult result = mockMvc.perform(get("/petclinic/api/pets/7")
                .contextPath("/petclinic")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        PetDto pet = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), PetDto.class);

        assertThat(pet.getId()).isEqualTo(7);
        assertThat(pet.getName()).isEqualTo("Samantha");
        assertThat(pet.getOwnerId()).isEqualTo(6);
        assertThat(pet.getType().getName()).isEqualTo("cat");
        assertThat(pet.getVisits()).hasSize(2);

        assertThat(statistics.getPrepareStatementCount())
            .as("all entities are fetched in a single join fetch query")
            .isEqualTo(1);
    }

    private PetDto petById(List<PetDto> pets, int id) {
        return pets.stream()
            .filter(pet -> pet.getId() != null && pet.getId() == id)
            .findFirst()
            .orElseThrow();
    }
}
