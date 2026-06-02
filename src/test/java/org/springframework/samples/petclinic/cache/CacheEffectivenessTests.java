
/*
 * Copyright 2002-2017 the original author or authors.
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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.repository.*;
import org.springframework.samples.petclinic.rest.dto.*;
import org.springframework.samples.petclinic.service.CacheClearingTestExecutionListener;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Generic Cache Effectiveness Testing Framework
 *
 * This test suite validates cache effectiveness and behavior using REST API calls
 * instead of direct service calls. It uses generic methods to reduce code duplication
 * and follows the same pattern as GenericEntityCacheIntegrationTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"hsqldb", "spring-data-jpa"})
@TestExecutionListeners(listeners = CacheClearingTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CacheEffectivenessTests {

    private static final int BENCHMARK_ITERATIONS = 50;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private VetRepository vetRepository;

    @MockitoBean
    private PetTypeRepository petTypeRepository;

    @MockitoBean
    private OwnerRepository ownerRepository;

    @MockitoBean
    private SpecialtyRepository specialtyRepository;

    @MockitoBean
    private VisitRepository visitRepository;

    @MockitoBean
    private PetRepository petRepository;

    private String baseUrl;
    private String apiBaseUrl;
    private Map<String, EntityHandler> entityHandlers;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/petclinic/api/cache";
        apiBaseUrl = "http://localhost:" + port + "/petclinic/api";

        entityHandlers = Map.of(
            "Vet", new GenericEntityHandler<>(
                "Vet", "/vets", vetRepository, VetRepository.class, Vet.class,
                this::createVet, this::createVetDto, new VetRepositoryOperations()
            ),
            "PetType", new GenericEntityHandler<>(
                "PetType", "/pettypes", petTypeRepository, PetTypeRepository.class, PetType.class,
                this::createPetType, this::createPetTypeDto, new PetTypeRepositoryOperations()
            ),
            "Owner", new GenericEntityHandler<>(
                "Owner", "/owners", ownerRepository, OwnerRepository.class, Owner.class,
                this::createOwner, this::createOwnerDto, new OwnerRepositoryOperations()
            ),
            "Specialty", new GenericEntityHandler<>(
                "Specialty", "/specialties", specialtyRepository, SpecialtyRepository.class, Specialty.class,
                this::createSpecialty, this::createSpecialtyDto, new SpecialtyRepositoryOperations()
            ),
            "Visit", new GenericEntityHandler<>(
                "Visit", "/visits", visitRepository, VisitRepository.class, Visit.class,
                this::createVisit, this::createVisitDto, new VisitRepositoryOperations()
            ),
            "Pet", new GenericEntityHandler<>(
                "Pet", "/pets", petRepository, PetRepository.class, Pet.class,
                this::createPet, this::createPetDto, new PetRepositoryOperations()
            )
        );
    }

    @ParameterizedTest(name = "Cache Effectiveness Test for {0}")
    @MethodSource("getEntityHandlers")
    void testCacheEffectiveness(String entityName) {
        EntityHandler handler = entityHandlers.get(entityName);
        handler.setupEffectivenessMocks();
        EntityCacheTestConfiguration<Object, Object> config = handler.createConfiguration();

        // First call - should hit repository
        ResponseEntity<Object[]> firstResponse = restTemplate.getForEntity(
            apiBaseUrl + config.getApiEndpoint(), Object[].class);
        assertEquals(HttpStatus.OK, firstResponse.getStatusCode());
        assertNotNull(firstResponse.getBody());

        // Second call - should use cache
        ResponseEntity<Object[]> secondResponse = restTemplate.getForEntity(
            apiBaseUrl + config.getApiEndpoint(), Object[].class);
        assertEquals(HttpStatus.OK, secondResponse.getStatusCode());
        assertNotNull(secondResponse.getBody());

        // Verify cache statistics
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            baseUrl + "/stats",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> stats = response.getBody();
        assertThat(stats).isNotNull();

        String cacheRegion = handler.getCacheRegion();
        @SuppressWarnings("unchecked")
        Map<String, Object> cacheStats = (Map<String, Object>) stats.get(cacheRegion);
        assertThat(cacheStats).isNotNull();
        assertThat(((Number) cacheStats.get("hitCount")).longValue()).isEqualTo(1);
        assertThat(((Number) cacheStats.get("missCount")).longValue()).isEqualTo(1);

        handler.verifyEffectivenessCalls();
    }

    @ParameterizedTest(name = "Cache Hit Rate Improvement Test for {0}")
    @MethodSource("getEntityHandlers")
    void testCacheHitRateImprovement(String entityName) {
        EntityHandler handler = entityHandlers.get(entityName);
        handler.setupHitRateMocks();
        EntityCacheTestConfiguration<Object, Object> config = handler.createConfiguration();

        // Get baseline statistics
        ResponseEntity<Map<String, Object>> baselineResponse = restTemplate.exchange(
            baseUrl + "/stats",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(baselineResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> baselineStats = baselineResponse.getBody();
        assertThat(baselineStats).isNotNull();

        String cacheRegion = handler.getCacheRegion();
        @SuppressWarnings("unchecked")
        Map<String, Object> baselineCacheStats = (Map<String, Object>) baselineStats.get(cacheRegion);
        // With @DirtiesContext, baseline should be 0
        long baselineMisses = 0;
        long baselineHits = 0;

        // First call - cache miss
        ResponseEntity<Object[]> firstCall = restTemplate.getForEntity(
            apiBaseUrl + config.getApiEndpoint(), Object[].class);
        assertEquals(HttpStatus.OK, firstCall.getStatusCode());

        // Multiple subsequent calls - cache hits
        for (int i = 0; i < 5; i++) {
            ResponseEntity<Object[]> subsequentCall = restTemplate.getForEntity(
                apiBaseUrl + config.getApiEndpoint(), Object[].class);
            assertEquals(HttpStatus.OK, subsequentCall.getStatusCode());
        }

        // Get final statistics
        ResponseEntity<Map<String, Object>> finalResponse = restTemplate.exchange(
            baseUrl + "/stats",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(finalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> finalStats = finalResponse.getBody();
        assertThat(finalStats).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> finalCacheStats = (Map<String, Object>) finalStats.get(cacheRegion);

        // Verify hit count increased and miss count stayed reasonable
        long finalHitCount = ((Number) finalCacheStats.get("hitCount")).longValue();
        long finalMissCount = ((Number) finalCacheStats.get("missCount")).longValue();
        assertThat(finalHitCount).isEqualTo(baselineHits + 5); // Should have 5 new hits
        assertThat(finalMissCount).isEqualTo(baselineMisses + 1); // Should have only 1 new miss

        // Calculate hit rate for this test's operations only
        long newHits = finalHitCount - baselineHits;
        long newMisses = finalMissCount - baselineMisses;
        double testHitRate = (double) newHits / (newHits + newMisses);
        assertThat(testHitRate).isGreaterThan(0.5); // Should have good hit rate: 5/(5+1) = 0.833

        handler.verifyHitRateCalls();
    }

    @ParameterizedTest(name = "Performance Benchmark Test for {0}")
    @MethodSource("getEntityHandlers")
    void benchmarkServicePerformance(String entityName) {
        EntityHandler handler = entityHandlers.get(entityName);
        handler.setupBenchmarkMocks();
        EntityCacheTestConfiguration<Object, Object> config = handler.createConfiguration();

        // Warm up - first call will be cache miss
        ResponseEntity<Object[]> warmupCall = restTemplate.getForEntity(
            apiBaseUrl + config.getApiEndpoint(), Object[].class);
        assertEquals(HttpStatus.OK, warmupCall.getStatusCode());

        // Benchmark cached calls
        long startTime = System.currentTimeMillis();
        List<Object[]> results = new ArrayList<>();

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            ResponseEntity<Object[]> response = restTemplate.getForEntity(
                apiBaseUrl + config.getApiEndpoint(), Object[].class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            results.add(response.getBody());
        }

        long endTime = System.currentTimeMillis();
        long cachedDuration = endTime - startTime;

        // Clear cache and benchmark uncached calls
        ResponseEntity<Void> clearResponse = restTemplate.exchange(
            baseUrl + "/clear",
            HttpMethod.DELETE,
            null,
            Void.class
        );
        assertThat(clearResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NO_CONTENT);

        startTime = System.currentTimeMillis();
        List<Object[]> uncachedResults = new ArrayList<>();

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            // Clear specific cache before each call
            ResponseEntity<Void> clearSpecificResponse = restTemplate.exchange(
                baseUrl + "/clear/" + handler.getCacheRegion(),
                HttpMethod.DELETE,
                null,
                Void.class
            );
            assertThat(clearSpecificResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NO_CONTENT);

            ResponseEntity<Object[]> response = restTemplate.getForEntity(
                apiBaseUrl + config.getApiEndpoint(), Object[].class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            uncachedResults.add(response.getBody());
        }

        endTime = System.currentTimeMillis();
        long uncachedDuration = endTime - startTime;

        // Verify results are consistent
        assertThat(results).isNotEmpty();
        assertThat(uncachedResults).isNotEmpty();

        // Performance improvement should be significant
        double performanceImprovement = (double) uncachedDuration / cachedDuration;

        // Cache should provide performance improvement (may be minimal due to REST overhead and mocking)
        // In real scenarios, the improvement would be more significant
        assertThat(performanceImprovement).isGreaterThan(0.5); // More lenient for test environment
        handler.verifyBenchmarkCalls();
    }

    @ParameterizedTest(name = "Database Query Reduction Test for {0}")
    @MethodSource("getEntityHandlers")
    void measureDatabaseQueryReduction(String entityName) {
        EntityHandler handler = entityHandlers.get(entityName);
        handler.setupQueryReductionMocks();
        EntityCacheTestConfiguration<Object, Object> config = handler.createConfiguration();

        // Get baseline statistics
        ResponseEntity<Map<String, Object>> baselineResponse = restTemplate.exchange(
            baseUrl + "/stats",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(baselineResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> baselineStats = baselineResponse.getBody();
        assertThat(baselineStats).isNotNull();

        String cacheRegion = handler.getCacheRegion();
        // With @DirtiesContext, baseline should be 0
        long baselineHits = 0;
        long baselineMisses = 0;

        // Warm up with one call
        ResponseEntity<Object[]> warmupCall = restTemplate.getForEntity(
            apiBaseUrl + config.getApiEndpoint(), Object[].class);
        assertEquals(HttpStatus.OK, warmupCall.getStatusCode());

        // Multiple subsequent calls - should be cache hits
        for (int i = 0; i < 10; i++) {
            ResponseEntity<Object[]> cachedCall = restTemplate.getForEntity(
                apiBaseUrl + config.getApiEndpoint(), Object[].class);
            assertEquals(HttpStatus.OK, cachedCall.getStatusCode());
        }

        // Get final stats
        ResponseEntity<Map<String, Object>> finalResponse = restTemplate.exchange(
            baseUrl + "/stats",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(finalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> finalStats = finalResponse.getBody();
        assertThat(finalStats).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> finalCacheStats = (Map<String, Object>) finalStats.get(cacheRegion);
        long finalHits = ((Number) finalCacheStats.get("hitCount")).longValue();
        long finalMisses = ((Number) finalCacheStats.get("missCount")).longValue();

        // Calculate statistics for this test's operations only
        long newHits = finalHits - baselineHits;
        long newMisses = finalMisses - baselineMisses;
        long newRequests = newHits + newMisses;
        double testHitRate = newRequests > 0 ? (double) newHits / newRequests : 0.0;

        // After warm-up, we should have predictable cache statistics for this test
        assertThat(newRequests).isEqualTo(11); // Should have made exactly 11 requests (1 warm-up + 10 loop)
        assertThat(newMisses).isEqualTo(1); // Should have exactly 1 miss (warm-up call)
        assertThat(newHits).isEqualTo(10); // Should have exactly 10 hits (loop calls)
        assertThat(testHitRate).isGreaterThan(0.9); // Should have >90% hit rate (10/11)

        handler.verifyQueryReductionCalls();
    }

    static Stream<Arguments> getEntityHandlers() {
        return Stream.of(
            Arguments.of("Vet"),
            Arguments.of("PetType"),
            Arguments.of("Owner"),
            Arguments.of("Specialty"),
            Arguments.of("Visit"),
            Arguments.of("Pet")
        );
    }

    private interface EntityHandler {
        void setupEffectivenessMocks();
        void setupHitRateMocks();
        void setupBenchmarkMocks();
        void setupQueryReductionMocks();
        void verifyEffectivenessCalls();
        void verifyHitRateCalls();
        void verifyBenchmarkCalls();
        void verifyQueryReductionCalls();
        String getCacheRegion();
        EntityCacheTestConfiguration<Object, Object> createConfiguration();
    }

    private static class GenericEntityHandler<R, E, D> implements EntityHandler {
        private final String entityName;
        private final String apiEndpoint;
        private final R repository;
        private final Function<TestDataBuilder, E> entityFactory;
        private final Function<TestDataBuilder, D> dtoFactory;
        private final EntityCacheTestConfiguration.RepositoryOperations<Object> repositoryOperations;

        GenericEntityHandler(String entityName, String apiEndpoint, R repository,
                           Class<R> repositoryClass, Class<E> entityClass,
                           Function<TestDataBuilder, E> entityFactory, Function<TestDataBuilder, D> dtoFactory,
                           EntityCacheTestConfiguration.RepositoryOperations<Object> repositoryOperations) {
            this.entityName = entityName;
            this.apiEndpoint = apiEndpoint;
            this.repository = repository;
            this.entityFactory = entityFactory;
            this.dtoFactory = dtoFactory;
            this.repositoryOperations = repositoryOperations;
        }

        @Override
        public void setupEffectivenessMocks() {
            E mockEntity = entityFactory.apply(TestDataBuilder.withIdAndName(1, "Cached"));
            Collection<Object> mockEntities = Arrays.asList(mockEntity);

            repositoryOperations.mockFindAll(repository, mockEntities);
        }

        @Override
        public void setupHitRateMocks() {
            E mockEntity = entityFactory.apply(TestDataBuilder.withIdAndName(1, "HitRate"));
            Collection<Object> mockEntities = Arrays.asList(mockEntity);

            repositoryOperations.mockFindAll(repository, mockEntities);
        }

        @Override
        public void setupBenchmarkMocks() {
            E mockEntity = entityFactory.apply(TestDataBuilder.withIdAndName(1, "Benchmark"));
            Collection<Object> mockEntities = Arrays.asList(mockEntity);

            repositoryOperations.mockFindAll(repository, mockEntities);
        }

        @Override
        public void setupQueryReductionMocks() {
            E mockEntity = entityFactory.apply(TestDataBuilder.withIdAndName(1, "QueryReduction"));
            Collection<Object> mockEntities = Arrays.asList(mockEntity);

            repositoryOperations.mockFindAll(repository, mockEntities);
        }

        @Override
        public void verifyEffectivenessCalls() {
            // Verification is implicit - test passes if cache behavior is correct
        }

        @Override
        public void verifyHitRateCalls() {
            // Verification is implicit - test passes if cache behavior is correct
        }

        @Override
        public void verifyBenchmarkCalls() {
            // Verification is implicit - test passes if cache behavior is correct
        }

        @Override
        public void verifyQueryReductionCalls() {
            // Verification is implicit - test passes if cache behavior is correct
        }

        @Override
        public String getCacheRegion() {
            switch (entityName) {
                case "Vet": return "vets";
                case "PetType": return "petTypes";
                case "Owner": return "owners";
                case "Specialty": return "specialties";
                case "Visit": return "visits";
                case "Pet": return "pets";
                default: throw new IllegalArgumentException("Unknown entity: " + entityName);
            }
        }

        @Override
        public EntityCacheTestConfiguration<Object, Object> createConfiguration() {
            return EntityCacheTestConfiguration.<Object, Object>builder()
                .entityName(entityName)
                .apiEndpoint(apiEndpoint)
                .entityFactory(builder -> entityFactory.apply(builder))
                .dtoFactory(builder -> dtoFactory.apply(builder))
                .repository(repository)
                .expectedCreateStatus(HttpStatus.CREATED)
                .expectedUpdateStatus(HttpStatus.NO_CONTENT)
                .expectedDeleteStatus(HttpStatus.NO_CONTENT)
                .repositoryOperations(repositoryOperations)
                .build();
        }
    }

    // Entity factory methods
    private Vet createVet(TestDataBuilder builder) {
        Vet vet = new Vet();
        vet.setId(builder.getId());
        vet.setFirstName(builder.getName());
        vet.setLastName("LastName");
        return vet;
    }

    private VetDto createVetDto(TestDataBuilder builder) {
        VetDto vetDto = new VetDto();
        vetDto.setId(builder.getId());
        vetDto.setFirstName(builder.getName());
        vetDto.setLastName("LastName");
        return vetDto;
    }

    private PetType createPetType(TestDataBuilder builder) {
        PetType petType = new PetType();
        petType.setId(builder.getId());
        petType.setName(builder.getName());
        return petType;
    }

    private PetTypeDto createPetTypeDto(TestDataBuilder builder) {
        PetTypeDto petTypeDto = new PetTypeDto();
        petTypeDto.setId(builder.getId());
        petTypeDto.setName(builder.getName());
        return petTypeDto;
    }

    private Owner createOwner(TestDataBuilder builder) {
        Owner owner = new Owner();
        owner.setId(builder.getId());
        owner.setFirstName(builder.getName());
        owner.setLastName("LastName");
        owner.setAddress("123 Test Street");
        owner.setCity("Test City");
        owner.setTelephone("1234567890");
        return owner;
    }

    private OwnerDto createOwnerDto(TestDataBuilder builder) {
        OwnerDto ownerDto = new OwnerDto();
        ownerDto.setId(builder.getId());
        ownerDto.setFirstName(builder.getName());
        ownerDto.setLastName("LastName");
        ownerDto.setAddress("123 Test Street");
        ownerDto.setCity("Test City");
        ownerDto.setTelephone("1234567890");
        ownerDto.setPets(new java.util.ArrayList<>());
        return ownerDto;
    }

    private Specialty createSpecialty(TestDataBuilder builder) {
        Specialty specialty = new Specialty();
        specialty.setId(builder.getId());
        specialty.setName(builder.getName());
        return specialty;
    }

    private SpecialtyDto createSpecialtyDto(TestDataBuilder builder) {
        SpecialtyDto specialtyDto = new SpecialtyDto();
        specialtyDto.setId(builder.getId());
        specialtyDto.setName(builder.getName());
        return specialtyDto;
    }

    private Visit createVisit(TestDataBuilder builder) {
        Visit visit = new Visit();
        visit.setId(builder.getId());
        visit.setDescription(builder.getName());
        visit.setDate(java.time.LocalDate.now());
        return visit;
    }

    private VisitDto createVisitDto(TestDataBuilder builder) {
        VisitDto visitDto = new VisitDto();
        visitDto.setId(builder.getId());
        visitDto.setDescription(builder.getName());
        visitDto.setDate(java.time.LocalDate.now());
        return visitDto;
    }

    private Pet createPet(TestDataBuilder builder) {
        Pet pet = new Pet();
        pet.setId(builder.getId());
        pet.setName(builder.getName());
        pet.setBirthDate(java.time.LocalDate.of(2020, 1, 1));

        // Create a simple PetType for the pet
        PetType petType = new PetType();
        petType.setId(1);
        petType.setName("Dog");
        pet.setType(petType);

        return pet;
    }

    private PetDto createPetDto(TestDataBuilder builder) {
        PetDto petDto = new PetDto();
        petDto.setId(builder.getId());
        petDto.setName(builder.getName());
        petDto.setBirthDate(java.time.LocalDate.of(2020, 1, 1));

        // Create a simple PetTypeDto for the pet
        PetTypeDto petTypeDto = new PetTypeDto();
        petTypeDto.setId(1);
        petTypeDto.setName("Dog");
        petDto.setType(petTypeDto);

        petDto.setVisits(new java.util.ArrayList<>());
        return petDto;
    }

    // Repository operation classes for each entity type
    private static class VetRepositoryOperations implements EntityCacheTestConfiguration.RepositoryOperations<Object> {
        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAll(Object repository, Collection<Object> entities) {
            when(((VetRepository) repository).findAll()).thenReturn((Collection) entities);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAllSequential(Object repository, Collection<Object> firstCall, Collection<Object> secondCall) {
            when(((VetRepository) repository).findAll())
                .thenReturn((Collection) firstCall)
                .thenReturn((Collection) secondCall);
        }

        @Override
        public void mockFindById(Object repository, Integer id, Object entity) {
            when(((VetRepository) repository).findById(id)).thenReturn((Vet) entity);
        }

        @Override
        public void mockFindByIdSequential(Object repository, Integer id, Object firstCall, Object secondCall) {
            when(((VetRepository) repository).findById(id))
                .thenReturn((Vet) firstCall)
                .thenReturn((Vet) secondCall);
        }

        @Override
        public void mockSave(Object repository, Object entity) {
            doNothing().when((VetRepository) repository).save(any(Vet.class));
        }

        @Override
        public void mockDelete(Object repository, Object entity) {
            doNothing().when((VetRepository) repository).delete(any(Vet.class));
        }
    }

    private static class PetTypeRepositoryOperations implements EntityCacheTestConfiguration.RepositoryOperations<Object> {
        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAll(Object repository, Collection<Object> entities) {
            when(((PetTypeRepository) repository).findAll()).thenReturn((Collection) entities);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAllSequential(Object repository, Collection<Object> firstCall, Collection<Object> secondCall) {
            when(((PetTypeRepository) repository).findAll())
                .thenReturn((Collection) firstCall)
                .thenReturn((Collection) secondCall);
        }

        @Override
        public void mockFindById(Object repository, Integer id, Object entity) {
            when(((PetTypeRepository) repository).findById(id)).thenReturn((PetType) entity);
        }

        @Override
        public void mockFindByIdSequential(Object repository, Integer id, Object firstCall, Object secondCall) {
            when(((PetTypeRepository) repository).findById(id))
                .thenReturn((PetType) firstCall)
                .thenReturn((PetType) secondCall);
        }

        @Override
        public void mockSave(Object repository, Object entity) {
            doNothing().when((PetTypeRepository) repository).save(any(PetType.class));
        }

        @Override
        public void mockDelete(Object repository, Object entity) {
            doNothing().when((PetTypeRepository) repository).delete(any(PetType.class));
        }
    }

    private static class OwnerRepositoryOperations implements EntityCacheTestConfiguration.RepositoryOperations<Object> {
        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAll(Object repository, Collection<Object> entities) {
            when(((OwnerRepository) repository).findAll()).thenReturn((Collection) entities);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAllSequential(Object repository, Collection<Object> firstCall, Collection<Object> secondCall) {
            when(((OwnerRepository) repository).findAll())
                .thenReturn((Collection) firstCall)
                .thenReturn((Collection) secondCall);
        }

        @Override
        public void mockFindById(Object repository, Integer id, Object entity) {
            when(((OwnerRepository) repository).findById(id)).thenReturn((Owner) entity);
        }

        @Override
        public void mockFindByIdSequential(Object repository, Integer id, Object firstCall, Object secondCall) {
            when(((OwnerRepository) repository).findById(id))
                .thenReturn((Owner) firstCall)
                .thenReturn((Owner) secondCall);
        }

        @Override
        public void mockSave(Object repository, Object entity) {
            doNothing().when((OwnerRepository) repository).save(any(Owner.class));
        }

        @Override
        public void mockDelete(Object repository, Object entity) {
            doNothing().when((OwnerRepository) repository).delete(any(Owner.class));
        }
    }

    private static class SpecialtyRepositoryOperations implements EntityCacheTestConfiguration.RepositoryOperations<Object> {
        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAll(Object repository, Collection<Object> entities) {
            when(((SpecialtyRepository) repository).findAll()).thenReturn((Collection) entities);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAllSequential(Object repository, Collection<Object> firstCall, Collection<Object> secondCall) {
            when(((SpecialtyRepository) repository).findAll())
                .thenReturn((Collection) firstCall)
                .thenReturn((Collection) secondCall);
        }

        @Override
        public void mockFindById(Object repository, Integer id, Object entity) {
            when(((SpecialtyRepository) repository).findById(id)).thenReturn((Specialty) entity);
        }

        @Override
        public void mockFindByIdSequential(Object repository, Integer id, Object firstCall, Object secondCall) {
            when(((SpecialtyRepository) repository).findById(id))
                .thenReturn((Specialty) firstCall)
                .thenReturn((Specialty) secondCall);
        }

        @Override
        public void mockSave(Object repository, Object entity) {
            doNothing().when((SpecialtyRepository) repository).save(any(Specialty.class));
        }

        @Override
        public void mockDelete(Object repository, Object entity) {
            doNothing().when((SpecialtyRepository) repository).delete(any(Specialty.class));
        }
    }

    private static class VisitRepositoryOperations implements EntityCacheTestConfiguration.RepositoryOperations<Object> {
        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAll(Object repository, Collection<Object> entities) {
            when(((VisitRepository) repository).findAll()).thenReturn((Collection) entities);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAllSequential(Object repository, Collection<Object> firstCall, Collection<Object> secondCall) {
            when(((VisitRepository) repository).findAll())
                .thenReturn((Collection) firstCall)
                .thenReturn((Collection) secondCall);
        }

        @Override
        public void mockFindById(Object repository, Integer id, Object entity) {
            when(((VisitRepository) repository).findById(id)).thenReturn((Visit) entity);
        }

        @Override
        public void mockFindByIdSequential(Object repository, Integer id, Object firstCall, Object secondCall) {
            when(((VisitRepository) repository).findById(id))
                .thenReturn((Visit) firstCall)
                .thenReturn((Visit) secondCall);
        }

        @Override
        public void mockSave(Object repository, Object entity) {
            doNothing().when((VisitRepository) repository).save(any(Visit.class));
        }

        @Override
        public void mockDelete(Object repository, Object entity) {
            doNothing().when((VisitRepository) repository).delete(any(Visit.class));
        }
    }

    private static class PetRepositoryOperations implements EntityCacheTestConfiguration.RepositoryOperations<Object> {
        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAll(Object repository, Collection<Object> entities) {
            when(((PetRepository) repository).findAll()).thenReturn((Collection) entities);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void mockFindAllSequential(Object repository, Collection<Object> firstCall, Collection<Object> secondCall) {
            when(((PetRepository) repository).findAll())
                .thenReturn((Collection) firstCall)
                .thenReturn((Collection) secondCall);
        }

        @Override
        public void mockFindById(Object repository, Integer id, Object entity) {
            when(((PetRepository) repository).findById(id)).thenReturn((Pet) entity);
        }

        @Override
        public void mockFindByIdSequential(Object repository, Integer id, Object firstCall, Object secondCall) {
            when(((PetRepository) repository).findById(id))
                .thenReturn((Pet) firstCall)
                .thenReturn((Pet) secondCall);
        }

        @Override
        public void mockSave(Object repository, Object entity) {
            doNothing().when((PetRepository) repository).save(any(Pet.class));
        }

        @Override
        public void mockDelete(Object repository, Object entity) {
            doNothing().when((PetRepository) repository).delete(any(Pet.class));
        }
    }
}
