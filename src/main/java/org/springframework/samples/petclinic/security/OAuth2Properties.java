package org.springframework.samples.petclinic.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "petclinic.security.oauth2")
public class OAuth2Properties {

    private String defaultLoginProvider = "google";
    private String adminEmails = "";

    public String getDefaultLoginProvider() {
        return defaultLoginProvider;
    }

    public void setDefaultLoginProvider(String defaultLoginProvider) {
        this.defaultLoginProvider = defaultLoginProvider;
    }

    public String getAdminEmails() {
        return adminEmails;
    }

    public void setAdminEmails(String adminEmails) {
        this.adminEmails = adminEmails;
    }

    /**
     * Parses the adminEmails property in format "email1:ROLE1,ROLE2;email2:ROLE3"
     * into a map of email -> list of roles.
     */
    public Map<String, List<String>> getAdminEmailRoles() {
        if (adminEmails == null || adminEmails.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new HashMap<>();
        for (String entry : adminEmails.split(";")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx < 0) {
                continue;
            }
            String email = trimmed.substring(0, colonIdx).trim();
            String rolesStr = trimmed.substring(colonIdx + 1).trim();
            List<String> roles = List.of(rolesStr.split(","));
            result.put(email, roles);
        }
        return result;
    }
}
