package org.springframework.samples.petclinic.rest.filter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Filter to add custom headers to all HTTP responses.
 * This filter adds headers like X-Custom-Header and X-Request-ID for compliance and monitoring purposes.
 */
@Component
@Order(1) // High priority to ensure it runs early in the filter chain
public class CustomHeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Add a static custom header
        httpResponse.setHeader("X-Custom-Header", "PetClinic-API");

        // Add a unique request ID for tracking/monitoring
        httpResponse.setHeader("X-Request-ID", UUID.randomUUID().toString());

        // Continue the filter chain
        chain.doFilter(request, response);
    }
}
