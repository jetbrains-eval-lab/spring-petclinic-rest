package org.springframework.samples.petclinic.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@ConditionalOnProperty(name = "petclinic.security.oauth2.enable", havingValue = "true")
@Order(1)
public class OAuth2SecurityConfig {

    private final PetClinicOAuth2UserService oAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2Properties oauth2Properties;

    public OAuth2SecurityConfig(PetClinicOAuth2UserService oAuth2UserService,
                                OAuth2AuthenticationSuccessHandler successHandler,
                                OAuth2Properties oauth2Properties) {
        this.oAuth2UserService = oAuth2UserService;
        this.successHandler = successHandler;
        this.oauth2Properties = oauth2Properties;
    }

    @Bean
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        String provider = oauth2Properties.getDefaultLoginProvider();
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oAuth2UserService)
                )
                .successHandler(successHandler)
                .defaultSuccessUrl("/api/auth/login", true)
            )
            .logout(logout -> logout
                .disable()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendRedirect(request.getContextPath() + "/oauth2/authorization/" + provider)
                )
            );
        return http.build();
    }
}
