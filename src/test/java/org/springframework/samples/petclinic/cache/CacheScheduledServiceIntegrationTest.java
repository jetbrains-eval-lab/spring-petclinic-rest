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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.repository.VetRepository;
import org.springframework.samples.petclinic.rest.dto.VetDto;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"hsqldb", "spring-data-jpa"})
@TestPropertySource(properties = {
    "petclinic.cache.scheduled.enabled=true",  // Enable scheduled caching for tests
    "petclinic.security.enable=false"
})
@Transactional
class CacheScheduledServiceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private VetRepository vetRepository;
    
    @Autowired
    ScheduledAnnotationBeanPostProcessor beanPostProcessor;

    private String apiBaseUrl;
    private String cacheBaseUrl;

    @BeforeEach
    void setUp() {
        apiBaseUrl = "http://localhost:" + port + "/petclinic/api";
        cacheBaseUrl = "http://localhost:" + port + "/petclinic/api/cache";
        restTemplate = restTemplate.withBasicAuth("admin", "admin");
        restTemplate.exchange(cacheBaseUrl, HttpMethod.DELETE, null, Void.class);
        reset(vetRepository);
    }

    @Test
    void testDataUpdatedWhenTriggeringScheduledAnnotationBeanPostProcessor() throws Exception {
        Vet mockVet = new Vet();
        mockVet.setId(1);
        mockVet.setFirstName("Scheduled");
        mockVet.setLastName("Vet");
        Collection<Vet> initialVets = Arrays.asList(mockVet);

        when(vetRepository.findAll()).thenReturn(initialVets);

        ResponseEntity<VetDto[]> initialResponse = restTemplate.getForEntity(
            apiBaseUrl + "/vets", VetDto[].class);
        assertEquals(HttpStatus.OK, initialResponse.getStatusCode());
        assertEquals("Scheduled", initialResponse.getBody()[0].getFirstName());
        verify(vetRepository, times(1)).findAll();

        ResponseEntity<VetDto[]> cachedResponse = restTemplate.getForEntity(
            apiBaseUrl + "/vets", VetDto[].class);
        assertEquals(HttpStatus.OK, cachedResponse.getStatusCode());
        verify(vetRepository, times(1)).findAll(); // Still cached

        // Update mock data (simulating database change)
        Vet updatedVet = new Vet();
        updatedVet.setId(1);
        updatedVet.setFirstName("PostProcessor");
        updatedVet.setLastName("Vet");
        Collection<Vet> updatedVets = Arrays.asList(updatedVet);
        when(vetRepository.findAll()).thenReturn(updatedVets);

        // invoke the scheduled method
        assertFalse(beanPostProcessor.getScheduledTasks().isEmpty());
        beanPostProcessor.getScheduledTasks().forEach(scheduledTask -> scheduledTask.getTask().getRunnable().run());

        ResponseEntity<VetDto[]> afterScheduledResponse = restTemplate.getForEntity(
            apiBaseUrl + "/vets", VetDto[].class);
        assertEquals(HttpStatus.OK, afterScheduledResponse.getStatusCode());
        assertEquals("PostProcessor", afterScheduledResponse.getBody()[0].getFirstName());

        // Verify database was accessed again after scheduled invalidation
        verify(vetRepository, times(2)).findAll(); // Scheduled invalidation worked
    }
}
