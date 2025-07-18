package org.springframework.samples.petclinic.security.sso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SsoAuthenticationProvider implements AuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(SsoAuthenticationProvider.class);

    private final SsoService ssoService;

    public SsoAuthenticationProvider(SsoService ssoService) {
        this.ssoService = ssoService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        try {
            // Try to authenticate with SSO
            logger.debug("Attempting SSO authentication for user: {}", username);
            SsoAuthenticationResult ssoResult = ssoService.authenticate(username, password);

            if (ssoResult.authenticated()) {
                List<GrantedAuthority> authorities = mapExternalRolesToAuthorities(ssoResult.roles());
                return new UsernamePasswordAuthenticationToken(
                    username, password, authorities);
            } else {
                throw new BadCredentialsException("SSO authentication failed");
            }
        } catch (BadCredentialsException e) {
            throw e;
        }
        catch (SsoAuthenticationException e) {
            // SSO service unavailable or other SSO-specific error
            logger.warn("SSO authentication failed, falling back to local authentication: {}", e.getMessage());
            throw new AuthenticationServiceException("SSO authentication failed", e);
        } catch (Exception e) {
            logger.error("Unexpected error during SSO authentication", e);
            throw new AuthenticationServiceException("Authentication failed due to an internal error", e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

    private List<GrantedAuthority> mapExternalRolesToAuthorities(List<String> externalRoles) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        for (String role : externalRoles) {
            // Map external roles to Spring Security roles
            // Example: If external role is "ADMIN", map to "ROLE_ADMIN"
            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role.toUpperCase();
            }
            authorities.add(new SimpleGrantedAuthority(role));
        }

        return authorities;
    }
}
