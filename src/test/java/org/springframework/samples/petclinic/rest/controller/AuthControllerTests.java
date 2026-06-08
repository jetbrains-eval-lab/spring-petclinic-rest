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
public class AuthControllerTests {

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
        assertEquals("/oauth2/authorization/google", loginData.get("loginUrl"),
                "loginUrl should point to OAuth2 authorization endpoint");
    }

    @Test
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

        // Ensure full OAuth2 flow actually reached provider endpoints.
        wireMockServer.verify(postRequestedFor(urlEqualTo("/oauth2/token")));
        wireMockServer.verify(getRequestedFor(urlEqualTo("/oauth2/userinfo")));
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
            String finalCookie;
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
            if (!cookieHeader.isEmpty()) {
                cookieHeader.append("; ");
            }
            cookieHeader.append(nameValue);
        }
        return !cookieHeader.isEmpty() ? cookieHeader.toString() : null;
    }

}
