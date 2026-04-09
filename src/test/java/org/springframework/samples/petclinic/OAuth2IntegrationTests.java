package org.springframework.samples.petclinic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "petclinic.security.oauth2.enable=true",
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret",
    "spring.security.oauth2.client.registration.google.scope=openid,email,profile"
})
@AutoConfigureMockMvc
@TestPropertySource(properties = "petclinic.security.enable=false")
class OAuth2IntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testLoginStatusUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.loginUrl").value("/oauth2/authorization/google"));
    }

    @Test
    void testLoginStatusAuthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/login")
                .with(oauth2Login()
                    .attributes(attrs -> {
                        attrs.put("sub", "google-123");
                        attrs.put("email", "user@example.com");
                        attrs.put("given_name", "Test");
                        attrs.put("family_name", "User");
                    })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void testUnauthenticatedAccessToProtectedEndpointReturns302() throws Exception {
        mockMvc.perform(get("/api/session/user"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void testGetSessionUserAuthenticated() throws Exception {
        mockMvc.perform(get("/api/session/user")
                .with(oauth2Login()
                    .attributes(attrs -> {
                        attrs.put("sub", "google-456");
                        attrs.put("email", "john@example.com");
                        attrs.put("given_name", "John");
                        attrs.put("family_name", "Doe");
                    })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("john@example.com"));
    }

    @Test
    void testSessionAttributesCRUD() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // Set attribute
        mockMvc.perform(put("/api/session/attributes/theme")
                .with(oauth2Login())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"dark\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value("dark"));

        // Get attribute
        mockMvc.perform(get("/api/session/attributes/theme")
                .with(oauth2Login())
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value("dark"));

        // Delete attribute
        mockMvc.perform(delete("/api/session/attributes/theme")
                .with(oauth2Login())
                .session(session))
            .andExpect(status().isOk());

        // Attribute should be gone
        mockMvc.perform(get("/api/session/attributes/theme")
                .with(oauth2Login())
                .session(session))
            .andExpect(status().isNotFound());
    }

    @Test
    void testSessionAttributeReservedKeyBlocked() throws Exception {
        mockMvc.perform(put("/api/session/attributes/authenticated_user")
                .with(oauth2Login())
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"hacked\""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testLogout() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // Authenticate first
        mockMvc.perform(get("/api/session/user")
                .with(oauth2Login())
                .session(session))
            .andExpect(status().isOk());

        // Logout
        mockMvc.perform(post("/api/auth/logout")
                .session(session))
            .andExpect(status().isOk());
    }

    @Test
    void testLogoutWithNoSession() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isOk());
    }

    @Test
    void testGetSessionUserAfterLogout() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Authenticate first to establish a session
        mockMvc.perform(get("/api/session/user")
                .with(oauth2Login())
                .session(session))
            .andExpect(status().isOk());

        // 2. Logout with that session
        mockMvc.perform(post("/api/auth/logout")
                .session(session))
            .andExpect(status().isOk());

        // 3. Immediately call GET /api/session/user again with the now-invalidated session
        // It should redirect (302) to the OAuth2 login page, NOT return 404
        mockMvc.perform(get("/api/session/user")
                .session(session))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void testSessionPersistenceAcrossRequests() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // First request: set attribute
        mockMvc.perform(put("/api/session/attributes/language")
                .with(oauth2Login())
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"en\""))
            .andExpect(status().isOk());

        // Second request: verify attribute persisted
        mockMvc.perform(get("/api/session/attributes/language")
                .with(oauth2Login())
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value("en"));
    }

    @Test
    void testGetMissingAttribute() throws Exception {
        mockMvc.perform(get("/api/session/attributes/nonexistent")
                .with(oauth2Login()))
            .andExpect(status().isNotFound());
    }
}
