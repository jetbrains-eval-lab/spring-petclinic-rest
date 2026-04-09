package org.springframework.samples.petclinic.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.samples.petclinic.model.Role;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PetClinicOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final OAuth2UserMapper userMapper;
    private final OAuth2Properties oauth2Properties;

    public PetClinicOAuth2UserService(UserRepository userRepository,
                                      OAuth2UserMapper userMapper,
                                      OAuth2Properties oauth2Properties) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.oauth2Properties = oauth2Properties;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        processOAuth2User(oAuth2User.getAttributes(), provider);
        
        // Load user from database to get official roles
        String username = (String) oAuth2User.getAttributes().get(userRequest.getClientRegistration()
            .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
        
        Optional<User> dbUser = userRepository.findByUsername(username);
        if (dbUser.isPresent()) {
            Set<GrantedAuthority> authorities = dbUser.get().getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toSet());
            
            // Re-wrap the user with merged authorities
            return new DefaultOAuth2User(authorities, oAuth2User.getAttributes(), 
                userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
        }
        
        return oAuth2User;
    }

    public void processOAuth2User(Map<String, Object> attributes, String provider) {
        User user = userMapper.mapToUser(attributes, provider);
        Optional<User> existing = userRepository.findByUsername(user.getUsername());
        if (existing.isPresent()) {
            User existingUser = existing.get();
            existingUser.setEmail(user.getEmail());
            existingUser.setFirstName(user.getFirstName());
            existingUser.setLastName(user.getLastName());
            existingUser.setPictureUrl(user.getPictureUrl());
            existingUser.setOauthId(user.getOauthId());
            existingUser.setOauthProvider(user.getOauthProvider());
            userRepository.save(existingUser);
        } else {
            assignRoles(user);
            userRepository.save(user);
        }
    }

    private void assignRoles(User user) {
        List<String> roles = new ArrayList<>();

        Map<String, List<String>> adminEmailRoles = oauth2Properties.getAdminEmailRoles();
        String email = user.getEmail();
        if (email != null && adminEmailRoles.containsKey(email)) {
            roles.addAll(adminEmailRoles.get(email));
        }

        if (roles.isEmpty()) {
            roles.add("ROLE_USER");
        }

        for (String roleName : roles) {
            String prefixed = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
            Role role = new Role();
            role.setName(prefixed);
            role.setUser(user);
            if (user.getRoles() == null) {
                user.setRoles(new java.util.HashSet<>());
            }
            user.getRoles().add(role);
        }
    }
}
