package org.springframework.samples.petclinic.rest.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@link CustomHeaderFilter}
 * This test verifies that the custom headers are added to all HTTP responses
 * by using a real application context with all filters applied.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomHeaderFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles="OWNER_ADMIN")
    void shouldAddCustomHeadersToResponse() throws Exception {
        // When making any request to the API
        mockMvc.perform(get("/api/pettypes"))
                // Then the response should include our custom headers
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Custom-Header"))
                .andExpect(header().string("X-Custom-Header", "PetClinic-API"))
                .andExpect(header().exists("X-Request-ID"));
    }
}
