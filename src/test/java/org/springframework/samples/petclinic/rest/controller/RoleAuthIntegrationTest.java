package org.springframework.samples.petclinic.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

public class RoleAuthIntegrationTest extends OAuthIntegrationIT {

    @Test
    void testGetVets() {
        HttpHeaders headers = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/vets", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "OAuth2 user with VET_ADMIN role should access /api/vets");
    }

    @Test
    void testGetOwners() {
        HttpHeaders headers = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/owners", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "OAuth2 user with OWNER_ADMIN role should access /api/owners");
    }

    @Test
    void testGetPet() {
        HttpHeaders headers = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/pets/3", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "OAuth2 user with OWNER_ADMIN role should access /api/pets/{id}");
    }

    @Test
    void testGetPetType() {
        HttpHeaders headers = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/pettypes/1", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "OAuth2 user with VET_ADMIN role should access /api/pettypes/{id}");
    }

    @Test
    void testGetSpecialty() {
        HttpHeaders headers = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/specialties/1", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "OAuth2 user with VET_ADMIN role should access /api/specialties/{id}");
    }

    @Test
    void testGetVisit() {
        HttpHeaders headers = loginAsAdmin();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/visits/1", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "OAuth2 user with OWNER_ADMIN role should access /api/visits/{id}");
    }

    @Test
    void testGetVetsForbiddenForRegularUser() {
        HttpHeaders headers = loginAsRegularUser();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/vets", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "OAuth2 user without VET_ADMIN role should get 403 on /api/vets");
    }

    @Test
    void testGetOwnersForbiddenForRegularUser() {
        HttpHeaders headers = loginAsRegularUser();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/owners", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "OAuth2 user without OWNER_ADMIN role should get 403 on /api/owners");
    }

    @Test
    void testGetPetForbiddenForRegularUser() {
        HttpHeaders headers = loginAsRegularUser();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/pets/3", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "OAuth2 user without OWNER_ADMIN role should get 403 on /api/pets/{id}");
    }

    @Test
    void testGetPetTypeForbiddenForRegularUser() {
        HttpHeaders headers = loginAsRegularUser();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/pettypes/1", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "OAuth2 user without VET_ADMIN role should get 403 on /api/pettypes/{id}");
    }

    @Test
    void testGetSpecialtyForbiddenForRegularUser() {
        HttpHeaders headers = loginAsRegularUser();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/specialties/1", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "OAuth2 user without VET_ADMIN role should get 403 on /api/specialties/{id}");
    }

    @Test
    void testGetVisitForbiddenForRegularUser() {
        HttpHeaders headers = loginAsRegularUser();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/visits/1", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "OAuth2 user without OWNER_ADMIN role should get 403 on /api/visits/{id}");
    }

    private HttpHeaders loginAsAdmin() {
        setupOAuth2StubsForUser("admin@example.com", "Admin", "User", "google-admin");

        String sessionCookie = createOAuth2Session();
        assertNotNull(sessionCookie, "OAuth2 login should create a session cookie");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", sessionCookie);
        headers.set("Accept", "application/json");
        return headers;
    }

    private HttpHeaders loginAsRegularUser() {
        setupOAuth2StubsForUser("regular@example.com", "Regular", "User", "google-regular");

        String sessionCookie = createOAuth2Session();
        assertNotNull(sessionCookie, "OAuth2 login should create a session cookie");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", sessionCookie);
        headers.set("Accept", "application/json");
        return headers;
    }
}
