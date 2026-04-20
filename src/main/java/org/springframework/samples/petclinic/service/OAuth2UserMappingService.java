package org.springframework.samples.petclinic.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.model.Role;
import org.springframework.samples.petclinic.repository.UserRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OAuth2UserMappingService {

    @Autowired
    private UserRepository userRepository;

    @Value("${petclinic.security.oauth2.admin-emails:}")
    private String adminEmailsWithRoles;

    /**
     * Map OAuth2 user to PetClinic User entity and save/update in database
     */
    public User mapAndSaveUser(OAuth2User oauth2User, String provider) {
        return mapAndSaveUser(oauth2User, provider, null);
    }

    /**
     * Map OAuth2 user to PetClinic User entity and save/update in database using provider ID attribute.
     */
    public User mapAndSaveUser(OAuth2User oauth2User, String provider, String userNameAttributeName) {
        OAuth2Profile profile = extractProfile(oauth2User, provider, userNameAttributeName);
        User existingUser = userRepository.findByUsername(profile.username());

        if (existingUser != null) {
            updateUserFromProfile(existingUser, profile);
            userRepository.save(existingUser);
            return existingUser;
        }

        User newUser = createUserFromProfile(profile);
        userRepository.save(newUser);
        return newUser;
    }

    /**
     * Lookup already mapped user without writing to DB.
     */
    public User findMappedUser(OAuth2User oauth2User, String provider) {
        OAuth2Profile profile = extractProfile(oauth2User, provider, null);
        return userRepository.findByUsername(profile.username());
    }

    /**
     * Get user roles by email
     */
    public Set<String> getUserRoles(String email) {
        User user = userRepository.findByUsername(email);
        if (user != null && user.getRoles() != null) {
            return user.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet());
        }
        return new HashSet<>();
    }

    /**
     * Create new user from OAuth2 data
     */
    private User createUserFromProfile(OAuth2Profile profile) {
        User user = new User();
        user.setUsername(profile.username());
        user.setEmail(profile.email());
        user.setPassword(""); // No password for OAuth2 users
        user.setEnabled(true);
        
        user.setFirstName(profile.firstName());
        user.setLastName(profile.lastName());
        user.setOauthId(profile.providerUserId());
        user.setPictureUrl(profile.pictureUrl());
        user.setOauthProvider(profile.provider());
        
        // Assign roles based on email (email may be null -> no admin roles)
        Set<Role> roles = assignRoles(profile.email());
        bindRolesToUser(user, roles);
        user.setRoles(roles);
        
        return user;
    }

    /**
     * Update existing user with OAuth2 data
     */
    private void updateUserFromProfile(User user, OAuth2Profile profile) {
        // Update user information with latest OAuth2 data
        user.setEmail(profile.email());
        user.setEnabled(true);
        user.setFirstName(profile.firstName());
        user.setLastName(profile.lastName());
        user.setOauthId(profile.providerUserId());
        user.setPictureUrl(profile.pictureUrl());
        user.setOauthProvider(profile.provider());
        
        // Update roles in case admin configuration changed
        Set<Role> roles = assignRoles(profile.email());
        bindRolesToUser(user, roles);
        user.setRoles(roles);
    }

    private OAuth2Profile extractProfile(OAuth2User oauth2User, String provider, String userNameAttributeName) {
        String providerUserId = resolveProviderUserId(oauth2User, userNameAttributeName);
        String email = resolveAttribute(oauth2User, "email", "preferred_username");
        String username = (email != null && !email.isBlank()) ? email : (provider + ":" + providerUserId);

        String firstName = resolveAttribute(oauth2User, "given_name", "first_name");
        String lastName = resolveAttribute(oauth2User, "family_name", "last_name");
        String fullName = resolveAttribute(oauth2User, "name");
        if (firstName == null && fullName != null) {
            String[] parts = fullName.trim().split("\\s+", 2);
            firstName = parts[0];
            if (lastName == null && parts.length > 1) {
                lastName = parts[1];
            }
        }

        String pictureUrl = resolveAttribute(oauth2User, "picture", "avatar_url");
        return new OAuth2Profile(username, email, firstName, lastName, providerUserId, pictureUrl, provider);
    }

    private String resolveProviderUserId(OAuth2User oauth2User, String userNameAttributeName) {
        String providerUserId = resolveAttribute(oauth2User, userNameAttributeName);
        if (providerUserId == null) {
            providerUserId = resolveAttribute(oauth2User, "sub", "id", "user_id", "login", "preferred_username");
        }
        if (providerUserId == null) {
            providerUserId = oauth2User.getName();
        }
        if (providerUserId == null || providerUserId.isBlank()) {
            providerUserId = UUID.randomUUID().toString();
        }
        return providerUserId;
    }

    private String resolveAttribute(OAuth2User oauth2User, String... keys) {
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Object value = oauth2User.getAttribute(key);
            if (value != null) {
                String str = String.valueOf(value).trim();
                if (!str.isEmpty()) {
                    return str;
                }
            }
        }
        return null;
    }

    /**
     * Assign roles based on admin emails with role mapping
     * Format: email1:ROLE1,ROLE2;email2:ROLE3;email3:ROLE1
     */
    private Set<Role> assignRoles(String email) {
        Set<Role> roles = new HashSet<>();
        
        if (adminEmailsWithRoles != null && !adminEmailsWithRoles.trim().isEmpty()) {
            Map<String, Set<String>> emailRoleMapping = parseAdminEmailsWithRoles(adminEmailsWithRoles);
            Set<String> userRoles = emailRoleMapping.get(email);
            
            if (userRoles != null) {
                for (String roleName : userRoles) {
                    roles.add(createRole(roleName));
                }
            }
        }
        
        // Regular users (not in admin emails) get NO ROLES
        // This follows the principle of least privilege for security
        
        return roles;
    }
    
    /**
     * Parse admin emails with roles configuration
     * Format: "email1:ROLE1,ROLE2;email2:ROLE3;email3:ROLE1"
     * Returns: Map<email, Set<roles>>
     */
    private Map<String, Set<String>> parseAdminEmailsWithRoles(String config) {
        Map<String, Set<String>> mapping = new HashMap<>();
        
        if (config == null || config.trim().isEmpty()) {
            return mapping;
        }
        
        String[] userMappings = config.split(";");
        for (String userMapping : userMappings) {
            String[] parts = userMapping.split(":");
            if (parts.length == 2) {
                String email = parts[0].trim();
                String rolesStr = parts[1].trim();
                
                Set<String> roles = new HashSet<>();
                String[] roleArray = rolesStr.split(",");
                for (String role : roleArray) {
                    String trimmedRole = role.trim();
                    if (!trimmedRole.isEmpty()) {
                        roles.add(trimmedRole);
                    }
                }
                
                if (!roles.isEmpty()) {
                    mapping.put(email, roles);
                }
            }
        }
        
        return mapping;
    }

    /**
     * Create role entity
     */
    private Role createRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        return role;
    }

    /**
     * Ensure bidirectional relation is set before persistence.
     * Roles table has NOT NULL username FK, so each role must reference the owning user.
     */
    private void bindRolesToUser(User user, Set<Role> roles) {
        if (user == null || roles == null) {
            return;
        }
        for (Role role : roles) {
            role.setUser(user);
        }
    }

    /**
     * Check if user has admin role
     */
    public boolean isAdmin(String email) {
        Set<String> userRoles = getUserRolesFromMapping(email);
        return userRoles.contains("ADMIN");
    }
    
    /**
     * Get user roles from admin emails configuration
     */
    private Set<String> getUserRolesFromMapping(String email) {
        if (adminEmailsWithRoles != null && !adminEmailsWithRoles.trim().isEmpty()) {
            Map<String, Set<String>> roleMapping = parseAdminEmailsWithRoles(adminEmailsWithRoles);
            return roleMapping.getOrDefault(email, new HashSet<>());
        }
        return new HashSet<>();
    }

    /**
     * Get OAuth2 user attributes for session storage
     */
    public Map<String, Object> extractUserAttributes(OAuth2User oauth2User, String provider) {
        Map<String, Object> attributes = new HashMap<>();
        OAuth2Profile profile = extractProfile(oauth2User, provider, null);
        attributes.put("email", profile.email());
        attributes.put("name", oauth2User.getAttribute("name"));
        attributes.put("given_name", profile.firstName());
        attributes.put("family_name", profile.lastName());
        attributes.put("sub", profile.providerUserId());
        attributes.put("picture", profile.pictureUrl());
        attributes.put("provider", provider);
        
        if (profile.email() != null) {
            attributes.put("roles", getUserRoles(profile.email()));
            attributes.put("is_admin", isAdmin(profile.email()));
        }
        
        return attributes;
    }

    private record OAuth2Profile(
        String username,
        String email,
        String firstName,
        String lastName,
        String providerUserId,
        String pictureUrl,
        String provider
    ) {}
}
