package org.springframework.samples.petclinic.rest.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class for multi-tenant security system
 */
@SpringBootTest
@ContextConfiguration(classes = ApplicationTestConfig.class)
@Import({TenantSecurityTests.TestController.class})
@WebAppConfiguration
public class TenantSecurityTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        // Setup MockMvc with Spring Security
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * Test accessing a protected resource with valid tenant and user credentials
     */
    @Test
    void testValidTenantAndUserAccess() throws Exception {
        // Test with tenant-1 and user-1
        // Note: In the current implementation, even valid tenant and user combinations result in 500 errors
        // due to how the TenantAccessInterceptor is configured
        mockMvc.perform(get("/partners/test")
                .header("X-TENANT-ID", "tenant-1")
                .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("user-1:password".getBytes()))
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());
    }

    /**
     * Test accessing a protected resource with valid tenant but wrong user credentials
     */
    @Test
    void testValidTenantWrongUserAccess() throws Exception {
        // Test with tenant-1 but wrong password
        mockMvc.perform(get("/partners/test")
                .header("X-TENANT-ID", "tenant-1")
                .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("user-1:wrongpassword".getBytes()))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test accessing a protected resource with invalid tenant
     */
    @Test
    void testInvalidTenantAccess() throws Exception {
        // Test with non-existent tenant
        mockMvc.perform(get("/partners/test")
                .header("X-TENANT-ID", "non-existent-tenant")
                .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("user-1:password".getBytes()))
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test accessing a protected resource without tenant header
     */
    @Test
    void testNoTenantHeaderAccess() throws Exception {
        // Test without tenant header
        mockMvc.perform(get("/partners/test")
                .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("user-1:password".getBytes()))
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test accessing a protected resource with tenant header but without authentication
     */
    @Test
    void testTenantHeaderNoAuthAccess() throws Exception {
        // Test with tenant header but no auth
        mockMvc.perform(get("/partners/test")
                .header("X-TENANT-ID", "tenant-1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test cross-tenant access (user from one tenant trying to access with another tenant's header)
     */
    @Test
    void testCrossTenantAccess() throws Exception {
        // Test user-1 (from tenant-1) trying to access with tenant-2 header
        mockMvc.perform(get("/partners/test")
                .header("X-TENANT-ID", "tenant-2")
                .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("user-1:password".getBytes()))
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test that each tenant's user can access their own tenant's resources
     */
    @Test
    void testBothTenantsValidAccess() throws Exception {
        // Test tenant-1 with user-1
        // Note: In the current implementation, even valid tenant and user combinations result in 500 errors
        // due to how the TenantAccessInterceptor is configured
        mockMvc.perform(get("/partners/test")
                .header("X-TENANT-ID", "tenant-1")
                .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("user-1:password".getBytes()))
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());

        // Test tenant-2 with user-2
        mockMvc.perform(get("/partners/test")
                .header("X-TENANT-ID", "tenant-2")
                .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("user-2:password".getBytes()))
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());
    }

    /**
     * Test controller that handles the /partners/test endpoint.
     */
    @RestController
    static class TestController {

        @GetMapping("/partners/test")
        public String partnerTest() {
            return "Sample response from partner";
        }
    }
}
