package org.springframework.samples.petclinic.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link SecurityControllerAdvice}.
 */
@SpringBootTest
@ContextConfiguration(classes = ApplicationTestConfig.class)
@AutoConfigureMockMvc
@Import(SecurityControllerAdviceTest.TestController.class)
public class SecurityControllerAdviceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestService testService;

    /**
     * Test controller to trigger exceptions for testing.
     */
    @RestController
    public static class TestController {

        private final TestService testService;

        public TestController(TestService testService) {
            this.testService = testService;
        }

        @GetMapping("/test/authentication")
        public String testAuthentication() {
            return testService.methodThatThrowsAuthenticationException();
        }

        @GetMapping("/test/bad-credentials")
        public String testBadCredentials() {
            return testService.methodThatThrowsBadCredentialsException();
        }

        @GetMapping("/test/access-denied")
        public String testAccessDenied() {
            return testService.methodThatThrowsAccessDeniedException();
        }

        @GetMapping("/test/illegal-state")
        public String testInsufficientAuthentication() {
            return testService.methodThatThrowsIllegalStateException();
        }
    }

    /**
     * Interface for mocking service that throws security exceptions.
     */
    public interface TestService {
        String methodThatThrowsAuthenticationException();
        String methodThatThrowsBadCredentialsException();
        String methodThatThrowsAccessDeniedException();
        String methodThatThrowsIllegalStateException();
    }

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController(testService))
            .setControllerAdvice(new SecurityControllerAdvice())
            .build();
    }

    @Test
    public void testHandleAuthenticationException() throws Exception {
        // Setup
        AuthenticationException authenticationException = mock(AuthenticationException.class);
        when(authenticationException.getMessage()).thenReturn("Authentication failed");
        when(testService.methodThatThrowsAuthenticationException()).thenThrow(authenticationException);

        // Test
        mockMvc.perform(get("/test/authentication")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code", is(401)))
            .andExpect(jsonPath("$.message", is("Authentication failed")))
            .andExpect(jsonPath("$.server_address", notNullValue()));
    }

    @Test
    public void testHandleBadCredentialsException() throws Exception {
        // Setup
        BadCredentialsException badCredentialsException = new BadCredentialsException("Bad credentials");
        when(testService.methodThatThrowsBadCredentialsException()).thenThrow(badCredentialsException);

        // Test
        mockMvc.perform(get("/test/bad-credentials")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code", is(401)))
            .andExpect(jsonPath("$.message", is("Bad credentials")))
            .andExpect(jsonPath("$.server_address", notNullValue()));
    }

    @Test
    public void testHandleAccessDeniedException() throws Exception {
        // Setup
        AccessDeniedException accessDeniedException = new AccessDeniedException("Access denied");
        when(testService.methodThatThrowsAccessDeniedException()).thenThrow(accessDeniedException);

        // Test
        mockMvc.perform(get("/test/access-denied")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is(403)))
            .andExpect(jsonPath("$.message", is("Access denied")))
            .andExpect(jsonPath("$.server_address", notNullValue()));
    }

    @Test
    public void testHandleIllegalStateException() throws Exception {
        // Setup
        IllegalStateException illegalStateException = new IllegalStateException("Illegal state exception");
        when(testService.methodThatThrowsIllegalStateException()).thenThrow(illegalStateException);

        // Test
        mockMvc.perform(get("/test/illegal-state")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code", is(500)))
            .andExpect(jsonPath("$.message", is("Illegal state exception")))
            .andExpect(jsonPath("$.server_address", notNullValue()));
    }
}
