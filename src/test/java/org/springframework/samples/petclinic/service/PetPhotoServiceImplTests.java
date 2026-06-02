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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PetPhotoServiceImplTests {

    private PetPhotoServiceImpl service;

    @TempDir
    private Path uploadDir;

    @BeforeEach
    void setup() throws IOException {
        service = new PetPhotoServiceImpl();
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
        service.init();
    }

    @Test
    void shouldStoreAndLoadPhotoWithOriginalExtension() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "photo",
            "portrait.png",
            "image/png",
            "test image content".getBytes(StandardCharsets.UTF_8)
        );

        String storedPath = service.storePhoto(3, file);

        assertThat(storedPath).endsWith("/3/photo.png");
        assertThat(service.getPhotoPath(3)).isEqualTo(uploadDir.resolve("3/photo.png"));
        assertThat(service.loadPhoto(3).getContentAsByteArray())
            .isEqualTo("test image content".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldStorePhotoWithDefaultExtensionWhenOriginalFilenameHasNoExtension() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "photo",
            "portrait",
            "image/jpeg",
            "image".getBytes(StandardCharsets.UTF_8)
        );

        String storedPath = service.storePhoto(7, file);

        assertThat(storedPath).endsWith("/7/photo.jpg");
        assertThat(Files.exists(uploadDir.resolve("7/photo.jpg"))).isTrue();
    }

    @Test
    void shouldRejectEmptyPhoto() {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "photo",
            "empty.jpg",
            "image/jpeg",
            new byte[0]
        );

        assertThatThrownBy(() -> service.storePhoto(1, emptyFile))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("empty file");
    }

    @Test
    void shouldDeleteStoredPhotoAndPetDirectory() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "photo",
            "portrait.jpg",
            "image/jpeg",
            "image".getBytes(StandardCharsets.UTF_8)
        );
        service.storePhoto(5, file);

        boolean deleted = service.deletePhoto(5);

        assertThat(deleted).isTrue();
        assertThat(Files.exists(uploadDir.resolve("5"))).isFalse();
    }

    @Test
    void shouldReturnFalseWhenDeletingMissingPetDirectory() throws IOException {
        assertThat(service.deletePhoto(99)).isFalse();
    }

    @Test
    void shouldReturnNullWhenPhotoIsMissing() throws IOException {
        Files.createDirectories(uploadDir.resolve("8"));

        assertThat(service.getPhotoPath(8)).isNull();
        assertThat(service.getPhotoPath(99)).isNull();
    }
}
