package org.springframework.samples.petclinic.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AuthControllerTests extends OAuthIntegrationIT {

    @Test
    void testUserDetailsAndRolesStorage() throws Exception {
        setupOAuth2StubsForUser("admin@example.com", "Admin", "User", "google-admin");

        String sessionCookie = createOAuth2Session();
        assertNotNull(sessionCookie, "OAuth2 login should create a session cookie");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", sessionCookie);

        ResponseEntity<String> userResponse = restTemplate.exchange(
                baseUrl + "/api/session/user", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertTrue(userResponse.getStatusCode().is2xxSuccessful(), "/api/session/user should return 2xx after login");
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = objectMapper.readValue(userResponse.getBody(), Map.class);

        // Verify OAuth2 user details are stored
        assertEquals("admin@example.com", responseBody.get("email"));
        assertEquals("Admin", responseBody.get("firstName"));
        assertEquals("User", responseBody.get("lastName"));

        // Verify roles are assigned and stored
        assertNotNull(responseBody.get("roles"));
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) responseBody.get("roles");
        assertFalse(roles.isEmpty(), "Admin user should have roles assigned");

        assertTrue(roles.contains("ROLE_ADMIN"), "Admin user should have ROLE_ADMIN role");
        assertTrue(roles.contains("ROLE_VET_ADMIN"), "Admin user should have ROLE_VET_ADMIN role");
        assertTrue(roles.contains("ROLE_OWNER_ADMIN"), "Admin user should have ROLE_OWNER_ADMIN role");
        assertEquals(3, roles.size(), "Admin user should have exactly 3 roles");
    }

}
