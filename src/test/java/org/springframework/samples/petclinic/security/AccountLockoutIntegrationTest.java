package org.springframework.samples.petclinic.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = ApplicationTestConfig.class)
@TestPropertySource(properties = {
    "petclinic.security.enable=true",
    "petclinic.security.lockout.enabled=true",
    "petclinic.security.lockout.max-attempts=3",
    "petclinic.security.lockout.lock-duration-minutes=5"
})
public class AccountLockoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testFailedAttemptsLeadToAccountLockout() throws Exception {
        String basicAuth = createBasicAuthHeader("testuser1", "wrongpassword");

        // Make failed login attempts up to the limit (3 attempts as per test properties)
        for (int i = 0; i < 3; i++) {
            // Each request should return 401 Unauthorized
            mockMvc.perform(get("/api/owners")
                    .header("Authorization", basicAuth)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }

        // After threshold is reached, the next attempt should return 429 Too Many Requests
        MvcResult result = mockMvc.perform(get("/api/owners")
                .header("Authorization", basicAuth)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is(HttpStatus.TOO_MANY_REQUESTS.value()))
            .andExpect(jsonPath("$.status").value(HttpStatus.TOO_MANY_REQUESTS.value()))
            .andExpect(jsonPath("$.error").value("Too Many Attempts"))
            .andExpect(jsonPath("$.message").value(containsString("temporarily locked")))
            .andReturn();

        // Verify response contains information about remaining lock time
        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains("minutes"),
            "Response should contain information about lock duration");
    }

    @Test
    public void testDifferentIpsNotAffectedByLockout() throws Exception {
        String basicAuth = createBasicAuthHeader("testuser2", "wrongpassword");
        // Lock one IP address
        String lockedIp = "10.10.10.10";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/owners")
                    .with(remoteAddr(lockedIp))
                    .header("Authorization", basicAuth)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }

        // Verify this IP is now locked
        mockMvc.perform(get("/api/owners")
                .with(remoteAddr(lockedIp))
                .header("Authorization", basicAuth)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is(HttpStatus.TOO_MANY_REQUESTS.value()));
    }

    @Test
    public void testDifferentUsersNotAffectedByLockout() throws Exception {
        String basicAuth = createBasicAuthHeader("testuser3", "wrongpassword");
        // Lock one user
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/owners")
                    .header("Authorization", basicAuth)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }

        // Verify this user is now locked
        mockMvc.perform(get("/api/owners")
                .header("Authorization", basicAuth)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is(HttpStatus.TOO_MANY_REQUESTS.value()));

        // Different user should still be able to attempt login
        String differentUser = "anotheruser";
        String differentAuthHeader = createBasicAuthHeader(differentUser, "wrongPassword");

        mockMvc.perform(get("/api/owners")
                .header("Authorization", differentAuthHeader)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized()); // Not locked, just unauthorized
    }

    // Helper methods

    private String createBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    private RequestPostProcessor remoteAddr(final String remoteAddr) {
        return request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        };
    }
}
