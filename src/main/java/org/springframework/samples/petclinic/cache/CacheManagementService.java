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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Pet Clinic cache management service providing programmatic cache operations.
 */
@Service
public class CacheManagementService {

    private static final Logger logger = LoggerFactory.getLogger(CacheManagementService.class);

    private final CacheManager cacheManager;

    @Autowired
    public CacheManagementService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictCache(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            if (key != null) {
                cache.evict(key);
                logger.info("Cache eviction completed - Cache: '{}', Key: '{}'", cacheName, key);
            } else {
                cache.clear();
                logger.info("Cache clear completed - Cache: '{}'", cacheName);
            }
        } else {
            logger.warn("Attempted to evict non-existent cache: '{}'", cacheName);
        }
    }

    public void evictAllCaches() {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                logger.info("Cleared cache: '{}'", cacheName);
            }
        }
        logger.info("All caches evicted. Total caches cleared: {}", cacheNames.size());
    }

    public Collection<String> getCacheNames() {
        return cacheManager.getCacheNames();
    }
}
