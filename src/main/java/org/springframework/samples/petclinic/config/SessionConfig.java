package org.springframework.samples.petclinic.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.config.SessionRepositoryCustomizer;

@Configuration
@EnableJdbcHttpSession
@ConditionalOnProperty(name = "petclinic.security.oauth2.enable", havingValue = "true")
public class SessionConfig {

    @Bean
    SessionRepositoryCustomizer<JdbcIndexedSessionRepository> jdbcSessionTimeoutCustomizer(
            @Value("${spring.session.timeout:30m}") Duration sessionTimeout) {
        return repository -> repository.setDefaultMaxInactiveInterval((int) sessionTimeout.getSeconds());
    }
}
