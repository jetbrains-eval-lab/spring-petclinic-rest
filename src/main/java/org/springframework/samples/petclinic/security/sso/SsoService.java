package org.springframework.samples.petclinic.security.sso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SsoService {

    private static final Logger logger = LoggerFactory.getLogger(SsoService.class);

    @Value("${petclinic.security.sso.url}")
    private String ssoApiUrl;

    private final RestTemplate restTemplate;

    public SsoService(RestTemplateBuilder restTemplateBuilder,
                      @Value("${petclinic.security.sso.timeout:5000}") int ssoTimeout) {
        this.restTemplate = restTemplateBuilder
            .connectTimeout(Duration.ofMillis(ssoTimeout))
            .readTimeout(Duration.ofMillis(ssoTimeout))
            .build();
    }

    /**
     * Authenticate a user using the external SSO service
     *
     * @param username The username
     * @param password The password
     * @return SsoAuthenticationResult containing authentication status and roles
     * @throws SsoAuthenticationException if SSO service is unavailable or returns an error
     */
    public SsoAuthenticationResult authenticate(String username, String password) throws SsoAuthenticationException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(username, password);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                ssoApiUrl,
                HttpMethod.POST,
                entity,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                boolean authenticated = (boolean) responseBody.getOrDefault("authenticated", false);

                if (authenticated) {
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) responseBody.getOrDefault("roles", new ArrayList<>());
                    return new SsoAuthenticationResult(true, roles);
                }
            }

            return new SsoAuthenticationResult(false, new ArrayList<>());

        } catch (RestClientException e) {
            if (e instanceof HttpClientErrorException.Unauthorized){
                logger.error("Unauthorized by SSO service", e);
                return new SsoAuthenticationResult(false, new ArrayList<>());
            }
            logger.error("SSO service unavailable or returned an error", e);
            throw new SsoAuthenticationException("SSO service unavailable", e);
        } catch (Exception e) {
            logger.error("Unexpected error during SSO authentication", e);
            throw new SsoAuthenticationException("Error during SSO authentication", e);
        }
    }
}
