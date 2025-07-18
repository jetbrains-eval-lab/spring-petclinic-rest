package org.springframework.samples.petclinic.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.config.IpWhitelistProperties;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter that validates client IP addresses against a configured whitelist.
 * Rejects requests from non-whitelisted IPs with a 403 Forbidden response.
 */
public class IpWhitelistFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(IpWhitelistFilter.class);

    private final IpWhitelistProperties ipWhitelistProperties;

    public IpWhitelistFilter(IpWhitelistProperties ipWhitelistProperties) {
        this.ipWhitelistProperties = ipWhitelistProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip the filter if IP whitelist is not enabled
        if (!ipWhitelistProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get client IP address
        String clientIp = extractClientIpAddress(request);

        // If the whitelist is empty, allow all requests
        List<String> whitelist = ipWhitelistProperties.getWhitelist();
        if (whitelist.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if client IP is in the whitelist
        if (isIpWhitelisted(clientIp, whitelist)) {
            filterChain.doFilter(request, response);
        } else {
            logger.warn("Access denied for IP: {} - not in whitelist", clientIp);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.getWriter().write("Access denied: Your IP address is not allowed to access this resource");
        }
    }

    private String extractClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, the first one is the original client
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isIpWhitelisted(String clientIp, List<String> whitelist) {
        // Check for exact matches
        if (whitelist.contains(clientIp)) {
            return true;
        }

        // Check for CIDR notation or subnet masks (simplified implementation)
        for (String whitelistedIp : whitelist) {
            if (whitelistedIp.contains("*")) {
                // Handle wildcard notation (e.g. 192.168.1.*)
                String ipPattern = whitelistedIp.replace(".", "\\.").replace("*", ".*");
                if (clientIp.matches(ipPattern)) {
                    return true;
                }
            }
        }

        return false;
    }
}
