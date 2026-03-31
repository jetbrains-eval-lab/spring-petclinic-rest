package org.springframework.samples.petclinic.rest.controller;

import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST controller for manual cache management.
 * <p>
 * Provides an endpoint to clear all application caches. Access requires the ADMIN role.
 */
@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("/api")
public class CacheRestController {

    private final CacheManager cacheManager;

    public CacheRestController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Clears all application caches.
     *
     * @return 204 No Content on success
     */
    @PreAuthorize("hasRole(@roles.ADMIN)")
    @DeleteMapping("/cache")
    public ResponseEntity<Void> clearAllCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
