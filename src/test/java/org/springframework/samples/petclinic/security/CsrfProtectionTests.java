package org.springframework.samples.petclinic.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.rest.dto.OwnerDto;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class for CSRF protection
 */
@SpringBootTest
@WebAppConfiguration
public class CsrfProtectionTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        // Setup MockMvc with security
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    /**
     * Test that GET requests work without CSRF token
     */
    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(get("/api/owners")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    /**
     * Test that POST requests are rejected without CSRF token
     */
    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testPostRequestWithoutCsrfToken() throws Exception {
        OwnerDto ownerDto = new OwnerDto()
                .firstName("John")
                .lastName("Doe")
                .address("123 Main St")
                .city("Anytown")
                .telephone("1234567890");

        ObjectMapper mapper = new ObjectMapper();
        String ownerJson = mapper.writeValueAsString(ownerDto);

        mockMvc.perform(post("/api/owners")
                .content(ownerJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    /**
     * Test that POST requests are accepted with valid CSRF token
     */
    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testPostRequestWithCsrfToken() throws Exception {
        OwnerDto ownerDto = new OwnerDto()
                .firstName("John")
                .lastName("Doe")
                .address("123 Main St")
                .city("Anytown")
                .telephone("1234567890");

        ObjectMapper mapper = new ObjectMapper();
        String ownerJson = mapper.writeValueAsString(ownerDto);

        // Use Spring Security's test utilities to add a valid CSRF token
        mockMvc.perform(post("/api/owners")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .content(ownerJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    /**
     * Test that PUT requests are rejected without CSRF token
     */
    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testPutRequestWithoutCsrfToken() throws Exception {
        OwnerDto ownerDto = new OwnerDto()
                .id(1)
                .firstName("John")
                .lastName("Doe")
                .address("123 Main St")
                .city("Anytown")
                .telephone("1234567890");

        ObjectMapper mapper = new ObjectMapper();
        String ownerJson = mapper.writeValueAsString(ownerDto);

        mockMvc.perform(put("/api/owners/1")
                .content(ownerJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    /**
     * Test that DELETE requests are rejected without CSRF token
     */
    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testDeleteRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(delete("/api/owners/1")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    /**
     * Test that GET requests are not affected by CSRF protection
     */
    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetRequestsExcludedFromCsrf() throws Exception {
        // Test that GET requests work without CSRF token
        mockMvc.perform(get("/api/owners")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // Should return 200 OK, not 403 Forbidden
    }
}
