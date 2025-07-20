/*
 * Copyright 2016-2017 the original author or authors.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.rest.advice.ExceptionControllerAdvice;
import org.springframework.samples.petclinic.rest.dto.UploadPetPhoto200ResponseDto;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.samples.petclinic.service.PetPhotoService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for {@link PetRestController} photo functionality
 */
class PetPhotoRestControllerTests {

    @Mock
    private ClinicService clinicService;

    @Mock
    private PetPhotoService petPhotoService;

    private PetRestController petRestController;

    private MockMvc mockMvc;

    private Pet testPet;
    private Pet testPetNoPhoto;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        // Create test pets
        PetType dogType = new PetType();
        dogType.setId(2);
        dogType.setName("dog");

        Owner owner = new Owner();
        owner.setId(1);
        owner.setFirstName("Eduardo");
        owner.setLastName("Rodriquez");
        owner.setAddress("2693 Commerce St.");
        owner.setCity("McFarland");
        owner.setTelephone("6085558763");

        testPet = new Pet();
        testPet.setId(3);
        testPet.setName("Rosy");
        testPet.setBirthDate(LocalDate.now());
        testPet.setType(dogType);
        testPet.setOwner(owner);
        testPet.setPhotoPath("/uploads/pets/3/photo.jpg");

        testPetNoPhoto = new Pet();
        testPetNoPhoto.setId(4);
        testPetNoPhoto.setName("Jewel");
        testPetNoPhoto.setBirthDate(LocalDate.now());
        testPetNoPhoto.setType(dogType);
        testPetNoPhoto.setOwner(owner);

        // Create controller with mocked dependencies
        petRestController = new PetRestController(clinicService, null, petPhotoService);

        // Set up MockMvc
        this.mockMvc = MockMvcBuilders.standaloneSetup(petRestController)
            .setControllerAdvice(new ExceptionControllerAdvice())
            .build();
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testUploadPetPhotoSuccess() throws Exception {
        given(this.clinicService.findPetById(3)).willReturn(testPet);

        MockMultipartFile photoFile = new MockMultipartFile(
            "photo",
            "test-photo.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "test image content".getBytes()
        );

        given(this.petPhotoService.storePhoto(eq(3), any())).willReturn("/uploads/pets/3/photo.jpg");

        this.mockMvc.perform(multipart("/api/pets/3/photo")
                .file(photoFile)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.photoPath").value("/uploads/pets/3/photo.jpg"));

        verify(clinicService).savePet(testPet);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testUploadPetPhotoNotFound() throws Exception {
        given(this.clinicService.findPetById(999)).willReturn(null);

        MockMultipartFile photoFile = new MockMultipartFile(
            "photo",
            "test-photo.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "test image content".getBytes()
        );

        this.mockMvc.perform(multipart("/api/pets/999/photo")
                .file(photoFile)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testUploadPetPhotoIOException() throws Exception {
        given(this.clinicService.findPetById(3)).willReturn(testPet);

        MockMultipartFile photoFile = new MockMultipartFile(
            "photo",
            "test-photo.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            "test image content".getBytes()
        );

        doThrow(new IOException("Storage error")).when(this.petPhotoService).storePhoto(eq(3), any());

        this.mockMvc.perform(multipart("/api/pets/3/photo")
                .file(photoFile)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetPhotoSuccess() throws Exception {
        given(this.clinicService.findPetById(3)).willReturn(testPet);

        Resource photoResource = new ByteArrayResource("test image content".getBytes());
        given(this.petPhotoService.loadPhoto(3)).willReturn(photoResource);

        this.mockMvc.perform(get("/api/pets/3/photo")
                .accept(MediaType.IMAGE_JPEG_VALUE))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_JPEG_VALUE))
            .andExpect(header().string("Content-Disposition", "inline"))
            .andExpect(content().bytes("test image content".getBytes()));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetPhotoNotFound() throws Exception {
        given(this.clinicService.findPetById(999)).willReturn(null);

        this.mockMvc.perform(get("/api/pets/999/photo")
                .accept(MediaType.IMAGE_JPEG_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetPhotoNoPhotoPath() throws Exception {
        given(this.clinicService.findPetById(4)).willReturn(testPetNoPhoto);

        this.mockMvc.perform(get("/api/pets/4/photo")
                .accept(MediaType.IMAGE_JPEG_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetPhotoIOException() throws Exception {
        given(this.clinicService.findPetById(3)).willReturn(testPet);

        doThrow(new IOException("Loading error")).when(this.petPhotoService).loadPhoto(3);

        this.mockMvc.perform(get("/api/pets/3/photo")
                .accept(MediaType.IMAGE_JPEG_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testDeletePetPhotoSuccess() throws Exception {
        given(this.clinicService.findPetById(3)).willReturn(testPet);

        given(this.petPhotoService.deletePhoto(3)).willReturn(true);

        this.mockMvc.perform(delete("/api/pets/3/photo")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        verify(clinicService).savePet(testPet);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testDeletePetPhotoNotFound() throws Exception {
        given(this.clinicService.findPetById(999)).willReturn(null);

        this.mockMvc.perform(delete("/api/pets/999/photo")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testDeletePetPhotoNoPhotoPath() throws Exception {
        given(this.clinicService.findPetById(4)).willReturn(testPetNoPhoto);

        this.mockMvc.perform(delete("/api/pets/4/photo")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testDeletePetPhotoDeleteFailure() throws Exception {
        given(this.clinicService.findPetById(3)).willReturn(testPet);

        given(this.petPhotoService.deletePhoto(3)).willReturn(false);

        this.mockMvc.perform(delete("/api/pets/3/photo")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testDeletePetPhotoIOException() throws Exception {
        given(this.clinicService.findPetById(3)).willReturn(testPet);

        doThrow(new IOException("Deletion error")).when(this.petPhotoService).deletePhoto(3);

        this.mockMvc.perform(delete("/api/pets/3/photo")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError());
    }
}
