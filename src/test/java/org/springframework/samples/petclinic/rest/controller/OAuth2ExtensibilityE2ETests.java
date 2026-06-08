package org.springframework.samples.petclinic.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

/**
 * OAuth2 Extensibility E2E Test - GitHub Provider
 * 
 * Verifies that the system supports multiple OAuth2 providers beyond Google.
 * 
 * Flow: authorization -> callback -> token exchange -> userinfo -> session
 * 
 * Uses only MINIMAL API endpoints:
 * - GET /api/auth/login
 * - GET /api/session/user
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OAuth2ExtensibilityE2ETests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;
    // Issue 13: use dynamic port to avoid port conflicts
    private static WireMockServer wireMockServer;
    private ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate oauthTemplate;
    private BasicCookieStore cookieStore;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(options()
                .dynamicPort()
                .extensions(new com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer(true)));
        wireMockServer.start();

        // Configure OAuth2 properties for GitHub provider
        registry.add("petclinic.security.oauth2.enable", () -> "true");
        registry.add("petclinic.security.enable", () -> "false");
        registry.add("spring.security.oauth2.client.registration.github.client-id", () -> "test-github-client-id");
        registry.add("spring.security.oauth2.client.registration.github.client-secret", () -> "test-github-client-secret");
        registry.add("spring.security.oauth2.client.registration.github.scope", () -> "user:email");
        registry.add("spring.security.oauth2.client.provider.github.authorization-uri",
                () -> "http://localhost:" + wireMockServer.port() + "/oauth2/authorize");
        registry.add("spring.security.oauth2.client.provider.github.token-uri",
                () -> "http://localhost:" + wireMockServer.port() + "/oauth2/token");
        registry.add("spring.security.oauth2.client.provider.github.user-info-uri",
                () -> "http://localhost:" + wireMockServer.port() + "/user");
        registry.add("spring.security.oauth2.client.provider.github.user-name-attribute", () -> "login");

        // Session configuration
        registry.add("spring.session.jdbc.initialize-schema", () -> "always");
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

        cookieStore = new BasicCookieStore();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .disableRedirectHandling()
                .build();
        oauthTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    @Test
    void testGitHubOAuth2ProviderExtensibility() throws Exception {
        setupGitHubOAuth2Stubs();

        // Step 1: Verify /api/auth/login returns login info (per requirements)
        ResponseEntity<String> loginResponse = restTemplate.getForEntity(
                baseUrl + "/api/auth/login", String.class);
        assertTrue(loginResponse.getStatusCode().is2xxSuccessful(),
                "/api/auth/login should return 200");

        @SuppressWarnings("unchecked")
        Map<String, Object> loginData = objectMapper.readValue(loginResponse.getBody(), Map.class);
        assertEquals(Boolean.FALSE, loginData.get("authenticated"),
                "User should not be authenticated yet");

        // Step 2: go through full OAuth2 flow (authorization -> callback -> token -> userinfo)
        String sessionCookie = createGithubOAuth2Session();
        assertNotNull(sessionCookie,
                "Full GitHub OAuth2 flow should create a session cookie");

        // Step 3: Verify the session works with /api/session/user
        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", sessionCookie);

        ResponseEntity<String> userResponse = restTemplate.exchange(
                baseUrl + "/api/session/user", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertTrue(userResponse.getStatusCode().is2xxSuccessful(),
                "After GitHub login, /api/session/user should return 200 - proves GitHub provider is supported");

        // Step 4: Verify the response contains authenticated user data
        @SuppressWarnings("unchecked")
        Map<String, Object> userData = objectMapper.readValue(userResponse.getBody(), Map.class);
        assertEquals(Boolean.TRUE, userData.get("authenticated"),
                "User should be authenticated after GitHub OAuth2 login");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) userData.get("user");
        assertNotNull(user,
                "User data should be present after GitHub OAuth2 login");
        assertEquals("github", user.get("provider"),
                "Mapped session user should indicate github provider");

        // Ensure OAuth2 provider interactions actually happened end-to-end.
        wireMockServer.verify(postRequestedFor(urlEqualTo("/oauth2/token")));
        wireMockServer.verify(getRequestedFor(urlEqualTo("/user")));
    }

    @Test
    void testGitHubWithoutEmailFallsBackToProviderScopedUsername() throws Exception {
        setupGitHubOAuth2Stubs("""
                {
                  "login": "github-no-email-user",
                  "id": 778899,
                  "name": "No Email User",
                  "given_name": "No",
                  "family_name": "Email",
                  "avatar_url": "https://github.com/no-email-avatar.jpg"
                }
                """);

        String sessionCookie = createGithubOAuth2Session();
        assertNotNull(sessionCookie, "GitHub OAuth2 flow should create a session cookie");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", sessionCookie);
        ResponseEntity<String> userResponse = restTemplate.exchange(
                baseUrl + "/api/session/user", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertTrue(userResponse.getStatusCode().is2xxSuccessful());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = objectMapper.readValue(userResponse.getBody(), Map.class);
        assertEquals(Boolean.TRUE, responseBody.get("authenticated"));
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) responseBody.get("user");
        assertNotNull(user);
        assertEquals("github", user.get("provider"));
        assertNotNull(user.get("email"));
        assertTrue(String.valueOf(user.get("email")).startsWith("github:"),
                "When provider does not return email, mapped username/email should use provider-scoped fallback");
    }

    private void setupGitHubOAuth2Stubs() {
        setupGitHubOAuth2Stubs("""
                {
                  "login": "github-user",
                  "sub": "12345",
                  "id": 12345,
                  "name": "GitHub User",
                  "given_name": "GitHub",
                  "family_name": "User",
                  "email": "github.user@example.com",
                  "picture": "https://github.com/avatar.jpg"
                }
                """);
    }

    private void setupGitHubOAuth2Stubs(String userInfoBody) {
        // Authorization endpoint - redirect back to Spring callback preserving state
        wireMockServer.stubFor(get(urlMatching("/oauth2/authorize.*"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location",
                                "http://localhost:" + port + "/petclinic/login/oauth2/code/github?code=test-code&state={{{request.query.state}}}")
                        .withTransformers("response-template")));

        // Token endpoint - exchange code for access token
        wireMockServer.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "access_token": "github-access-token",
                                  "token_type": "Bearer",
                                  "expires_in": 3600,
                                  "scope": "user:email"
                                }
                                """)));

        // UserInfo endpoint
        wireMockServer.stubFor(get(urlEqualTo("/user"))
                .withHeader("Authorization", equalTo("Bearer github-access-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userInfoBody)));
    }

    private String createGithubOAuth2Session() {
        ResponseEntity<String> step1 = oauthTemplate.getForEntity(
                baseUrl + "/oauth2/authorization/github", String.class);
        String stateCookie = extractCookieFromResponse(step1);
        String redirectToProvider = step1.getHeaders().getFirst(HttpHeaders.LOCATION);
        if (redirectToProvider == null) {
            return null;
        }

        HttpHeaders headers1 = new HttpHeaders();
        if (stateCookie != null) {
            headers1.set("Cookie", stateCookie);
        }
        ResponseEntity<String> step2 = oauthTemplate.exchange(
                URI.create(redirectToProvider), HttpMethod.GET, new HttpEntity<>(headers1), String.class);
        String callbackUrl = step2.getHeaders().getFirst(HttpHeaders.LOCATION);
        if (callbackUrl == null) {
            return null;
        }

        HttpHeaders headers2 = new HttpHeaders();
        if (stateCookie != null) {
            headers2.set("Cookie", stateCookie);
        }
        ResponseEntity<String> step3 = oauthTemplate.exchange(
                URI.create(callbackUrl), HttpMethod.GET, new HttpEntity<>(headers2), String.class);

        String sessionCookie = extractCookieFromResponse(step3);
        if (sessionCookie != null) {
            return stateCookie != null ? stateCookie + "; " + sessionCookie : sessionCookie;
        }
        return stateCookie;
    }

    private String extractCookieFromResponse(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        StringBuilder cookieHeader = new StringBuilder();
        for (String cookie : cookies) {
            String nameValue = cookie.split(";")[0].trim();
            if (cookieHeader.length() > 0) {
                cookieHeader.append("; ");
            }
            cookieHeader.append(nameValue);
        }
        return cookieHeader.length() > 0 ? cookieHeader.toString() : null;
    }
}
