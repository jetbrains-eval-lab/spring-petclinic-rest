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
package org.springframework.samples.petclinic.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.cache.CacheManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("/api/cache")
@Tag(name = "Cache Management", description = "Cache management operations")
public class CacheManagementRestController {

    private final CacheManagementService cacheManagementService;

    @Autowired
    public CacheManagementRestController(CacheManagementService cacheManagementService) {
        this.cacheManagementService = cacheManagementService;
    }

    @Operation(summary = "Clear all caches", description = "Clears all cache entries across all configured caches")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully cleared all caches"),
        @ApiResponse(responseCode = "500", description = "Error clearing caches")
    })
    @PreAuthorize("hasRole(@roles.ADMIN)")
    @DeleteMapping
    public ResponseEntity<Void> clearAllCaches() {
        try {
            cacheManagementService.evictAllCaches();
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
