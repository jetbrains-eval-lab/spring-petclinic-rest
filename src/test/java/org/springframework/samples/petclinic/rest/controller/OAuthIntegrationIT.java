package org.springframework.samples.petclinic.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class OAuthIntegrationIT {

    protected static WireMockServer wireMockServer;

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    protected RestTemplate oauthTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String baseUrl;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(options()
                .dynamicPort()
                .extensions(new com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer(true)));
        wireMockServer.start();

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

        registry.add("petclinic.security.oauth2.enable", () -> "true");
        registry.add("petclinic.security.enable", () -> "false");

        registry.add("petclinic.security.oauth2.admin-emails",
            () -> "admin@example.com:ADMIN,VET_ADMIN,OWNER_ADMIN;vet-admin@example.com:VET_ADMIN;owner-admin@example.com:OWNER_ADMIN");
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/petclinic";
        wireMockServer.resetAll();

        BasicCookieStore cookieStore = new BasicCookieStore();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .disableRedirectHandling()
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        oauthTemplate = new RestTemplate(factory);
    }

    protected void setupOAuth2StubsForUser(String email, String firstName, String lastName, String sub) {
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

    protected String createOAuth2Session() {
        try {
            ResponseEntity<String> step1 = oauthTemplate.getForEntity(
                    baseUrl + "/oauth2/authorization/google", String.class);

            String stateCookie = extractCookieFromResponse(step1);
            String redirectToProvider = step1.getHeaders().getFirst("Location");

            if (redirectToProvider == null) {
                return null;
            }

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

            HttpHeaders headers2 = new HttpHeaders();
            if (stateCookie != null) {
                headers2.set("Cookie", stateCookie);
            }
            ResponseEntity<String> step3 = oauthTemplate.exchange(
                    URI.create(callbackUrl), HttpMethod.GET, new HttpEntity<>(headers2), String.class);

            String sessionCookie = extractCookieFromResponse(step3);

            String finalCookie;
            if (sessionCookie != null) {
                finalCookie = stateCookie != null ? stateCookie + "; " + sessionCookie : sessionCookie;
            } else {
                finalCookie = stateCookie;
            }

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
        StringBuilder cookieHeader = new StringBuilder();
        for (String cookie : cookies) {
            String nameValue = cookie.split(";")[0].trim();
            if (!cookieHeader.isEmpty()) {
                cookieHeader.append("; ");
            }
            cookieHeader.append(nameValue);
        }
        return !cookieHeader.isEmpty() ? cookieHeader.toString() : null;
    }

}
