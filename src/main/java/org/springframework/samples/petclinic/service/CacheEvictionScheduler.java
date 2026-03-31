package org.springframework.samples.petclinic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that periodically evicts all application caches.
 * <p>
 * The eviction interval is configurable via the
 * {@code petclinic.cache.evict.interval} property (milliseconds).
 * Defaults to one hour (3 600 000 ms).
 */
@Component
public class CacheEvictionScheduler {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictionScheduler.class);

    private final CacheManager cacheManager;

    public CacheEvictionScheduler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Scheduled(fixedRateString = "${petclinic.cache.evict.interval:3600000}")
    public void evictAllCaches() {
        log.info("Scheduled cache eviction: clearing all caches");
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}
