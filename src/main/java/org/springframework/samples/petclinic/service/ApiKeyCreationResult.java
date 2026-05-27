/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.samples.petclinic.service;

import org.springframework.samples.petclinic.model.ApiKey;

/**
 * Result object for API key creation, containing both the full key (plaintext)
 * and the ApiKey entity. The full key is only available immediately after creation.
 *
 * @author Spring PetClinic Team
 */
public class ApiKeyCreationResult {
    private final String fullKey;
    private final ApiKey apiKey;

    public ApiKeyCreationResult(String fullKey, ApiKey apiKey) {
        this.fullKey = fullKey;
        this.apiKey = apiKey;
    }

    public String getFullKey() {
        return fullKey;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }
}

