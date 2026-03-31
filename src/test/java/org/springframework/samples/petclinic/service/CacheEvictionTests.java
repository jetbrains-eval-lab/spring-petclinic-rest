package org.springframework.samples.petclinic.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.samples.petclinic.config.CacheConfig;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.model.Specialty;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.repository.SpecialtyRepository;
import org.springframework.samples.petclinic.repository.VetRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration tests verifying automatic cache eviction on entity updates
 * and scheduled cache invalidation via {@link CacheEvictionScheduler}.
 */
@SpringBootTest
class CacheEvictionTests {

    @Autowired
    private ClinicService clinicService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheEvictionScheduler cacheEvictionScheduler;

    @MockitoBean
    private VetRepository vetRepository;

    @MockitoBean
    private SpecialtyRepository specialtyRepository;

    @MockitoBean
    private org.springframework.samples.petclinic.repository.PetRepository petRepository;

    @MockitoBean
    private org.springframework.samples.petclinic.repository.OwnerRepository ownerRepository;

    @MockitoBean
    private org.springframework.samples.petclinic.repository.VisitRepository visitRepository;

    @MockitoBean
    private org.springframework.samples.petclinic.repository.PetTypeRepository petTypeRepository;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    @Test
    void testVetCacheIsPopulatedOnRead() {
        Vet vet = new Vet();
        vet.setId(1);
        vet.setFirstName("James");
        vet.setLastName("Carter");
        when(vetRepository.findAll()).thenReturn(List.of(vet));

        // First call: populates cache
        clinicService.findAllVets();
        // Second call: served from cache
        clinicService.findAllVets();

        // Repository should only be called once due to caching
        verify(vetRepository, times(1)).findAll();
    }

    @Test
    void testOwnerCacheIsPopulatedOnRead() {
        Owner owner = new Owner();
        owner.setId(1);
        owner.setFirstName("George");
        owner.setLastName("Franklin");

        when(ownerRepository.findById(1)).thenReturn(owner);

        clinicService.findOwnerById(1);
        clinicService.findOwnerById(1);

        verify(ownerRepository, times(1)).findById(1);
    }

    @Test
    void testOwnerListCacheIsPopulatedOnRead() {
        Owner owner = new Owner();
        owner.setId(1);
        owner.setFirstName("George");
        owner.setLastName("Franklin");

        when(ownerRepository.findAll()).thenReturn(List.of(owner));

        clinicService.findAllOwners();
        clinicService.findAllOwners();

        verify(ownerRepository, times(1)).findAll();
    }

    @Test
    void testOwnerSearchCacheIsPopulatedOnRead() {
        Owner owner = new Owner();
        owner.setId(3);
        owner.setFirstName("Eduardo");
        owner.setLastName("Rodriquez");

        when(ownerRepository.findByLastName("Rod")).thenReturn(List.of(owner));

        clinicService.findOwnerByLastName("Rod");
        clinicService.findOwnerByLastName("Rod");

        verify(ownerRepository, times(1)).findByLastName("Rod");
    }

    @Test
    void testPetCacheIsPopulatedOnRead() {
        Owner owner = new Owner();
        owner.setId(6);
        owner.setFirstName("Jean");
        owner.setLastName("Coleman");

        PetType petType = new PetType();
        petType.setId(1);
        petType.setName("cat");

        Pet pet = new Pet();
        pet.setId(7);
        pet.setName("Samantha");
        pet.setOwner(owner);
        pet.setType(petType);

        when(petRepository.findById(7)).thenReturn(pet);

        clinicService.findPetById(7);
        clinicService.findPetById(7);

        verify(petRepository, times(1)).findById(7);
    }

    @Test
    void testVetCacheIsEvictedOnSave() {
        Vet vet = new Vet();
        vet.setId(1);
        vet.setFirstName("James");
        vet.setLastName("Carter");
        when(vetRepository.findAll()).thenReturn(List.of(vet));

        // Populate cache
        clinicService.findAllVets();
        verify(vetRepository, times(1)).findAll();

        // Save triggers eviction
        clinicService.saveVet(vet);

        // Next read should hit the repository again
        clinicService.findAllVets();
        verify(vetRepository, times(2)).findAll();
    }

    @Test
    void testVetCacheIsEvictedOnDelete() {
        Vet vet = new Vet();
        vet.setId(1);
        vet.setFirstName("James");
        vet.setLastName("Carter");
        when(vetRepository.findAll()).thenReturn(List.of(vet));

        // Populate cache
        clinicService.findAllVets();
        verify(vetRepository, times(1)).findAll();

        // Delete triggers eviction
        clinicService.deleteVet(vet);

        // Next read should hit the repository again
        clinicService.findAllVets();
        verify(vetRepository, times(2)).findAll();
    }

    @Test
    void testSpecialtyCacheIsEvictedOnSave() {
        Specialty specialty = new Specialty();
        specialty.setId(1);
        specialty.setName("Dentistry");
        when(specialtyRepository.findAll()).thenReturn(List.of(specialty));

        // Populate cache
        clinicService.findAllSpecialties();
        verify(specialtyRepository, times(1)).findAll();

        // Save triggers eviction
        clinicService.saveSpecialty(specialty);

        // Next read should hit the repository again
        clinicService.findAllSpecialties();
        verify(specialtyRepository, times(2)).findAll();
    }

    @Test
    void testScheduledEvictionClearsAllCaches() {
        // Populate caches manually
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.put("sentinel", "value");
            }
        });

        // Verify caches are populated
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                assertThat(cache.get("sentinel")).isNotNull();
            }
        });

        // Trigger scheduled eviction
        cacheEvictionScheduler.evictAllCaches();

        // All caches should now be empty
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                assertThat(cache.get("sentinel")).isNull();
            }
        });
    }
}
