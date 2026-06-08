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
import org.springframework.samples.petclinic.rest.dto.VetDto;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractVetRestControllerN1IntegrationTests {

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
    @WithMockUser(roles = "VET_ADMIN")
    void listVets_shouldFetchAllAssociationsInSingleQuery() throws Exception {
        MvcResult result = mockMvc.perform(get("/petclinic/api/vets")
                .contextPath("/petclinic")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        List<VetDto> vets = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<List<VetDto>>() { }
        );

        assertThat(vets).hasSize(6);

        VetDto linda = vets.stream()
            .filter(v -> v.getId() != null && v.getId() == 3)
            .findFirst()
            .orElseThrow();
        assertThat(linda.getSpecialties()).hasSize(2);

        assertThat(statistics.getPrepareStatementCount())
            .as("all entities are fetched in a single join fetch query")
            .isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "VET_ADMIN")
    void getVet_shouldFetchAllAssociationsInSingleQuery() throws Exception {
        MvcResult result = mockMvc.perform(get("/petclinic/api/vets/3")
                .contextPath("/petclinic")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        VetDto vet = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(), VetDto.class);

        assertThat(vet.getId()).isEqualTo(3);
        assertThat(vet.getSpecialties()).hasSize(2);

        assertThat(statistics.getPrepareStatementCount())
            .as("all entities are fetched in a single join fetch query")
            .isEqualTo(1);
    }
}
