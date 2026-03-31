package org.springframework.samples.petclinic.rest.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.rest.advice.ExceptionControllerAdvice;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class for {@link CacheRestController}.
 */
@SpringBootTest
@ContextConfiguration(classes = ApplicationTestConfig.class)
@WebAppConfiguration
class CacheRestControllerTests {

    @Autowired
    private CacheRestController cacheRestController;

    @Autowired
    private CacheManager cacheManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(cacheRestController)
            .setControllerAdvice(new ExceptionControllerAdvice())
            .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testClearAllCachesReturnsNoContent() throws Exception {
        // Populate a cache entry so we can verify it gets cleared
        Cache vetsCache = cacheManager.getCache("vets");
        assertThat(vetsCache).isNotNull();
        vetsCache.put("testKey", "testValue");
        assertThat(vetsCache.get("testKey")).isNotNull();

        mockMvc.perform(delete("/api/cache")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Cache entry should be evicted after the DELETE
        assertThat(vetsCache.get("testKey")).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testClearAllCachesEvictsAllCacheRegions() throws Exception {
        // Put a value in each cache region
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.put("key", "value");
            }
        });

        mockMvc.perform(delete("/api/cache")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // All caches should be empty after clearing
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                assertThat(cache.get("key")).isNull();
            }
        });
    }
}
