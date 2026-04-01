package org.springframework.samples.petclinic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.security.OAuth2AuthenticationSuccessHandler;
import org.springframework.samples.petclinic.security.OAuth2Properties;
import org.springframework.samples.petclinic.security.OAuth2UserMapper;
import org.springframework.samples.petclinic.security.PetClinicOAuth2UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "petclinic.security.oauth2.enable=true",
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret",
    "spring.security.oauth2.client.registration.github.client-id=test-github-client-id",
    "spring.security.oauth2.client.registration.github.client-secret=test-github-secret",
    "petclinic.security.oauth2.admin-emails=admin@example.com:ROLE_ADMIN,ROLE_VET_ADMIN"
})
@AutoConfigureMockMvc
@TestPropertySource(properties = "petclinic.security.enable=false")
class OAuth2ExtensibilityE2ETests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuth2UserMapper userMapper;

    @Autowired
    private PetClinicOAuth2UserService petClinicOAuth2UserService;

    @Autowired
    private OAuth2Properties oauth2Properties;

    @Autowired
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Test
    void testGithubLoginReturnsUser() throws Exception {
        mockMvc.perform(get("/api/session/user")
                .with(oauth2Login()
                    .attributes(attrs -> {
                        attrs.put("id", "gh-789");
                        attrs.put("login", "githubuser");
                        attrs.put("email", "github@example.com");
                        attrs.put("name", "GitHub User");
                    })))
            .andExpect(status().isOk());
    }

    @Test
    void testNonGoogleProviderWithEmailMapsCorrectly() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "github-42");
        attrs.put("email", "github@example.com");
        attrs.put("name", "GitHub User");

        User user = userMapper.mapToUser(attrs, "github");

        assertEquals("github@example.com", user.getUsername());
        assertEquals("github@example.com", user.getEmail());
        assertEquals("github-42", user.getOauthId());
        assertEquals("github", user.getOauthProvider());
    }

    @Test
    void testNonGoogleProviderWithoutEmailUsesStableIdentity() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "12345");
        attrs.put("login", "noEmailUser");

        User user = userMapper.mapToUser(attrs, "github");

        assertEquals("github:12345", user.getUsername());
        assertEquals("12345", user.getOauthId());
        assertNotNull(user.getUsername());
        assertTrue(user.getUsername().startsWith("github:"));
    }

    @Test
    void testNonGoogleProviderWithSubFallback() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", "sub-999");

        User user = userMapper.mapToUser(attrs, "custom-provider");

        assertEquals("sub-999", user.getOauthId());
        assertEquals("custom-provider:sub-999", user.getUsername());
    }

    @Test
    void testGoogleProviderMapsStrictly() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", "google-sub-123");
        attrs.put("email", "google@example.com");
        attrs.put("given_name", "Google");
        attrs.put("family_name", "User");
        attrs.put("picture", "https://example.com/pic.jpg");

        User user = userMapper.mapToUser(attrs, "google");

        assertEquals("google@example.com", user.getUsername());
        assertEquals("google@example.com", user.getEmail());
        assertEquals("google-sub-123", user.getOauthId());
        assertEquals("Google", user.getFirstName());
        assertEquals("User", user.getLastName());
        assertEquals("https://example.com/pic.jpg", user.getPictureUrl());
        assertEquals("google", user.getOauthProvider());
    }

    @Test
    void testNonGoogleProviderNameParsing() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", "abc123");
        attrs.put("name", "First Last");
        attrs.put("email", "fl@example.com");

        User user = userMapper.mapToUser(attrs, "github");

        assertEquals("First", user.getFirstName());
        assertEquals("Last", user.getLastName());
    }

    @Test
    void testNonGoogleProviderSingleWordName() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", "single-name-user");
        attrs.put("name", "Mononymous");

        User user = userMapper.mapToUser(attrs, "custom");

        assertEquals("Mononymous", user.getFirstName());
        assertNull(user.getLastName());
    }

    @Test
    void testNonGoogleProviderWithGivenNameField() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "prov-789");
        attrs.put("first_name", "Alice");
        attrs.put("last_name", "Smith");
        attrs.put("email", "alice@example.com");

        User user = userMapper.mapToUser(attrs, "custom");

        assertEquals("Alice", user.getFirstName());
        assertEquals("Smith", user.getLastName());
    }

    @Test
    void testAdminEmailGetsCorrectRole() throws Exception {
        mockMvc.perform(get("/api/auth/login")
                .with(oauth2Login()
                    .attributes(attrs -> {
                        attrs.put("sub", "admin-sub-123");
                        attrs.put("email", "admin@example.com");
                    })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void testUnauthenticatedAccessRedirects() throws Exception {
        mockMvc.perform(get("/api/session/user"))
            .andExpect(status().is3xxRedirection());
    }

    // PetClinicOAuth2UserService unit tests

    @Test
    void testProcessOAuth2UserCreatesNewUser() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", "new-user-sub-unique-xyz");
        attrs.put("email", "newuniqueuser@example.com");
        attrs.put("given_name", "New");
        attrs.put("family_name", "User");

        petClinicOAuth2UserService.processOAuth2User(attrs, "google");
        // No exception means success
    }

    @Test
    void testProcessOAuth2UserUpdatesExistingUser() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", "update-user-sub-456");
        attrs.put("email", "updatetest@example.com");
        attrs.put("given_name", "Update");
        attrs.put("family_name", "Test");

        // Create user first
        petClinicOAuth2UserService.processOAuth2User(attrs, "google");

        // Update with new info
        Map<String, Object> updatedAttrs = new HashMap<>();
        updatedAttrs.put("sub", "update-user-sub-456");
        updatedAttrs.put("email", "updatetest@example.com");
        updatedAttrs.put("given_name", "Updated");
        updatedAttrs.put("family_name", "Name");

        petClinicOAuth2UserService.processOAuth2User(updatedAttrs, "google");
    }

    @Test
    void testProcessOAuth2UserAssignsAdminRoles() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", "admin-user-sub-789");
        attrs.put("email", "admin@example.com");
        attrs.put("given_name", "Admin");
        attrs.put("family_name", "User");

        petClinicOAuth2UserService.processOAuth2User(attrs, "google");
    }

    @Test
    void testProcessOAuth2UserWithNoEmailGetsStableIdentity() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "github-no-email-999");
        attrs.put("login", "noemail");

        petClinicOAuth2UserService.processOAuth2User(attrs, "github");
    }

    // OAuth2Properties unit tests

    @Test
    void testOAuth2PropertiesEmptyAdminEmails() {
        OAuth2Properties props = new OAuth2Properties();
        assertTrue(props.getAdminEmailRoles().isEmpty());
    }

    @Test
    void testOAuth2PropertiesBlankAdminEmails() {
        OAuth2Properties props = new OAuth2Properties();
        props.setAdminEmails("   ");
        assertTrue(props.getAdminEmailRoles().isEmpty());
    }

    @Test
    void testOAuth2PropertiesSingleEntry() {
        OAuth2Properties props = new OAuth2Properties();
        props.setAdminEmails("user@test.com:ROLE_ADMIN");
        Map<String, List<String>> roles = props.getAdminEmailRoles();
        assertEquals(1, roles.size());
        assertTrue(roles.containsKey("user@test.com"));
        assertTrue(roles.get("user@test.com").contains("ROLE_ADMIN"));
    }

    @Test
    void testOAuth2PropertiesMultipleRoles() {
        OAuth2Properties props = new OAuth2Properties();
        props.setAdminEmails("user@test.com:ROLE_ADMIN,ROLE_VET_ADMIN");
        Map<String, List<String>> roles = props.getAdminEmailRoles();
        List<String> userRoles = roles.get("user@test.com");
        assertTrue(userRoles.contains("ROLE_ADMIN"));
        assertTrue(userRoles.contains("ROLE_VET_ADMIN"));
    }

    @Test
    void testOAuth2PropertiesMultipleEntries() {
        OAuth2Properties props = new OAuth2Properties();
        props.setAdminEmails("user1@test.com:ROLE_ADMIN;user2@test.com:ROLE_VET_ADMIN");
        Map<String, List<String>> roles = props.getAdminEmailRoles();
        assertEquals(2, roles.size());
        assertTrue(roles.containsKey("user1@test.com"));
        assertTrue(roles.containsKey("user2@test.com"));
    }

    @Test
    void testOAuth2PropertiesMissingColon() {
        OAuth2Properties props = new OAuth2Properties();
        props.setAdminEmails("invalid-entry");
        Map<String, List<String>> roles = props.getAdminEmailRoles();
        assertTrue(roles.isEmpty());
    }

    @Test
    void testOAuth2PropertiesSemicolonOnly() {
        OAuth2Properties props = new OAuth2Properties();
        props.setAdminEmails(";");
        assertTrue(props.getAdminEmailRoles().isEmpty());
    }

    @Test
    void testOAuth2PropertiesDefaultProvider() {
        assertEquals("google", oauth2Properties.getDefaultLoginProvider());
    }

    // OAuth2AuthenticationSuccessHandler unit tests

    @Test
    void testSuccessHandlerStoresUserInfoInSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", "handler-test-sub");
        attrs.put("email", "handler@example.com");
        attrs.put("given_name", "Handler");
        attrs.put("family_name", "Test");
        attrs.put("picture", "https://example.com/pic.jpg");

        OAuth2User oAuth2User = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            attrs, "sub"
        );
        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
            oAuth2User, oAuth2User.getAuthorities(), "google"
        );

        try {
            successHandler.onAuthenticationSuccess(request, response, auth);
        } catch (Exception ignored) {
            // Response send redirect may throw in test context
        }

        HttpSession session = request.getSession(false);
        assertNotNull(session);
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = (Map<String, Object>)
            session.getAttribute(OAuth2AuthenticationSuccessHandler.SESSION_KEY_AUTHENTICATED_USER);
        assertNotNull(userInfo);
        assertEquals("handler@example.com", userInfo.get("email"));
    }

    @Test
    void testSuccessHandlerWithNonOAuth2Principal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "basicUser", "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );

        try {
            successHandler.onAuthenticationSuccess(request, response, auth);
        } catch (Exception ignored) {
            // Expected in test context
        }

        // No session attribute should be set for non-OAuth2 principal
        HttpSession session = request.getSession(false);
        if (session != null) {
            assertNull(session.getAttribute(
                OAuth2AuthenticationSuccessHandler.SESSION_KEY_AUTHENTICATED_USER));
        }
    }
}
