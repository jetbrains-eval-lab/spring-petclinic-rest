package org.springframework.samples.petclinic.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.samples.petclinic.model.Role;
import org.springframework.samples.petclinic.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class OAuth2UserMapper {

    /**
     * Maps database roles to Spring Security GrantedAuthorities.
     */
    public Set<GrantedAuthority> mapToAuthorities(Set<Role> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
            .map(Role::getName)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toSet());
    }

    /**
     * Maps OAuth2 attributes to a PetClinic User entity.
     * Applies strict Google mapping for the "google" provider,
     * and provider-agnostic fallback mapping for all others.
     */
    public User mapToUser(Map<String, Object> attributes, String provider) {
        User user = new User();
        user.setEnabled(true);
        user.setOauthProvider(provider);

        if ("google".equalsIgnoreCase(provider)) {
            mapGoogleAttributes(user, attributes);
        } else {
            mapGenericAttributes(user, attributes, provider);
        }

        return user;
    }

    private void mapGoogleAttributes(User user, Map<String, Object> attributes) {
        String oauthId = (String) attributes.get("sub");
        user.setOauthId(oauthId);

        String email = (String) attributes.get("email");
        user.setEmail(email);
        user.setUsername(email);

        user.setFirstName((String) attributes.get("given_name"));
        user.setLastName((String) attributes.get("family_name"));
        user.setPictureUrl((String) attributes.get("picture"));
    }

    private void mapGenericAttributes(User user, Map<String, Object> attributes, String provider) {
        String oauthId = deriveProviderId(attributes);
        user.setOauthId(oauthId);

        String email = (String) attributes.get("email");
        user.setEmail(email);

        String username = (email != null && !email.isBlank())
            ? email
            : provider + ":" + oauthId;
        user.setUsername(username);

        user.setFirstName(deriveFirstName(attributes));
        user.setLastName(deriveLastName(attributes));
        user.setPictureUrl((String) attributes.get("avatar_url"));
    }

    private String deriveProviderId(Map<String, Object> attributes) {
        for (String key : List.of("sub", "id", "login")) {
            Object val = attributes.get(key);
            if (val != null) {
                return val.toString();
            }
        }
        return String.valueOf(attributes.hashCode());
    }

    private String deriveFirstName(Map<String, Object> attributes) {
        for (String key : List.of("given_name", "first_name")) {
            Object val = attributes.get(key);
            if (val instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        Object name = attributes.get("name");
        if (name instanceof String s && s.contains(" ")) {
            return s.substring(0, s.indexOf(' '));
        }
        return name instanceof String s ? s : null;
    }

    private String deriveLastName(Map<String, Object> attributes) {
        for (String key : List.of("family_name", "last_name")) {
            Object val = attributes.get(key);
            if (val instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        Object name = attributes.get("name");
        if (name instanceof String s && s.contains(" ")) {
            return s.substring(s.indexOf(' ') + 1);
        }
        return null;
    }
}
