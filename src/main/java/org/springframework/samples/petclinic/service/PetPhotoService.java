/*
 * Copyright 2002-2025 the original author or authors.
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

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Service interface for handling pet photos.
 */
public interface PetPhotoService {

    /**
     * Store a photo for a pet.
     *
     * @param petId The ID of the pet
     * @param file The photo file to store
     * @return The path to the stored photo
     * @throws IOException If an I/O error occurs
     */
    String storePhoto(int petId, MultipartFile file) throws IOException;

    /**
     * Load a pet's photo as a resource.
     *
     * @param petId The ID of the pet
     * @return The photo resource
     * @throws IOException If an I/O error occurs
     */
    Resource loadPhoto(int petId) throws IOException;

    /**
     * Delete a pet's photo.
     *
     * @param petId The ID of the pet
     * @return true if the photo was deleted, false otherwise
     * @throws IOException If an I/O error occurs
     */
    boolean deletePhoto(int petId) throws IOException;

    /**
     * Get the path to a pet's photo.
     *
     * @param petId The ID of the pet
     * @return The path to the photo
     */
    Path getPhotoPath(int petId);
}
