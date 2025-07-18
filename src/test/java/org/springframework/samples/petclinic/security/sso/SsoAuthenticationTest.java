package org.springframework.samples.petclinic.security.sso;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextConfiguration(classes = ApplicationTestConfig.class)
@Import({SsoAuthenticationTest.MockSsoControllerTestConfig.class, SsoAuthenticationTest.MockSsoController.class})
@TestPropertySource(properties = {
    "petclinic.security.sso.url=http://localhost:8087/petclinic/mock-sso/auth",
    "server.port=8087",
    "petclinic.security.enable=true"
})
public class SsoAuthenticationTest {

    @Autowired
    private AuthenticationConfiguration authConfig;

    private AuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() throws Exception {
        authenticationManager = authConfig.getAuthenticationManager();
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        // Setup MockMvc with Spring Security
        this.mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    public void testSuccessfulSsoAuthentication() {
        // Create authentication request with valid credentials
        UsernamePasswordAuthenticationToken authRequest =
            new UsernamePasswordAuthenticationToken("admin", "validPassword");

        // Authenticate
        Authentication authentication = authenticationManager.authenticate(authRequest);

        // Verify authentication was successful
        assertTrue(authentication.isAuthenticated());
        assertEquals("admin", authentication.getName());

        // Verify roles were mapped correctly
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    public void testRoleMapping() {
        // Create authentication request with user that has multiple roles
        UsernamePasswordAuthenticationToken authRequest =
            new UsernamePasswordAuthenticationToken("multiRoleUser", "validPassword");

        // Authenticate
        Authentication authentication = authenticationManager.authenticate(authRequest);

        // Verify authentication was successful
        assertTrue(authentication.isAuthenticated());

        // Verify all roles were mapped correctly
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER_ADMIN")));
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_VET_ADMIN")));
    }

    @Test
    public void testRoleMappingWithoutRolePrefix() {
        // Create authentication request with user that has roles without ROLE_ prefix
        UsernamePasswordAuthenticationToken authRequest =
            new UsernamePasswordAuthenticationToken("unprefixedRoleUser", "validPassword");

        // Authenticate
        Authentication authentication = authenticationManager.authenticate(authRequest);

        // Verify authentication was successful
        assertTrue(authentication.isAuthenticated());

        // Verify roles were mapped correctly with ROLE_ prefix added
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    public void testFailedSsoAuthentication() {
        // Create authentication request with invalid credentials
        UsernamePasswordAuthenticationToken authRequest =
            new UsernamePasswordAuthenticationToken("admin", "invalidPassword");

        // Attempt to authenticate and expect BadCredentialsException
        assertThrows(BadCredentialsException.class, () -> {
            authenticationManager.authenticate(authRequest);
        });
    }

    @Test
    public void testSsoServiceUnavailable() {
        // Create authentication request with credentials that trigger SSO service unavailable
        UsernamePasswordAuthenticationToken authRequest =
            new UsernamePasswordAuthenticationToken("unavailableUser", "anyPassword");

        // Attempt to authenticate and expect AuthenticationServiceException
        assertThrows(AuthenticationServiceException.class, () -> {
            authenticationManager.authenticate(authRequest);
        });
    }

    @TestConfiguration
    public static class MockSsoControllerTestConfig {

        @Bean
        public SecurityFilterChain mockSsoFilterChain(HttpSecurity http) throws Exception {
            http
                .securityMatcher("/mock-sso/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests((authz) -> authz
                    .requestMatchers(HttpMethod.POST, "/mock-sso/auth").permitAll()
                );

            return http.build();
        }
    }

    // Mock SSO Controller for testing
    @RestController
    public static class MockSsoController {

        @PostMapping("/mock-sso/auth")
        public ResponseEntity<Map<String, Object>> authenticate(@RequestHeader("Authorization") String authHeader) {
            Map<String, Object> response = new HashMap<>();

            if (authHeader != null && authHeader.startsWith("Basic ")) {
                String base64Credentials = authHeader.substring("Basic ".length());
                String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
                String[] values = credentials.split(":", 2);
                String username = values[0];
                String password = values[1];

                if ("unavailableUser".equals(username) || "fallbackUser".equals(username)) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
                }

                if ("admin".equals(username) && "validPassword".equals(password)) {
                    response.put("authenticated", true);
                    response.put("roles", Collections.singletonList("ADMIN"));
                    return ResponseEntity.ok(response);
                } else if ("multiRoleUser".equals(username) && "validPassword".equals(password)) {
                    response.put("authenticated", true);
                    response.put("roles", List.of("OWNER_ADMIN", "VET_ADMIN"));
                    return ResponseEntity.ok(response);
                } else if ("unprefixedRoleUser".equals(username) && "validPassword".equals(password)) {
                    response.put("authenticated", true);
                    response.put("roles", Collections.singletonList("ADMIN"));
                    return ResponseEntity.ok(response);
                }
            }

            response.put("authenticated", false);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }
}
