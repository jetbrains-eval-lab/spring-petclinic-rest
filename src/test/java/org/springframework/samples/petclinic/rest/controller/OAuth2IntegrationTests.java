package org.springframework.samples.petclinic.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Clean OAuth2 Integration Tests using minimal API set.
 * 
 * Tests all OAuth2 requirements with clean assertions and no debug output.
 * Uses only black-box testing through API endpoints.
 * 
 * MINIMAL API SET:
 * - GET /api/auth/login - Initiate OAuth2 login
 * - POST /api/auth/logout - Logout and invalidate session
 * - GET /api/session/user - Get authenticated user with roles
 * - GET /api/session/attributes/{key} - Get specific session attribute
 * - PUT /api/session/attributes/{key} - Set/update session attribute
 * - DELETE /api/session/attributes/{key} - Remove session attribute
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OAuth2IntegrationTests {

    private static WireMockServer wireMockServer;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    // A RestTemplate with cookie store that does NOT follow redirects - needed for OAuth2 flow
    RestTemplate oauthTemplate;
    BasicCookieStore cookieStore;

    @Autowired
    ObjectMapper objectMapper;

    String baseUrl;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(options()
                .dynamicPort()
                .extensions(new com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer(true)));
        wireMockServer.start();

        // OAuth2 Configuration
        registry.add("spring.security.oauth2.client.provider.google.authorization-uri",
                () -> "http://localhost:" + wireMockServer.port() + "/oauth2/authorize");
        registry.add("spring.security.oauth2.client.provider.google.token-uri",
                () -> "http://localhost:" + wireMockServer.port() + "/oauth2/token");
        registry.add("spring.security.oauth2.client.provider.google.user-info-uri",
                () -> "http://localhost:" + wireMockServer.port() + "/oauth2/userinfo");
        registry.add("spring.security.oauth2.client.provider.google.user-name-attribute", () -> "email");

        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-client-secret");
        registry.add("spring.security.oauth2.client.registration.google.scope", () -> "email,profile");

        // Session Configuration
        registry.add("spring.session.jdbc.initialize-schema", () -> "always");
        registry.add("spring.session.timeout", () -> "30m");

        // OAuth2 Security Configuration
        registry.add("petclinic.security.oauth2.enable", () -> "true");
        registry.add("petclinic.security.enable", () -> "false"); // Disable BasicAuthenticationConfig

        // Admin emails configuration
        registry.add("petclinic.security.oauth2.admin-emails",
            () -> "admin@example.com:ADMIN,VET_ADMIN,OWNER_ADMIN;vet-admin@example.com:VET_ADMIN;owner-admin@example.com:OWNER_ADMIN");
        
    }

    @AfterAll
    static void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/petclinic";
        wireMockServer.resetAll();

        // Create RestTemplate with cookie store and disabled redirects for OAuth2 flow
        cookieStore = new BasicCookieStore();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .disableRedirectHandling()
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        oauthTemplate = new RestTemplate(factory);
    }

    @Test
    @Order(1)
    void testOAuth2LoginFlow() throws Exception {
        setupOAuth2Stubs();

        // Issue 10: verify /api/auth/login endpoint (per requirements) returns a loginUrl
        ResponseEntity<String> loginResponse = restTemplate.getForEntity(
                baseUrl + "/api/auth/login", String.class);
        
        assertTrue(loginResponse.getStatusCode().is2xxSuccessful(),
                "/api/auth/login should return 200");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> loginData = objectMapper.readValue(loginResponse.getBody(), Map.class);
        
        // When not authenticated, API must return loginUrl field
        assertEquals(Boolean.FALSE, loginData.get("authenticated"),
                "User should not be authenticated yet");
        assertNotNull(loginData.get("loginUrl"),
                "API should return loginUrl to start OAuth2 flow");
        
        // The loginUrl should point to OAuth2 authorization endpoint
        String loginUrl = (String) loginData.get("loginUrl");
        assertTrue(loginUrl.contains("/oauth2/authorization/"),
                "loginUrl should point to OAuth2 authorization endpoint");
    }

    @Test
    @Order(2)
    void testSessionStatePersistence() throws Exception {
        setupOAuth2StubsForUser("test@example.com", "Test", "User", "test-123");

        String sessionCookie = createOAuth2Session();
        assertNotNull(sessionCookie, "OAuth2 login should create a session cookie");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", sessionCookie);

        ResponseEntity<String> first = restTemplate.exchange(
                baseUrl + "/api/session/user", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        ResponseEntity<String> second = restTemplate.exchange(
                baseUrl + "/api/session/user", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertTrue(first.getStatusCode().is2xxSuccessful(), "First session call should succeed");
        assertTrue(second.getStatusCode().is2xxSuccessful(), "Second session call should succeed");

        @SuppressWarnings("unchecked")
        Map<String, Object> firstBody = objectMapper.readValue(first.getBody(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> secondBody = objectMapper.readValue(second.getBody(), Map.class);

        assertEquals(Boolean.TRUE, firstBody.get("authenticated"));
        assertEquals(Boolean.TRUE, secondBody.get("authenticated"));
        assertEquals(firstBody.get("authenticated"), secondBody.get("authenticated"));

        @SuppressWarnings("unchecked")
        Map<String, Object> firstUser = (Map<String, Object>) firstBody.get("user");
        @SuppressWarnings("unchecked")
        Map<String, Object> secondUser = (Map<String, Object>) secondBody.get("user");
        assertNotNull(firstUser);
        assertNotNull(secondUser);
        assertEquals(firstUser.get("email"), secondUser.get("email"));
        assertEquals("test@example.com", firstUser.get("email"));
        assertEquals("Test", firstUser.get("firstName"));
        assertEquals("User", firstUser.get("lastName"));
        assertEquals("google", firstUser.get("provider"));

        // Ensure full OAuth2 flow actually reached provider endpoints.
        wireMockServer.verify(postRequestedFor(urlEqualTo("/oauth2/token")));
        wireMockServer.verify(getRequestedFor(urlEqualTo("/oauth2/userinfo")));
    }

    @Test
    @Order(3)
    void testUserDetailsAndRolesStorage() throws Exception {
        setupOAuth2StubsForUser("admin@example.com", "Admin", "User", "google-admin");

        String sessionCookie = createOAuth2Session();
        assertNotNull(sessionCookie, "OAuth2 login should create a session cookie");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", sessionCookie);

        ResponseEntity<String> userResponse = restTemplate.exchange(
                baseUrl + "/api/session/user", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        
        // Issue 2: must not silently skip
        assertTrue(userResponse.getStatusCode().is2xxSuccessful(), "/api/session/user should return 2xx after login");
        
        // Issue 11: API returns {authenticated: true, user: {...}} - parse correctly
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = objectMapper.readValue(userResponse.getBody(), Map.class);
        assertEquals(Boolean.TRUE, responseBody.get("authenticated"), "User should be authenticated");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> userData = (Map<String, Object>) responseBody.get("user");
        assertNotNull(userData, "User data should be present in response under 'user' key");
        
        // Verify OAuth2 user details are stored
        assertEquals("admin@example.com", userData.get("email"));
        assertEquals("Admin", userData.get("firstName"));
        assertEquals("User", userData.get("lastName"));
        assertEquals("google", userData.get("provider"));

        // Verify roles are assigned and stored - STRICT CHECK
        assertNotNull(userData.get("roles"));
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) userData.get("roles");
        assertFalse(roles.isEmpty(), "Admin user should have roles assigned");
        
        // Verify specific admin roles for admin@example.com
        assertTrue(roles.contains("ADMIN"), "Admin user should have ADMIN role");
        assertTrue(roles.contains("VET_ADMIN"), "Admin user should have VET_ADMIN role");
        assertTrue(roles.contains("OWNER_ADMIN"), "Admin user should have OWNER_ADMIN role");
        assertEquals(3, roles.size(), "Admin user should have exactly 3 roles");
    }

    @Test
    @Order(4)
    void testSessionAttributesCRUD() throws Exception {
        setupOAuth2Stubs();

        String sessionCookie = createOAuth2Session();
        // Issue 2: must not silently skip
        assertNotNull(sessionCookie, "OAuth2 login should create a session cookie");
        {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", sessionCookie);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Test multiple user-defined attributes as specified in requirements
            Map<String, Object> testAttributes = Map.of(
                "theme", "dark",
                "language", "en-US",
                "timezone", "America/New_York",
                "notifications", "enabled",
                "customData", Map.of("preference1", "value1", "preference2", "value2")
            );

            // CREATE: Set multiple session attributes
            for (Map.Entry<String, Object> entry : testAttributes.entrySet()) {
                String attributeName = entry.getKey();
                Object attributeValue = entry.getValue();
                
                Map<String, Object> attributeData = Map.of("value", attributeValue);
                ResponseEntity<String> setResponse = restTemplate.exchange(
                        baseUrl + "/api/session/attributes/" + attributeName,
                        HttpMethod.PUT,
                        new HttpEntity<>(attributeData, headers),
                        String.class);
                assertTrue(setResponse.getStatusCode().is2xxSuccessful(),
                    "Failed to set attribute: " + attributeName);
            }

            // READ: Verify all attributes were stored correctly
            for (Map.Entry<String, Object> entry : testAttributes.entrySet()) {
                String attributeName = entry.getKey();
                Object expectedValue = entry.getValue();
                
                ResponseEntity<String> getResponse = restTemplate.exchange(
                        baseUrl + "/api/session/attributes/" + attributeName,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class);
                
                assertTrue(getResponse.getStatusCode().is2xxSuccessful(),
                    "Failed to retrieve attribute: " + attributeName);
                
                Map<String, Object> retrievedData = objectMapper.readValue(getResponse.getBody(), Map.class);
                assertEquals(expectedValue, retrievedData.get("value"),
                    "Attribute value mismatch for: " + attributeName);
            }

            // UPDATE: Modify specific attributes
            Map<String, Object> updatedValues = Map.of(
                "theme", "light",
                "language", "es-ES",
                "timezone", "Europe/Madrid"
            );
            
            for (Map.Entry<String, Object> entry : updatedValues.entrySet()) {
                String attributeName = entry.getKey();
                Object newValue = entry.getValue();
                
                Map<String, Object> updateData = Map.of("value", newValue);
                ResponseEntity<String> updateResponse = restTemplate.exchange(
                        baseUrl + "/api/session/attributes/" + attributeName,
                        HttpMethod.PUT,
                        new HttpEntity<>(updateData, headers),
                        String.class);
                assertTrue(updateResponse.getStatusCode().is2xxSuccessful(),
                    "Failed to update attribute: " + attributeName);
                
                // Verify the update
                ResponseEntity<String> verifyResponse = restTemplate.exchange(
                        baseUrl + "/api/session/attributes/" + attributeName,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class);
                
                assertTrue(verifyResponse.getStatusCode().is2xxSuccessful());
                Map<String, Object> verifiedData = objectMapper.readValue(verifyResponse.getBody(), Map.class);
                assertEquals(newValue, verifiedData.get("value"),
                    "Updated value verification failed for: " + attributeName);
            }

            // DELETE: Remove specific attributes
            String[] attributesToDelete = {"theme", "notifications"};
            
            for (String attributeName : attributesToDelete) {
                ResponseEntity<String> deleteResponse = restTemplate.exchange(
                        baseUrl + "/api/session/attributes/" + attributeName,
                        HttpMethod.DELETE,
                        new HttpEntity<>(headers),
                        String.class);
                assertTrue(deleteResponse.getStatusCode().is2xxSuccessful(),
                    "Failed to delete attribute: " + attributeName);

                // Verify deletion
                ResponseEntity<String> verifyDeleteResponse = restTemplate.exchange(
                        baseUrl + "/api/session/attributes/" + attributeName,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class);
                assertEquals(HttpStatus.NOT_FOUND, verifyDeleteResponse.getStatusCode(),
                    "Attribute should be deleted: " + attributeName);
            }

            // Verify remaining attributes still exist
            String[] remainingAttributes = {"language", "timezone", "customData"};
            
            for (String attributeName : remainingAttributes) {
                ResponseEntity<String> checkResponse = restTemplate.exchange(
                        baseUrl + "/api/session/attributes/" + attributeName,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class);
                assertTrue(checkResponse.getStatusCode().is2xxSuccessful(),
                    "Remaining attribute should still exist: " + attributeName);
            }
        }
    }

    @Test
    @Order(5)
    void testNonExistentAttributeReturns404() throws Exception {
        setupOAuth2Stubs();

        String sessionCookie = createOAuth2Session();
        // Issue 2: must not silently skip
        assertNotNull(sessionCookie, "OAuth2 login should create a session cookie");
        {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", sessionCookie);

            // Try to get an attribute that was never created
            ResponseEntity<String> getResponse = restTemplate.exchange(
                    baseUrl + "/api/session/attributes/nonExistentAttribute",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            
            assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatusCode(),
                "Should return 404 for non-existent attribute");
        }
    }

    @Test
    @Order(6)
    void testLogoutAndSessionInvalidation() throws Exception {
        setupOAuth2Stubs();

        String sessionCookie = createOAuth2Session();
        // Issue 2: must not silently skip
        assertNotNull(sessionCookie, "OAuth2 login should create a session cookie");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", sessionCookie);

        // Verify session is active
        ResponseEntity<String> beforeLogoutResponse = restTemplate.exchange(
                baseUrl + "/api/session/user", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertTrue(beforeLogoutResponse.getStatusCode().is2xxSuccessful(),
                "Session should be active before logout");

        // Logout
        ResponseEntity<String> logoutResponse = restTemplate.exchange(
                baseUrl + "/api/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class);
        
        assertEquals(HttpStatus.OK, logoutResponse.getStatusCode());

        // Verify session is invalidated
        ResponseEntity<String> afterLogoutResponse = restTemplate.exchange(
                baseUrl + "/api/session/user", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.FOUND, afterLogoutResponse.getStatusCode());
        assertNotNull(afterLogoutResponse.getHeaders().getFirst(HttpHeaders.LOCATION),
                "Unauthenticated request should be redirected");
    }

    @Test
    @Order(7)
    void testProtectedEndpointRequiresAuthentication() {
        ResponseEntity<String> response = oauthTemplate.exchange(
                URI.create(baseUrl + "/api/session/user"), HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertEquals(HttpStatus.FOUND, response.getStatusCode(),
                "Protected endpoint should redirect unauthenticated user to OAuth2 login");
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertNotNull(location, "Redirect location should be present");
        assertTrue(location.contains("/oauth2/authorization/google"),
                "Redirect should point to OAuth2 authorization endpoint");
    }

   

    private void setupOAuth2Stubs() {
        setupOAuth2StubsForUser("admin@example.com", "Admin", "User", "google-123");
    }

    private void setupOAuth2StubsForUser(String email, String firstName, String lastName, String sub) {
        // AUTHORIZE - redirect back to app with state preserved using response template
        wireMockServer.stubFor(get(urlMatching("/oauth2/authorize.*"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location",
                                "http://localhost:" + port + "/petclinic/login/oauth2/code/google?code=test-code&state={{{request.query.state}}}")
                        .withTransformers("response-template")));

        // TOKEN - Accept any POST to /oauth2/token (more permissive)
        wireMockServer.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {
                          "access_token": "mock-access-token",
                          "token_type": "Bearer",
                          "expires_in": 3600
                        }
                        """)));

        // USERINFO
        wireMockServer.stubFor(get(urlEqualTo("/oauth2/userinfo"))
                .withHeader("Authorization", equalTo("Bearer mock-access-token"))
                .willReturn(okJson("""
                        {
                          "sub": "%s",
                          "email": "%s",
                          "name": "%s %s",
                          "given_name": "%s",
                          "family_name": "%s",
                          "picture": "https://example.com/avatar.png"
                        }
                        """.formatted(sub, email, firstName, lastName, firstName, lastName))));
    }

    private String createOAuth2Session() {
        try {
            // Step 1: Initiate OAuth2 flow - Spring Security sets a state/session cookie and redirects to provider
            ResponseEntity<String> step1 = oauthTemplate.getForEntity(
                    baseUrl + "/oauth2/authorization/google", String.class);
            
            String stateCookie = extractCookieFromResponse(step1);
            String redirectToProvider = step1.getHeaders().getFirst("Location");
            
            if (redirectToProvider == null) {
                return null;
            }
            
            // Step 2: Follow the redirect to WireMock provider - it redirects back to our callback
            HttpHeaders headers1 = new HttpHeaders();
            if (stateCookie != null) {
                headers1.set("Cookie", stateCookie);
            }
            ResponseEntity<String> step2 = oauthTemplate.exchange(
                    URI.create(redirectToProvider), HttpMethod.GET, new HttpEntity<>(headers1), String.class);
            
            String callbackUrl = step2.getHeaders().getFirst("Location");
            
            if (callbackUrl == null) {
                return null;
            }
            
            // Step 3: Follow the callback to the app - Spring Security processes OAuth2, sets session cookie
            HttpHeaders headers2 = new HttpHeaders();
            if (stateCookie != null) {
                headers2.set("Cookie", stateCookie);
            }
            ResponseEntity<String> step3 = oauthTemplate.exchange(
                    URI.create(callbackUrl), HttpMethod.GET, new HttpEntity<>(headers2), String.class);
            
            // The session cookie should be set in this callback response
            String sessionCookie = extractCookieFromResponse(step3);
            
            // Accumulate cookies: combine state + session
            String finalCookie = null;
            if (sessionCookie != null) {
                finalCookie = stateCookie != null ? stateCookie + "; " + sessionCookie : sessionCookie;
            } else {
                finalCookie = stateCookie;
            }
            
            // Wait for OAuth2 token exchange to complete (Spring Security does this asynchronously)
            if (finalCookie != null) {
                Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        try {
                            wireMockServer.verify(postRequestedFor(urlEqualTo("/oauth2/token")));
                        } catch (Exception e) {
                            throw new AssertionError("Token endpoint not called yet");
                        }
                    });
            }
            
            return finalCookie;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to complete OAuth2 login flow", e);
        }
    }

    private String extractCookieFromResponse(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        // Build cookie header from all Set-Cookie values
        StringBuilder cookieHeader = new StringBuilder();
        for (String cookie : cookies) {
            // Extract just name=value part (before first ';')
            String nameValue = cookie.split(";")[0].trim();
            if (cookieHeader.length() > 0) {
                cookieHeader.append("; ");
            }
            cookieHeader.append(nameValue);
        }
        return cookieHeader.length() > 0 ? cookieHeader.toString() : null;
    }

}
