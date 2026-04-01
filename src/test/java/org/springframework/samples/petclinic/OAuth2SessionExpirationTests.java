package org.springframework.samples.petclinic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(properties = {
    "petclinic.security.oauth2.enable=true",
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret",
    "server.servlet.session.timeout=1"
})
@AutoConfigureMockMvc
@TestPropertySource(properties = "petclinic.security.enable=false")
class OAuth2SessionExpirationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testNoSessionRedirectsToLogin() throws Exception {
        // No session / no authentication → should redirect to OAuth2 provider
        mockMvc.perform(get("/api/session/user"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void testInvalidatedSessionRedirectsToLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // Establish authenticated session
        mockMvc.perform(get("/api/session/user")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("sub", "expire-test-123");
                    attrs.put("email", "expire@example.com");
                }))
                .session(session))
            .andExpect(status().isOk());

        // Invalidate the session (simulates expiration)
        session.invalidate();

        // Access without new authentication → redirect
        MockHttpSession newSession = new MockHttpSession();
        mockMvc.perform(get("/api/session/user").session(newSession))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void testSessionAttributesLostAfterInvalidation() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // Set an attribute in the session
        mockMvc.perform(put("/api/session/attributes/timezone")
                .with(oauth2Login())
                .session(session)
                .contentType(APPLICATION_JSON)
                .content("\"UTC\""))
            .andExpect(status().isOk());

        // Invalidate session
        session.invalidate();

        // New session should not have the attribute
        MockHttpSession freshSession = new MockHttpSession();
        mockMvc.perform(get("/api/session/attributes/timezone")
                .with(oauth2Login())
                .session(freshSession))
            .andExpect(status().isNotFound());
    }

    @Test
    void testSessionRemainsValidAcrossMultipleRequests() throws Exception {
        MockHttpSession session = new MockHttpSession();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/session/user")
                    .with(oauth2Login().attributes(attrs -> {
                        attrs.put("sub", "persist-123");
                        attrs.put("email", "persist@example.com");
                    }))
                    .session(session))
                .andExpect(status().isOk());
        }
    }
}
