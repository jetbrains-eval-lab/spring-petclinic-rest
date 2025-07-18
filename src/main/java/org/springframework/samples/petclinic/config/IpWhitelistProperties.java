package org.springframework.samples.petclinic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for IP whitelist settings.
 */
@Component
@ConfigurationProperties(prefix = "security.ip")
public class IpWhitelistProperties {

    /**
     * List of IP addresses allowed to access the application.
     * If empty, all IPs are allowed.
     */
    private List<String> whitelist = new ArrayList<>();

    /**
     * Flag to enable/disable IP whitelist filtering.
     */
    private boolean enabled = false;

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
