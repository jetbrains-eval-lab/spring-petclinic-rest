package org.springframework.samples.petclinic.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginAttemptUnitTests {

    @AfterEach
    public void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void disabledLockoutIgnoresFailedAttempts() {
        LoginAttemptService service = new LoginAttemptService(properties(false, 1, 5));

        service.registerFailedAttempt("disabled-user");

        assertThat(service.isLocked("disabled-user")).isFalse();
        assertThat(service.getFailedAttempts("disabled-user")).isZero();
        assertThat(service.getRemainingLockTime("disabled-user")).isZero();
    }

    @Test
    public void successfulLoginClearsTrackedAttempts() {
        LoginAttemptService service = new LoginAttemptService(properties(true, 3, 5));
        service.registerFailedAttempt("owner");

        service.registerSuccessfulLogin("owner");

        assertThat(service.getFailedAttempts("owner")).isZero();
        assertThat(service.isLocked("owner")).isFalse();
        assertThat(service.getRemainingLockTime("owner")).isZero();
    }

    @Test
    public void expiredLockIsClearedOnLookup() {
        LoginAttemptService service = new LoginAttemptService(properties(true, 1, 0));

        service.registerFailedAttempt("expired-user");

        assertThat(service.isLocked("expired-user")).isFalse();
        assertThat(service.getFailedAttempts("expired-user")).isZero();
    }

    @Test
    public void lockedAccountReportsRemainingLockTime() {
        LoginAttemptService service = new LoginAttemptService(properties(true, 1, 5));

        service.registerFailedAttempt("locked-user");

        assertThat(service.isLocked("locked-user")).isTrue();
        assertThat(service.getRemainingLockTime("locked-user")).isPositive();
    }

    @Test
    public void lockedForwardedIpReturnsTooManyRequests() throws Exception {
        LoginAttemptService service = new LoginAttemptService(properties(true, 1, 5));
        service.registerFailedAttempt("203.0.113.10");
        LoginAttemptFilter filter = new LoginAttemptFilter(service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/owners");
        request.addHeader("Authorization", basicAuth("unlocked-user", "wrong-password"));
        request.addHeader("X-Forwarded-For", "203.0.113.10, 198.51.100.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("IP address");
    }

    @Test
    public void requestWithoutBasicAuthContinuesFilterChain() throws Exception {
        LoginAttemptFilter filter = new LoginAttemptFilter(new LoginAttemptService(properties(true, 1, 5)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/owners");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(filterChain.getResponse()).isSameAs(response);
    }

    @Test
    public void successListenerClearsForwardedIpAttempt() {
        LoginAttemptService service = new LoginAttemptService(properties(true, 3, 5));
        service.registerFailedAttempt("vet");
        service.registerFailedAttempt("198.51.100.20");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.20, 203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        new AuthenticationSuccessListener(service).onApplicationEvent(successEvent("vet"));

        assertThat(service.getFailedAttempts("vet")).isZero();
        assertThat(service.getFailedAttempts("198.51.100.20")).isZero();
    }

    @Test
    public void successListenerHandlesMissingRequestContext() {
        LoginAttemptService service = new LoginAttemptService(properties(true, 3, 5));
        service.registerFailedAttempt("assistant");

        new AuthenticationSuccessListener(service).onApplicationEvent(successEvent("assistant"));

        assertThat(service.getFailedAttempts("assistant")).isZero();
    }

    private LoginAttemptProperties properties(boolean enabled, int maxAttempts, int lockDurationMinutes) {
        LoginAttemptProperties properties = new LoginAttemptProperties();
        properties.setEnabled(enabled);
        properties.setMaxAttempts(maxAttempts);
        properties.setLockDurationMinutes(lockDurationMinutes);
        return properties;
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private AuthenticationSuccessEvent successEvent(String username) {
        return new AuthenticationSuccessEvent(new TestingAuthenticationToken(username, "password"));
    }
}
