package org.springframework.samples.petclinic.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Cache configuration.
 * <p>
 * Enables caching and scheduling support. Cache names correspond to the main entity
 * types managed by {@link org.springframework.samples.petclinic.service.ClinicService}.
 */
@Configuration
@EnableCaching
@EnableScheduling
public class CacheConfig {

    public static final String CACHE_VETS = "vets";
    public static final String CACHE_OWNERS = "owners";
    public static final String CACHE_PETS = "pets";
    public static final String CACHE_PET_TYPES = "petTypes";
    public static final String CACHE_SPECIALTIES = "specialties";
    public static final String CACHE_VISITS = "visits";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
            CACHE_VETS,
            CACHE_OWNERS,
            CACHE_PETS,
            CACHE_PET_TYPES,
            CACHE_SPECIALTIES,
            CACHE_VISITS
        );
    }
}
