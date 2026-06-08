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

abstract class AbstractOwnerRestControllerN1IntegrationTests {

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
    void listOwners_shouldFetchAllAssociationsInSingleQuery() throws Exception {
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

        OwnerDto jean = ownerById(owners, 6);
        assertThat(jean.getPets()).hasSize(2);
        PetDto samantha = petById(jean.getPets(), 7);
        assertThat(samantha.getVisits()).hasSize(2);

        assertThat(statistics.getPrepareStatementCount())
            .as("all entities are fetched in a single join fetch query")
            .isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void listOwnersByLastName_shouldFetchAllAssociationsInSingleQuery() throws Exception {
        statistics.clear();

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
        OwnerDto jean = ownerById(owners, 6);
        assertThat(jean.getPets()).hasSize(2);
        PetDto samantha = petById(jean.getPets(), 7);
        assertThat(samantha.getVisits()).hasSize(2);

        assertThat(statistics.getPrepareStatementCount())
            .as("all entities are fetched in a single join fetch query")
            .isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void getOwner_shouldFetchAllAssociationsInSingleQuery() throws Exception {
        MvcResult result = mockMvc.perform(get("/petclinic/api/owners/6")
                .contextPath("/petclinic")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        OwnerDto owner = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), OwnerDto.class);

        assertThat(owner.getId()).isEqualTo(6);
        assertThat(owner.getPets()).hasSize(2);
        PetDto samantha = petById(owner.getPets(), 7);
        assertThat(samantha.getVisits()).hasSize(2);

        assertThat(statistics.getPrepareStatementCount())
            .as("all entities are fetched in a single join fetch query")
            .isEqualTo(1);
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
}
