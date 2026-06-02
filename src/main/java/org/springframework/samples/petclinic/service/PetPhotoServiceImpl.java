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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Implementation of the PetPhotoService interface.
 * This service handles storing, retrieving, and deleting pet photos on the file system.
 */
@Service
public class PetPhotoServiceImpl implements PetPhotoService {

    private static final Logger log = LoggerFactory.getLogger(PetPhotoServiceImpl.class);

    @Value("${petclinic.upload.dir:uploads/pets}")
    private String uploadDir;

    private Path rootLocation;

    @PostConstruct
    public void init() throws IOException {
        this.rootLocation = Paths.get(uploadDir);
        Files.createDirectories(rootLocation);
        log.info("Initialized photo storage in: {}", rootLocation.toAbsolutePath());
    }

    @Override
    public String storePhoto(int petId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file");
        }

        Path petDir = rootLocation.resolve(String.valueOf(petId));
        Files.createDirectories(petDir);

        String filename = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = getFileExtension(filename);
        String storedFilename = "photo" + fileExtension;
        Path destinationFile = petDir.resolve(storedFilename);

        try {
            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
            String relativePath = "/" + uploadDir + "/" + petId + "/" + storedFilename;
            log.info("Stored file: {} for pet ID: {}", relativePath, petId);
            return relativePath;
        } catch (IOException e) {
            throw new IOException("Failed to store file " + filename, e);
        }
    }

    @Override
    public Resource loadPhoto(int petId) throws IOException {
        try {
            Path file = getPhotoPath(petId);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new IOException("Could not read file: " + file);
            }
        } catch (MalformedURLException e) {
            throw new IOException("Could not read file", e);
        }
    }

    @Override
    public boolean deletePhoto(int petId) throws IOException {
        Path petDir = rootLocation.resolve(String.valueOf(petId));
        if (!Files.exists(petDir)) {
            return false;
        }

        try {
            Files.list(petDir)
                .filter(path -> path.getFileName().toString().startsWith("photo"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        log.info("Deleted photo: {} for pet ID: {}", path, petId);
                    } catch (IOException e) {
                        log.error("Error deleting photo: {}", path, e);
                    }
                });

            // Delete the directory if it's empty
            if (Files.list(petDir).count() == 0) {
                Files.delete(petDir);
            }

            return true;
        } catch (IOException e) {
            throw new IOException("Failed to delete photo for pet ID: " + petId, e);
        }
    }

    @Override
    public Path getPhotoPath(int petId) {
        Path petDir = rootLocation.resolve(String.valueOf(petId));
        try {
            if (Files.exists(petDir)) {
                return Files.list(petDir)
                    .filter(path -> path.getFileName().toString().startsWith("photo"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("No photo found for pet ID: " + petId));
            } else {
                throw new IOException("No directory found for pet ID: " + petId);
            }
        } catch (IOException e) {
            log.error("Error finding photo for pet ID: {}", petId, e);
            return null;
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex);
        }
        return ".jpg"; // Default extension
    }
}
