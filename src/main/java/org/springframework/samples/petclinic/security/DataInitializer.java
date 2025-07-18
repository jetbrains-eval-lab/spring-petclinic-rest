package org.springframework.samples.petclinic.security;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Initializes predefined tenants and users on application startup.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final DataSource dataSource;

    private final PasswordEncoder passwordEncoder;

    public DataInitializer(DataSource dataSource, PasswordEncoder passwordEncoder) {
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Create predefined tenants and users
        createTenantUser("tenant-1", "user-1", "password", true);
        createTenantUser("tenant-2", "user-2", "password", true);
    }

    private void createTenantUser(String tenantId, String username, String password, boolean enabled) {
        // Check if user already exists
        if (userExists(username)) {
            updateUserTenant(username, tenantId);
            return;
        }

        // Create user with tenant
        String sql = "INSERT INTO users (username, password, enabled, tenant_id) VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, passwordEncoder.encode(password));
            ps.setBoolean(3, enabled);
            ps.setString(4, tenantId);

            ps.executeUpdate();

            // Add ADMIN role to the user
            addRole(username, "ROLE_ADMIN");

        } catch (SQLException e) {
            throw new RuntimeException("Error creating tenant user", e);
        }
    }

    private boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking if user exists", e);
        }

        return false;
    }

    private void updateUserTenant(String username, String tenantId) {
        String sql = "UPDATE users SET tenant_id = ? WHERE username = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tenantId);
            ps.setString(2, username);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error updating user tenant", e);
        }
    }

    private void addRole(String username, String role) {
        String sql = "INSERT INTO roles (username, role) VALUES (?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, role);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error adding role to user", e);
        }
    }
}
