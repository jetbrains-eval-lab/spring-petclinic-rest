package org.springframework.samples.petclinic.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.samples.petclinic.service.OAuth2UserMappingService;

import jakarta.servlet.http.HttpSession;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@ConditionalOnProperty(name = "petclinic.security.oauth2.enable", havingValue = "true")
public class OAuth2SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(OAuth2SecurityConfig.class);

    @Autowired
    private OAuth2UserMappingService userMappingService;

    @Value("${petclinic.security.session.creation-policy:IF_REQUIRED}")
    private String sessionCreationPolicy;

    @Bean
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // REST API
            .sessionManagement(session -> session
                .sessionCreationPolicy(parseSessionCreationPolicy(sessionCreationPolicy))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/**").permitAll() // Allow OAuth2 endpoints
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oauth2UserService())
                )
                .successHandler(oauth2AuthenticationSuccessHandler())
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(logoutSuccessHandler())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            );

        return http.build();
    }

    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return request -> {
            OAuth2User oauth2User = delegate.loadUser(request);
            String provider = request.getClientRegistration().getRegistrationId();
            String userNameAttributeName = request.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();
            // Map OAuth2 user to our domain user once during user loading.
            userMappingService.mapAndSaveUser(oauth2User, provider, userNameAttributeName);
            return oauth2User;
        };
    }

    @Bean
    public AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            HttpSession session = request.getSession(true); // Ensure session is created
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            
            // Extract provider name dynamically
            String provider = "google"; // default fallback
            if (authentication instanceof OAuth2AuthenticationToken token) {
                provider = token.getAuthorizedClientRegistrationId();
            }

            // User is already mapped/saved in oauth2UserService; only load and store in session.
            org.springframework.samples.petclinic.model.User domainUser =
                userMappingService.findMappedUser(oauth2User, provider);
            if (domainUser == null) {
                domainUser = userMappingService.mapAndSaveUser(oauth2User, provider);
            }
            
            // Store in HttpSession as per requirements
            session.setAttribute("authenticated_user", domainUser);
            session.setAttribute("session_created_time", System.currentTimeMillis());
            session.setAttribute("last_activity_time", System.currentTimeMillis());

            // Redirect to session user endpoint to show authentication status
            response.sendRedirect("/api/session/user");
        };
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.setStatus(200);
            response.getWriter().write("{\"message\":\"Logout successful\"}");
            response.setContentType("application/json");
        };
    }

    /**
     * Parse session creation policy from string configuration
     */
    private SessionCreationPolicy parseSessionCreationPolicy(String policy) {
        return switch (policy.toUpperCase()) {
            case "ALWAYS" -> SessionCreationPolicy.ALWAYS;
            case "NEVER" -> SessionCreationPolicy.NEVER;
            case "STATELESS" -> SessionCreationPolicy.STATELESS;
            case "IF_REQUIRED" -> SessionCreationPolicy.IF_REQUIRED;
            default -> {
                log.warn("Unknown session creation policy '{}', defaulting to IF_REQUIRED", policy);
                yield SessionCreationPolicy.IF_REQUIRED;
            }
        };
    }
}
