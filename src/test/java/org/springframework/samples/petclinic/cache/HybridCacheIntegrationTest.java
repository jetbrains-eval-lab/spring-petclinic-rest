/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.repository.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration tests for hybrid caching system focusing on L1/L2 coordination and fallback scenarios.
 * Tests cover all cached endpoints to verify hybrid cache behavior across all entity types.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
    "petclinic.cache.hybrid.enabled=true",
    "petclinic.security.enable=false",
    "spring.datasource.url=jdbc:h2:mem:hybridcachetest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.sql.init.schema-locations=classpath*:db/h2/schema.sql",
    "spring.sql.init.data-locations=classpath*:db/h2/data.sql"
})
public class HybridCacheIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    @Qualifier("l1CacheManager")
    private CacheManager cacheManager;

    @MockitoBean
    private VetRepository vetRepository;

    @MockitoBean
    private OwnerRepository ownerRepository;

    @MockitoBean
    private PetRepository petRepository;

    @MockitoBean
    private PetTypeRepository petTypeRepository;

    @MockitoBean
    private SpecialtyRepository specialtyRepository;

    @MockitoBean
    private VisitRepository visitRepository;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        clearAllCaches();
        reset(vetRepository, ownerRepository, petRepository, petTypeRepository, specialtyRepository, visitRepository);
        setupAllMockData();
    }

    /**
     * Test data provider for parameterized cache tests (collection endpoints)
     */
    static Stream<Arguments> cacheTestData() {
        return Stream.of(
            Arguments.of("vets", "/api/vets", "vets", "\"id\":1", "\"id\":2"),
            Arguments.of("owners", "/api/owners", "owners", "\"id\":1", "\"id\":2"),
            Arguments.of("pets", "/api/pets", "pets", "\"id\":1", "\"id\":2"),
            Arguments.of("petTypes", "/api/pettypes", "petTypes", "\"id\":1", "\"id\":2"),
            Arguments.of("specialties", "/api/specialties", "specialties", "\"id\":1", "\"id\":2"),
            Arguments.of("visits", "/api/visits", "visits", "\"id\":1", "\"id\":2")
        );
    }

    /**
     * Test data provider for parameterized cache tests (single entity endpoints)
     */
    static Stream<Arguments> singleEntityCacheTestData() {
        return Stream.of(
            Arguments.of("vets", "/api/vets/1", "vetById", "\"id\":1", "Test"),
            Arguments.of("owners", "/api/owners/1", "ownerById", "\"id\":1", "John"),
            Arguments.of("pets", "/api/pets/1", "petById", "\"id\":1", "Fluffy"),
            Arguments.of("petTypes", "/api/pettypes/1", "petTypeById", "\"id\":1", "Cat"),
            Arguments.of("specialties", "/api/specialties/1", "specialtyById", "\"id\":1", "Dentistry"),
            Arguments.of("visits", "/api/visits/1", "visitById", "\"id\":1", "Regular checkup")
        );
    }

    @ParameterizedTest(name = "testHybridCacheCoordination_{0}")
    @MethodSource("cacheTestData")
    void testHybridCacheCoordination(String entityName, String endpoint, String cacheName, String expectedId1, String expectedId2) {
        // 1. Populate both cache layers
        ResponseEntity<String> response1 = restTemplate.getForEntity(endpoint, String.class);
        assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();

        // 2. Verify both cache layers are populated
        assertThat(findL1KeysForCache(cacheName)).isNotEmpty();
        assertThat(findL2KeysForCache(cacheName)).isNotEmpty();

        // 3. Verify caching is working (should be fast L1 hit)
        clearAllRepositoryInvocations();
        ResponseEntity<String> response2 = restTemplate.getForEntity(endpoint, String.class);
        assertThat(response2.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response2.getBody()).contains(expectedId1).contains(expectedId2);
        verifyNoRepositoryInteractions();

        // 4. Clear L1 cache
        clearL1Cache();

        // 5. Check L2 is working
        clearAllRepositoryInvocations();
        ResponseEntity<String> response3 = restTemplate.getForEntity(endpoint, String.class);
        assertThat(response3.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response3.getBody()).contains(expectedId1).contains(expectedId2);
        verifyNoRepositoryInteractions();

        // 6. Verify L2 is populated (L1 may or may not be full depending on implementation)
        assertThat(findL2KeysForCache(cacheName)).isNotEmpty();

        // 7. Fourth call - L1 and L2 should be populated
        clearL2Cache();
        ResponseEntity<String> response4 = restTemplate.getForEntity(endpoint, String.class);
        assertThat(response4.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response4.getBody()).contains(expectedId1).contains(expectedId2);

        // 8. Check L1 is working
        clearL2Cache();
        clearAllRepositoryInvocations();
        ResponseEntity<String> response5 = restTemplate.getForEntity(endpoint, String.class);
        assertThat(response5.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response5.getBody()).contains(expectedId1).contains(expectedId2);
        verifyNoRepositoryInteractions();
    }

    @ParameterizedTest(name = "testCompleteCacheFallbackAndRecovery_{0}")
    @MethodSource("cacheTestData")
    void testCompleteCacheFallbackAndRecovery(String entityName, String endpoint, String cacheName, String expectedId1, String expectedId2) {
        // 1. Populate both cache layers
        ResponseEntity<String> originalResponse = restTemplate.getForEntity(endpoint, String.class);
        assertThat(originalResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // 2. Verify both cache layers are populated
        assertThat(findL1KeysForCache(cacheName)).isNotEmpty();
        assertThat(findL2KeysForCache(cacheName)).isNotEmpty();

        // 3. Verify caching is working
        clearAllRepositoryInvocations();
        ResponseEntity<String> cachedResponse = restTemplate.getForEntity(endpoint, String.class);
        assertThat(cachedResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(cachedResponse.getBody()).contains(expectedId1).contains(expectedId2);
        verifyNoRepositoryInteractions();

        // 4. Clear all caches completely to simulate total cache failure
        clearAllCaches();
        assertThat(findL1KeysForCache(cacheName)).isEmpty();
        assertThat(findL2KeysForCache(cacheName)).isEmpty();

        // 5. Call should go to database (both caches empty) and repopulate both layers
        clearAllRepositoryInvocations();
        ResponseEntity<String> fallbackResponse = restTemplate.getForEntity(endpoint, String.class);
        assertThat(fallbackResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(fallbackResponse.getBody()).contains(expectedId1).contains(expectedId2);
        verifyRepositoryInteraction(entityName);

        // 6. Verify both cache layers are populated
        assertThat(findL1KeysForCache(cacheName)).isNotEmpty();
        assertThat(findL2KeysForCache(cacheName)).isNotEmpty();

        // 7. Final verification - should be cached again
        clearAllRepositoryInvocations();
        ResponseEntity<String> finalCachedResponse = restTemplate.getForEntity(endpoint, String.class);
        assertThat(finalCachedResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(finalCachedResponse.getBody()).contains(expectedId1).contains(expectedId2);
        verifyNoRepositoryInteractions();
    }

    @ParameterizedTest(name = "testHybridCacheCoordination_SingleEntity_{0}")
    @MethodSource("singleEntityCacheTestData")
    void testHybridCacheCoordinationSingleEntity(String entityName, String endpoint, String cacheName, String expectedId, String expectedContent) {
        // 1. Seed through REST to populate both cache layers
        ResponseEntity<String> response1 = restTemplate.getForEntity(endpoint, String.class);
        assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();

        // 2. Verify both cache layers are populated
        assertThat(findL1KeysForCache(cacheName)).isNotEmpty();
        assertThat(findL2KeysForCache(cacheName)).isNotEmpty();

        // 3. Verify caching is working
        clearAllRepositoryInvocations();
        ResponseEntity<String> response2 = restTemplate.getForEntity(endpoint, String.class);
        assertThat(response2.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response2.getBody()).contains(expectedId).contains(expectedContent);
        verifyNoRepositoryInteractions();

        // 4. Clear L1 cache
        clearL1Cache();

        // 5. Check L2 is working
        clearAllRepositoryInvocations();
        ResponseEntity<String> response3 = restTemplate.getForEntity(endpoint, String.class);
        assertThat(response3.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response3.getBody()).contains(expectedId).contains(expectedContent);
        verifyNoRepositoryInteractions();

        // 6. Verify L2 is populated (L1 may or may not be full depending on implementation)
        assertThat(findL2KeysForCache(cacheName)).isNotEmpty();

        // 7. Fourth call - L1 and L2 should be populated
        clearL2Cache();
        ResponseEntity<String> response4 = restTemplate.getForEntity(endpoint, String.class);
        assertThat(response4.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response4.getBody()).contains(expectedId).contains(expectedContent);

        // 8. Check L1 is working
        clearL2Cache();
        clearAllRepositoryInvocations();
        ResponseEntity<String> response5 = restTemplate.getForEntity(endpoint, String.class);
        assertThat(response5.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response5.getBody()).contains(expectedId).contains(expectedContent);
        verifyNoRepositoryInteractions();
    }

    @ParameterizedTest(name = "testCompleteCacheFallbackAndRecovery_SingleEntity_{0}")
    @MethodSource("singleEntityCacheTestData")
    void testCompleteCacheFallbackAndRecoverySingleEntity(String entityName, String endpoint, String cacheName, String expectedId, String expectedContent) {
        // 1. Load data into both caches
        ResponseEntity<String> originalResponse = restTemplate.getForEntity(endpoint, String.class);
        assertThat(originalResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // 2. Verify both cache layers are populated
        assertThat(findL1KeysForCache(cacheName)).isNotEmpty();
        assertThat(findL2KeysForCache(cacheName)).isNotEmpty();

        // 3. Verify caching is working
        clearAllRepositoryInvocations();
        ResponseEntity<String> cachedResponse = restTemplate.getForEntity(endpoint, String.class);
        assertThat(cachedResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(cachedResponse.getBody()).contains(expectedId).contains(expectedContent);
        verifyNoRepositoryInteractions();

        // 4. Clear all caches completely to simulate total cache failure
        clearAllCaches();
        assertThat(findL1KeysForCache(cacheName)).isEmpty();
        assertThat(findL2KeysForCache(cacheName)).isEmpty();

        // 5. Call should go to database (both caches empty) and repopulate both layers
        clearAllRepositoryInvocations();
        ResponseEntity<String> fallbackResponse = restTemplate.getForEntity(endpoint, String.class);
        assertThat(fallbackResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(fallbackResponse.getBody()).contains(expectedId).contains(expectedContent);
        verifySingleEntityRepositoryInteraction(entityName);

        // 6. Verify both cache layers are populated
        assertThat(findL1KeysForCache(cacheName)).isNotEmpty();
        assertThat(findL2KeysForCache(cacheName)).isNotEmpty();

        // 7. Final verification - should be cached again
        clearAllRepositoryInvocations();
        ResponseEntity<String> finalCachedResponse = restTemplate.getForEntity(endpoint, String.class);
        assertThat(finalCachedResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(finalCachedResponse.getBody()).contains(expectedId).contains(expectedContent);
        verifyNoRepositoryInteractions();
    }

    @ParameterizedTest(name = "testCacheEvictAnnotation_{0}")
    @MethodSource("cacheEvictionTestData")
    void testCacheEvictAnnotationOnDelete(String entityName, String collectionEndpoint, String byIdEndpoint,
                                   String deleteEndpoint, String collectionCacheName, String byIdCacheName) {
        // Load entities into cache
        ResponseEntity<String> entitiesBeforeDeletion = restTemplate.getForEntity(collectionEndpoint, String.class);
        assertThat(entitiesBeforeDeletion.getStatusCode().is2xxSuccessful()).isTrue();

        // Load individual entity into cache
        ResponseEntity<String> individualEntityResponse = restTemplate.getForEntity(byIdEndpoint, String.class);
        assertThat(individualEntityResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // Verify both caches are populated before deletion
        assertThat(findL1KeysForCache(collectionCacheName)).isNotEmpty();
        assertThat(findL2KeysForCache(collectionCacheName)).isNotEmpty();
        assertThat(findL1KeysForCache(byIdCacheName)).isNotEmpty();
        assertThat(findL2KeysForCache(byIdCacheName)).isNotEmpty();

        // Perform DELETE request
        restTemplate.delete(deleteEndpoint);

        // Verify cache reflects deletion - both collection and by-id caches should be evicted
        assertThat(findL1KeysForCache(collectionCacheName)).isEmpty();
        assertThat(findL2KeysForCache(collectionCacheName)).isEmpty();
        assertThat(findL1KeysForCache(byIdCacheName)).isEmpty();
        assertThat(findL2KeysForCache(byIdCacheName)).isEmpty();
    }

    @ParameterizedTest(name = "testCacheEvictOnUpdate_{0}")
    @MethodSource("cacheEvictionTestData")
    void testCacheEvictOnUpdate(String entityName, String collectionEndpoint, String byIdEndpoint,
                                 String deleteEndpoint, String collectionCacheName, String byIdCacheName) {
        // Load entities into cache
        ResponseEntity<String> collectionResponse = restTemplate.getForEntity(collectionEndpoint, String.class);
        assertThat(collectionResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> entityResponse = restTemplate.getForEntity(byIdEndpoint, String.class);
        assertThat(entityResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // Verify both caches are populated before update
        assertThat(findL1KeysForCache(collectionCacheName)).isNotEmpty();
        assertThat(findL2KeysForCache(collectionCacheName)).isNotEmpty();
        assertThat(findL1KeysForCache(byIdCacheName)).isNotEmpty();
        assertThat(findL2KeysForCache(byIdCacheName)).isNotEmpty();

        // Perform UPDATE (PUT) operation
        String entityJson = entityResponse.getBody();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(entityJson, headers);
        restTemplate.exchange(byIdEndpoint, org.springframework.http.HttpMethod.PUT, request, String.class);

        // Verify collection cache is evicted (except for pets which don't evict on update)
        // byId cache may be repopulated by findById in update
        if (!"pets".equals(entityName)) {
            assertThat(findL1KeysForCache(collectionCacheName)).isEmpty();
            assertThat(findL2KeysForCache(collectionCacheName)).isEmpty();
        }
    }

    @ParameterizedTest(name = "testCacheEvictOnCreate_{0}")
    @MethodSource("cacheEvictionOnCreateTestData")
    void testCacheEvictOnCreate(String entityName, String collectionEndpoint, String createJson,
                                 String collectionCacheName, String byIdCacheName) {
        // Load entities into cache
        ResponseEntity<String> collectionResponse = restTemplate.getForEntity(collectionEndpoint, String.class);
        assertThat(collectionResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // Verify caches are populated before create
        assertThat(findL1KeysForCache(collectionCacheName)).isNotEmpty();
        assertThat(findL2KeysForCache(collectionCacheName)).isNotEmpty();

        // Perform CREATE (POST) operation which should trigger @CacheEvict
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(createJson, headers);
        restTemplate.postForEntity(collectionEndpoint, request, String.class);

        // Verify both L1 and L2 caches are evicted
        assertThat(findL1KeysForCache(collectionCacheName)).isEmpty();
        assertThat(findL2KeysForCache(collectionCacheName)).isEmpty();
        assertThat(findL1KeysForCache(byIdCacheName)).isEmpty();
        assertThat(findL2KeysForCache(byIdCacheName)).isEmpty();
    }

    /**
     * Test data provider for cache eviction tests (DELETE and UPDATE)
     */
    static Stream<Arguments> cacheEvictionTestData() {
        return Stream.of(
            Arguments.of("vets", "/api/vets", "/api/vets/1", "/api/vets/1", "vets", "vetById"),
            Arguments.of("owners", "/api/owners", "/api/owners/1", "/api/owners/1", "owners", "ownerById"),
            Arguments.of("pets", "/api/pets", "/api/pets/1", "/api/pets/1", "pets", "petById"),
            Arguments.of("petTypes", "/api/pettypes", "/api/pettypes/1", "/api/pettypes/1", "petTypes", "petTypeById"),
            Arguments.of("specialties", "/api/specialties", "/api/specialties/1", "/api/specialties/1", "specialties", "specialtyById"),
            Arguments.of("visits", "/api/visits", "/api/visits/1", "/api/visits/1", "visits", "visitById")
        );
    }

    /**
     * Test data provider for cache eviction on create tests
     */
    static Stream<Arguments> cacheEvictionOnCreateTestData() {
        return Stream.of(
            Arguments.of("vets", "/api/vets",
                "{\"firstName\":\"New\",\"lastName\":\"Vet\",\"specialties\":[]}",
                "vets", "vetById"),
            Arguments.of("owners", "/api/owners",
                "{\"firstName\":\"New\",\"lastName\":\"Owner\",\"address\":\"123 St\",\"city\":\"City\",\"telephone\":\"1234567890\",\"pets\":[]}",
                "owners", "ownerById"),
            Arguments.of("petTypes", "/api/pettypes",
                "{\"name\":\"NewType\"}",
                "petTypes", "petTypeById"),
            Arguments.of("specialties", "/api/specialties",
                "{\"name\":\"NewSpecialty\"}",
                "specialties", "specialtyById")
        );
    }

    @org.junit.jupiter.api.Test
    void testOwnerSearchByLastNameCacheDeletion() {
        // Load parameterized cache (search by lastName)
        ResponseEntity<String> searchResponse = restTemplate.getForEntity("/api/owners?lastName=Doe", String.class);
        assertThat(searchResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(searchResponse.getBody()).contains("\"id\":1");

        // Load individual entity into cache
        ResponseEntity<String> ownerResponse = restTemplate.getForEntity("/api/owners/1", String.class);
        assertThat(ownerResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(ownerResponse.getBody()).contains("\"id\":1");

        // Verify both caches are populated before deletion
        assertThat(findL1KeysForCache("ownersByLastName")).isNotEmpty();
        assertThat(findL2KeysForCache("ownersByLastName")).isNotEmpty();
        assertThat(findL1KeysForCache("ownerById")).isNotEmpty();
        assertThat(findL2KeysForCache("ownerById")).isNotEmpty();

        // Perform DELETE request
        restTemplate.delete("/api/owners/1");

        // Verify both parameterized and by-id caches are evicted after deletion
        assertThat(findL1KeysForCache("ownersByLastName")).isEmpty();
        assertThat(findL2KeysForCache("ownersByLastName")).isEmpty();
        assertThat(findL1KeysForCache("ownerById")).isEmpty();
        assertThat(findL2KeysForCache("ownerById")).isEmpty();
    }


    private void clearAllCaches() {
        for (String cacheName : cacheManager.getCacheNames()) {
            clearL1Cache(cacheName);
            clearL2Cache(cacheName);
        }
    }

    private void clearL1Cache(String cacheName) {
        // Clear only L1 cache for specified cache using primary cache manager (L1)
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private void clearL2Cache(String cacheName) {
        // Clear L2 Redis cache for specific cache name
        Set<String> keys = redisTemplate.keys(cacheName + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public void clearL1Cache() {
        // Clear all L1 cache entries
        for (String cacheName : cacheManager.getCacheNames()) {
            clearL1Cache(cacheName);
        }
    }

    public void clearL2Cache() {
        // Clear all L2 Redis cache entries
        for (String cacheName : cacheManager.getCacheNames()) {
            clearL2Cache(cacheName);
        }
    }

    private Set<String> findL1KeysForCache(String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return Collections.emptySet();
        }

        // For Caffeine cache, we need to access the native cache to get keys
        if (cache instanceof org.springframework.cache.caffeine.CaffeineCache) {
            org.springframework.cache.caffeine.CaffeineCache caffeineCache =
                (org.springframework.cache.caffeine.CaffeineCache) cache;
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                caffeineCache.getNativeCache();

            // Convert keys to strings and return as set
            return nativeCache.asMap().keySet().stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
        }

        // Fallback for other cache implementations
        return Collections.emptySet();
    }

    private Set<String> findL2KeysForCache(String cacheName) {
        Set<String> keys = redisTemplate.keys(cacheName + ":*");
        return keys != null ? keys : Collections.emptySet();
    }

    private void clearAllRepositoryInvocations() {
        clearInvocations(vetRepository, ownerRepository, petRepository, petTypeRepository, specialtyRepository, visitRepository);
    }

    private void verifyNoRepositoryInteractions() {
        verifyNoInteractions(vetRepository, ownerRepository, petRepository, petTypeRepository, specialtyRepository, visitRepository);
    }

    private void verifyRepositoryInteraction(String entityName) {
        switch (entityName) {
            case "vets" -> verify(vetRepository).findAll();
            case "owners" -> verify(ownerRepository).findAll();
            case "pets" -> verify(petRepository).findAll();
            case "petTypes" -> verify(petTypeRepository).findAll();
            case "specialties" -> verify(specialtyRepository).findAll();
            case "visits" -> verify(visitRepository).findAll();
        }
    }

    private void verifySingleEntityRepositoryInteraction(String entityName) {
        switch (entityName) {
            case "vets" -> verify(vetRepository).findById(1);
            case "owners" -> verify(ownerRepository).findById(1);
            case "pets" -> verify(petRepository).findById(1);
            case "petTypes" -> verify(petTypeRepository).findById(1);
            case "specialties" -> verify(specialtyRepository).findById(1);
            case "visits" -> verify(visitRepository).findById(1);
        }
    }

    private void setupAllMockData() {
        setupVetMockData();
        setupOwnerMockData();
        setupPetMockData();
        setupPetTypeMockData();
        setupSpecialtyMockData();
        setupVisitMockData();
    }

    private void setupVetMockData() {
        Vet mockVet1 = new Vet();
        mockVet1.setId(1);
        mockVet1.setFirstName("Test");
        mockVet1.setLastName("Vet");
        Vet mockVet2 = new Vet();
        mockVet2.setId(2);
        mockVet2.setFirstName("Another");
        mockVet2.setLastName("Veterinarian");
        Collection<Vet> vets = Arrays.asList(mockVet1, mockVet2);
        when(vetRepository.findAll()).thenReturn(vets);
        when(vetRepository.findById(1)).thenReturn(mockVet1);
        when(vetRepository.findById(2)).thenReturn(mockVet2);
    }

    private void setupOwnerMockData() {
        Owner mockOwner1 = new Owner();
        mockOwner1.setId(1);
        mockOwner1.setFirstName("John");
        mockOwner1.setLastName("Doe");
        mockOwner1.setAddress("123 Main St");
        mockOwner1.setCity("Springfield");
        mockOwner1.setTelephone("1234567890");

        Owner mockOwner2 = new Owner();
        mockOwner2.setId(2);
        mockOwner2.setFirstName("Jane");
        mockOwner2.setLastName("Smith");
        mockOwner2.setAddress("456 Oak Ave");
        mockOwner2.setCity("Springfield");
        mockOwner2.setTelephone("0987654321");

        Collection<Owner> owners = Arrays.asList(mockOwner1, mockOwner2);
        when(ownerRepository.findAll()).thenReturn(owners);
        when(ownerRepository.findById(1)).thenReturn(mockOwner1);
        when(ownerRepository.findById(2)).thenReturn(mockOwner2);
        when(ownerRepository.findByLastName("Doe")).thenReturn(Arrays.asList(mockOwner1));
    }

    private void setupPetMockData() {
        Pet mockPet1 = new Pet();
        mockPet1.setId(1);
        mockPet1.setName("Fluffy");
        mockPet1.setBirthDate(LocalDate.of(2020, 1, 1));

        Pet mockPet2 = new Pet();
        mockPet2.setId(2);
        mockPet2.setName("Buddy");
        mockPet2.setBirthDate(LocalDate.of(2019, 6, 15));

        Collection<Pet> pets = Arrays.asList(mockPet1, mockPet2);
        when(petRepository.findAll()).thenReturn(pets);
        when(petRepository.findById(1)).thenReturn(mockPet1);
        when(petRepository.findById(2)).thenReturn(mockPet2);
    }

    private void setupPetTypeMockData() {
        PetType mockPetType1 = new PetType();
        mockPetType1.setId(1);
        mockPetType1.setName("Cat");

        PetType mockPetType2 = new PetType();
        mockPetType2.setId(2);
        mockPetType2.setName("Dog");

        Collection<PetType> petTypes = Arrays.asList(mockPetType1, mockPetType2);
        when(petTypeRepository.findAll()).thenReturn(petTypes);
        when(petTypeRepository.findById(1)).thenReturn(mockPetType1);
        when(petTypeRepository.findById(2)).thenReturn(mockPetType2);
    }

    private void setupSpecialtyMockData() {
        Specialty mockSpecialty1 = new Specialty();
        mockSpecialty1.setId(1);
        mockSpecialty1.setName("Dentistry");

        Specialty mockSpecialty2 = new Specialty();
        mockSpecialty2.setId(2);
        mockSpecialty2.setName("Surgery");

        Collection<Specialty> specialties = Arrays.asList(mockSpecialty1, mockSpecialty2);
        when(specialtyRepository.findAll()).thenReturn(specialties);
        when(specialtyRepository.findById(1)).thenReturn(mockSpecialty1);
        when(specialtyRepository.findById(2)).thenReturn(mockSpecialty2);
        when(specialtyRepository.findSpecialtiesByNameIn(Set.of("Dentistry"))).thenReturn(Arrays.asList(mockSpecialty1));
    }

    private void setupVisitMockData() {
        Visit mockVisit1 = new Visit();
        mockVisit1.setId(1);
        mockVisit1.setDate(LocalDate.of(2024, 1, 15));
        mockVisit1.setDescription("Regular checkup");

        Visit mockVisit2 = new Visit();
        mockVisit2.setId(2);
        mockVisit2.setDate(LocalDate.of(2024, 2, 20));
        mockVisit2.setDescription("Vaccination");

        Collection<Visit> visits = Arrays.asList(mockVisit1, mockVisit2);
        when(visitRepository.findAll()).thenReturn(visits);
        when(visitRepository.findById(1)).thenReturn(mockVisit1);
        when(visitRepository.findById(2)).thenReturn(mockVisit2);
        when(visitRepository.findByPetId(1)).thenReturn(Arrays.asList(mockVisit1));
    }
}
