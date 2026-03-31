package org.springframework.samples.petclinic.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.repository.OwnerRepository;
import org.springframework.samples.petclinic.repository.PetRepository;
import org.springframework.samples.petclinic.repository.VisitRepository;
import org.springframework.samples.petclinic.rest.dto.OwnerDto;
import org.springframework.samples.petclinic.rest.dto.PetDto;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression tests for missing cross-entity cache eviction in {@code ClinicServiceImpl}.
 *
 * <p>Bug 1: {@code deletePet} only evicts {@code CACHE_PETS}, not {@code CACHE_OWNERS}.
 * Since {@code Owner} eagerly loads its pets, a subsequent {@code GET /api/owners/{id}}
 * returns a stale owner still containing the deleted pet.</p>
 *
 * <p>Bug 2: {@code deleteVisit} and {@code saveVisit} only evict {@code CACHE_VISITS},
 * not {@code CACHE_PETS} or {@code CACHE_OWNERS}. Since {@code Pet} eagerly loads its
 * visits, a subsequent {@code GET /api/pets/{id}} returns a stale pet still containing
 * the deleted visit.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"hsqldb", "spring-data-jpa"})
@TestPropertySource(properties = {
    "petclinic.cache.scheduled.enabled=false",
    "petclinic.security.enable=false"
})
class CacheEvictionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private OwnerRepository ownerRepository;

    @MockitoBean
    private PetRepository petRepository;

    @MockitoBean
    private VisitRepository visitRepository;

    private String cacheUrl;
    private String ownersUrl;
    private String petsUrl;
    private String visitsUrl;

    @BeforeEach
    void setUp() {
        cacheUrl = "http://localhost:" + port + "/petclinic/api/cache";
        ownersUrl = "http://localhost:" + port + "/petclinic/api/owners";
        petsUrl = "http://localhost:" + port + "/petclinic/api/pets";
        visitsUrl = "http://localhost:" + port + "/petclinic/api/visits";
        restTemplate = restTemplate.withBasicAuth("admin", "admin");
        restTemplate.exchange(cacheUrl, HttpMethod.DELETE, null, Void.class);
        reset(ownerRepository, petRepository, visitRepository);
    }

    /**
     * Bug 1: deletePet does not evict CACHE_OWNERS.
     * After deleting a pet, GET /owners/{id} should reflect the deletion,
     * but instead returns the stale cached owner still containing the deleted pet.
     */
    @Test
    void deletePet_shouldEvictOwnerCache() {
        PetType petType = new PetType();
        petType.setId(1);
        petType.setName("Cat");

        Pet pet = new Pet();
        pet.setId(1);
        pet.setName("Whiskers");
        pet.setBirthDate(LocalDate.of(2020, 1, 1));
        pet.setType(petType);

        Owner ownerWithPet = new Owner();
        ownerWithPet.setId(1);
        ownerWithPet.setFirstName("John");
        ownerWithPet.setLastName("Doe");
        ownerWithPet.setAddress("123 Main St");
        ownerWithPet.setCity("Springfield");
        ownerWithPet.setTelephone("1234567890");
        ownerWithPet.addPet(pet);

        when(ownerRepository.findById(1)).thenReturn(ownerWithPet);
        when(petRepository.findById(1)).thenReturn(pet);
        doNothing().when(petRepository).delete(any(Pet.class));

        // First GET /owners/1 — populates CACHE_OWNERS with owner containing 1 pet
        ResponseEntity<OwnerDto> initialResponse = restTemplate.getForEntity(ownersUrl + "/1", OwnerDto.class);
        assertEquals(HttpStatus.OK, initialResponse.getStatusCode());
        assertNotNull(initialResponse.getBody());
        assertEquals(1, initialResponse.getBody().getPets().size(), "Owner should initially have 1 pet");

        // Re-stub to reflect the DB state after the pet is deleted
        Owner ownerWithoutPet = new Owner();
        ownerWithoutPet.setId(1);
        ownerWithoutPet.setFirstName("John");
        ownerWithoutPet.setLastName("Doe");
        ownerWithoutPet.setAddress("123 Main St");
        ownerWithoutPet.setCity("Springfield");
        ownerWithoutPet.setTelephone("1234567890");
        when(ownerRepository.findById(1)).thenReturn(ownerWithoutPet);

        // DELETE /pets/1 — evicts CACHE_PETS but NOT CACHE_OWNERS (the bug)
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(petsUrl + "/1", HttpMethod.DELETE, null, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        // GET /owners/1 — should show 0 pets; instead returns stale cached owner with 1 pet
        ResponseEntity<OwnerDto> afterDeleteResponse = restTemplate.getForEntity(ownersUrl + "/1", OwnerDto.class);
        assertEquals(HttpStatus.OK, afterDeleteResponse.getStatusCode());
        assertNotNull(afterDeleteResponse.getBody());
        assertEquals(0, afterDeleteResponse.getBody().getPets().size(),
            "After deleting a pet, the owner cache must be evicted so GET /owners/{id} reflects 0 pets");
    }

    /**
     * Bug 2: deleteVisit does not evict CACHE_PETS.
     * After deleting a visit, GET /pets/{id} should reflect the deletion,
     * but instead returns the stale cached pet still containing the deleted visit.
     */
    @Test
    void deleteVisit_shouldEvictPetCache() {
        PetType petType = new PetType();
        petType.setId(1);
        petType.setName("Dog");

        Visit visit = new Visit();
        visit.setId(1);
        visit.setDescription("Annual checkup");
        visit.setDate(LocalDate.of(2024, 6, 15));

        Pet petWithVisit = new Pet();
        petWithVisit.setId(1);
        petWithVisit.setName("Rex");
        petWithVisit.setBirthDate(LocalDate.of(2019, 3, 10));
        petWithVisit.setType(petType);
        petWithVisit.addVisit(visit);

        when(petRepository.findById(1)).thenReturn(petWithVisit);
        when(visitRepository.findById(1)).thenReturn(visit);
        doNothing().when(visitRepository).delete(any(Visit.class));

        // First GET /pets/1 — populates CACHE_PETS with pet containing 1 visit
        ResponseEntity<PetDto> initialResponse = restTemplate.getForEntity(petsUrl + "/1", PetDto.class);
        assertEquals(HttpStatus.OK, initialResponse.getStatusCode());
        assertNotNull(initialResponse.getBody());
        assertEquals(1, initialResponse.getBody().getVisits().size(), "Pet should initially have 1 visit");

        // Re-stub to reflect the DB state after the visit is deleted
        Pet petWithoutVisit = new Pet();
        petWithoutVisit.setId(1);
        petWithoutVisit.setName("Rex");
        petWithoutVisit.setBirthDate(LocalDate.of(2019, 3, 10));
        petWithoutVisit.setType(petType);
        when(petRepository.findById(1)).thenReturn(petWithoutVisit);

        // DELETE /visits/1 — evicts CACHE_VISITS but NOT CACHE_PETS (the bug)
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(visitsUrl + "/1", HttpMethod.DELETE, null, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        // GET /pets/1 — should show 0 visits; instead returns stale cached pet with 1 visit
        ResponseEntity<PetDto> afterDeleteResponse = restTemplate.getForEntity(petsUrl + "/1", PetDto.class);
        assertEquals(HttpStatus.OK, afterDeleteResponse.getStatusCode());
        assertNotNull(afterDeleteResponse.getBody());
        assertEquals(0, afterDeleteResponse.getBody().getVisits().size(),
            "After deleting a visit, the pet cache must be evicted so GET /pets/{id} reflects 0 visits");
    }
}
