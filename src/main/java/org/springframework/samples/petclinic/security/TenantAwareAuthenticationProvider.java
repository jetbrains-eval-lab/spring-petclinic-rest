package org.springframework.samples.petclinic.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Custom authentication provider that validates tenant access.
 * Extends the DaoAuthenticationProvider to add tenant validation.
 */
public class TenantAwareAuthenticationProvider extends DaoAuthenticationProvider {

    private final DataSource dataSource;

    public TenantAwareAuthenticationProvider(UserDetailsService userDetailsService, DataSource dataSource) {
        super(userDetailsService);
        this.dataSource = dataSource;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // First, authenticate using the parent provider
        Authentication result = super.authenticate(authentication);

        // Then, validate tenant access
        String username = authentication.getName();
        String currentTenant = TenantContext.getCurrentTenant();

        if (currentTenant == null || currentTenant.isEmpty()) {
            throw new BadCredentialsException("Tenant is not set");
        }

        if (!validateTenantAccess(username, currentTenant)) {
            throw new BadCredentialsException("User does not have access to tenant: " + currentTenant);
        }

        return result;
    }

    private boolean validateTenantAccess(String username, String tenantId) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ? AND tenant_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error validating tenant access", e);
        }

        return false;
    }
}
