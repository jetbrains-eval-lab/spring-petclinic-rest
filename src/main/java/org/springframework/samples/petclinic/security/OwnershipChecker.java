package org.springframework.samples.petclinic.security;

import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class OwnershipChecker {

    private final ClinicService clinicService;

    public OwnershipChecker(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    public boolean isOwnerProfile(Authentication authentication, int ownerId) {
        if (authentication == null) {
            return false;
        }
        Owner owner = clinicService.findOwnerById(ownerId);
        return owner != null && owner.getUsername() != null
            && owner.getUsername().equals(authentication.getName());
    }

    public boolean isVetProfile(Authentication authentication, int vetId) {
        if (authentication == null) {
            return false;
        }
        Vet vet = clinicService.findVetById(vetId);
        return vet != null && vet.getUsername() != null
            && vet.getUsername().equals(authentication.getName());
    }

    public boolean isPetOwner(Authentication authentication, int petId) {
        if (authentication == null) {
            return false;
        }
        Pet pet = clinicService.findPetById(petId);
        if (pet == null || pet.getOwner() == null) {
            return false;
        }
        Owner owner = pet.getOwner();
        // Handle skeleton owner objects in same transaction
        if (owner.getUsername() == null) {
            owner = clinicService.findOwnerById(owner.getId());
        }
        return owner != null && authentication.getName().equals(owner.getUsername());
    }
}
