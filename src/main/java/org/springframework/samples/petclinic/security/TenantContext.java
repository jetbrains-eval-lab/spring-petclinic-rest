package org.springframework.samples.petclinic.security;

/**
 * Utility class to store and retrieve the current tenant ID.
 * Uses ThreadLocal to ensure thread safety.
 */
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Private constructor to prevent instantiation
    }

    /**
     * Set the current tenant ID.
     *
     * @param tenantId the tenant ID to set
     */
    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Get the current tenant ID.
     *
     * @return the current tenant ID
     */
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Clear the current tenant ID.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
