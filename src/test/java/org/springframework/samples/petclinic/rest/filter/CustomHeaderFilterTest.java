package org.springframework.samples.petclinic.rest.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Test class for {@link CustomHeaderFilter}
 */
class CustomHeaderFilterTest {

    private CustomHeaderFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new CustomHeaderFilter();
    }

    @Test
    void shouldAddCustomHeadersToResponse() throws ServletException, IOException {
        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(response).setHeader(eq("X-Custom-Header"), eq("PetClinic-API"));
        verify(response).setHeader(eq("X-Request-ID"), any(String.class));
        verify(filterChain).doFilter(any(), any());
    }
}
