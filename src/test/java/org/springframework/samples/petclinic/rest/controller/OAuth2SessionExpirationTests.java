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
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Clean OAuth2 Session Expiration Test using Awaitility.
 * 
 * Tests session expiration behavior with 1-second timeout.
 * Uses only black-box testing through API endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OAuth2SessionExpirationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;
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

        // Session Configuration with SHORT timeout for testing
        registry.add("spring.session.jdbc.initialize-schema", () -> "always");
        registry.add("spring.session.timeout", () -> "1s"); // 1 second for fast testing
        registry.add("server.servlet.session.timeout", () -> "1s");

        // OAuth2 Security Configuration
        registry.add("petclinic.security.oauth2.enable", () -> "true");
        registry.add("petclinic.security.enable", () -> "false");
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
    void testSessionExpirationBehavior() throws Exception {
        setupOAuth2Stubs();

        String sessionCookie = createOAuth2Session();
        assertNotNull(sessionCookie, "OAuth2 login should create a session cookie");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", sessionCookie);

        // Verify session is initially active
        ResponseEntity<String> activeResponse = restTemplate.exchange(
                baseUrl + "/api/session/user", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertTrue(activeResponse.getStatusCode().is2xxSuccessful(),
                "Session should be active immediately after login");
        @SuppressWarnings("unchecked")
        Map<String, Object> activeBody = objectMapper.readValue(activeResponse.getBody(), Map.class);
        assertEquals(Boolean.TRUE, activeBody.get("authenticated"));

        // Wait without touching the endpoint; first poll happens only after timeout window.
        await()
            .pollDelay(Duration.ofSeconds(2))
            .atMost(Duration.ofSeconds(4))
            .untilAsserted(() -> {
                ResponseEntity<String> response = restTemplate.exchange(
                        baseUrl + "/api/session/user",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );
                assertEquals(HttpStatus.FOUND, response.getStatusCode());
            });

        // Final verification: after expiration, endpoint requires re-authentication (redirect)
        ResponseEntity<String> expiredResponse = restTemplate.exchange(
                baseUrl + "/api/session/user", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.FOUND, expiredResponse.getStatusCode());
        assertNotNull(expiredResponse.getHeaders().getFirst(HttpHeaders.LOCATION),
                "Expired session should trigger redirect for re-authentication");
    }

    private void setupOAuth2Stubs() {
        wireMockServer.stubFor(get(urlMatching("/oauth2/authorize.*"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location",
                                "http://localhost:" + port + "/petclinic/login/oauth2/code/google?code=test-code&state={{{request.query.state}}}")
                        .withTransformers("response-template")));

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

        wireMockServer.stubFor(get(urlEqualTo("/oauth2/userinfo"))
                .withHeader("Authorization", equalTo("Bearer mock-access-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "sub": "google-expiration-test",
                                  "email": "expiration@example.com",
                                  "given_name": "Expiration",
                                  "family_name": "Test",
                                  "picture": "https://example.com/avatar.jpg"
                                }
                                """)));
    }

    private String createOAuth2Session() {
        ResponseEntity<String> step1 = oauthTemplate.getForEntity(
                baseUrl + "/oauth2/authorization/google", String.class);
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
