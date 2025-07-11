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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.mapper.PetMapper;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.rest.api.PetsApi;
import org.springframework.samples.petclinic.rest.dto.PetDto;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Vitaliy Fedoriv
 */

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api")
public class PetRestController implements PetsApi {

    private final ClinicService clinicService;

    private final PetMapper petMapper;

    public PetRestController(ClinicService clinicService, PetMapper petMapper) {
        this.clinicService = clinicService;
        this.petMapper = petMapper;
    }

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @Override
    public ResponseEntity<PetDto> getPet(Integer petId) {
        PetDto pet = petMapper.toPetDto(this.clinicService.findPetById(petId));
        if (pet == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(pet, HttpStatus.OK);
    }

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @Override
    public ResponseEntity<List<PetDto>> listPets(Integer page, Integer size, List<String> sort) {
        // Create Pageable object from the parameters
        Pageable pageable;
        if (sort != null && !sort.isEmpty()) {
            // Parse sort parameters
            System.out.println("[DEBUG_LOG] Sort parameters: " + sort);

            // Create a list to hold the sort orders
            List<Sort.Order> orders = new ArrayList<>();

            // Check if we have a single parameter with comma (e.g., "name,desc")
            if (sort.size() == 1 && sort.get(0).contains(",")) {
                String sortParam = sort.get(0);
                String[] parts = sortParam.split(",");
                String property = parts[0];
                Sort.Direction direction = (parts.length > 1 && parts[1].equalsIgnoreCase("desc"))
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
                System.out.println("[DEBUG_LOG] Sort property: " + property + ", direction: " + direction);
                orders.add(new Sort.Order(direction, property));
            } else {
                // Process multiple sort parameters
                for (String sortParam : sort) {
                    String[] parts = sortParam.split(",");
                    String property = parts[0];
                    Sort.Direction direction = (parts.length > 1 && parts[1].equalsIgnoreCase("desc"))
                        ? Sort.Direction.DESC : Sort.Direction.ASC;
                    System.out.println("[DEBUG_LOG] Sort property: " + property + ", direction: " + direction);
                    orders.add(new Sort.Order(direction, property));
                }
            }

            pageable = PageRequest.of(page, size, Sort.by(orders));
            System.out.println("[DEBUG_LOG] Created pageable with sort: " + pageable);
        } else {
            pageable = PageRequest.of(page, size);
            System.out.println("[DEBUG_LOG] Created pageable without sort: " + pageable);
        }

        // Get paginated pets
        Page<Pet> petPage = this.clinicService.findAllPets(pageable);
        List<PetDto> pets = new ArrayList<>(petMapper.toPetsDto(petPage.getContent()));

        if (pets.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(pets, HttpStatus.OK);
    }


    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @Override
    public ResponseEntity<PetDto> updatePet(Integer petId, PetDto petDto) {
        Pet currentPet = this.clinicService.findPetById(petId);
        if (currentPet == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        currentPet.setBirthDate(petDto.getBirthDate());
        currentPet.setName(petDto.getName());
        currentPet.setType(petMapper.toPetType(petDto.getType()));
        this.clinicService.savePet(currentPet);
        return new ResponseEntity<>(petMapper.toPetDto(currentPet), HttpStatus.NO_CONTENT);
    }

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @Override
    public ResponseEntity<PetDto> deletePet(Integer petId) {
        Pet pet = this.clinicService.findPetById(petId);
        if (pet == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        this.clinicService.deletePet(pet);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
