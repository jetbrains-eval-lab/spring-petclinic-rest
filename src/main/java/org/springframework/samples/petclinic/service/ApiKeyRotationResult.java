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
 * Result object for API key rotation, containing both the new full key (plaintext)
 * and the new ApiKey entity. The full key is only available immediately after rotation.
 *
 * @author Spring PetClinic Team
 */
public class ApiKeyRotationResult {
    private final String newFullKey;
    private final ApiKey newApiKey;

    public ApiKeyRotationResult(String newFullKey, ApiKey newApiKey) {
        this.newFullKey = newFullKey;
        this.newApiKey = newApiKey;
    }

    public String getNewFullKey() {
        return newFullKey;
    }

    public ApiKey getNewApiKey() {
        return newApiKey;
    }
}

