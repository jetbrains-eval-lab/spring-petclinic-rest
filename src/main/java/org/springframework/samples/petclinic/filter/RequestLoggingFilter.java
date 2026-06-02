package org.springframework.samples.petclinic.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * A servlet filter that logs all incoming HTTP requests.
 * Logs the HTTP method and request URL for debugging purposes.
 */
@Component
public class RequestLoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String method = httpRequest.getMethod();
        String requestURI = httpRequest.getRequestURI();
        String queryString = httpRequest.getQueryString();

        // Log the request details
        if (queryString != null) {
            logger.info("Incoming request: {} {}?{}", method, requestURI, queryString);
        } else {
            logger.info("Incoming request: {} {}", method, requestURI);
        }

        // Continue the filter chain
        chain.doFilter(request, response);
    }
}
