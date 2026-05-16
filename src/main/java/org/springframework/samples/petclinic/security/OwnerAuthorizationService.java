package org.springframework.samples.petclinic.security;

import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Authorization service for checking owner-related resource ownership.
 * Used in SpEL expressions within @PreAuthorize annotations.
 */
@Service("ownerAuthz")
public class OwnerAuthorizationService {

    private final ClinicService clinicService;

    public OwnerAuthorizationService(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    /**
     * Checks if the authenticated user is the owner with the given ID.
     *
     * @param ownerId the ID of the owner to check
     * @param authentication the current authentication
     * @return true if the authenticated user owns this owner record
     */
    public boolean isOwner(Integer ownerId, Authentication authentication) {
        if (authentication == null || ownerId == null) {
            return false;
        }
        
        String username = authentication.getName();
        Owner owner = clinicService.findOwnerById(ownerId);
        
        if (owner == null || owner.getUsername() == null) {
            return false;
        }
        
        return owner.getUsername().equals(username);
    }

    /**
     * Checks if the authenticated user owns the pet with the given ID.
     *
     * @param petId the ID of the pet to check
     * @param authentication the current authentication
     * @return true if the authenticated user owns this pet
     */
    public boolean isPetOwner(Integer petId, Authentication authentication) {
        if (authentication == null || petId == null) {
            return false;
        }
        
        String username = authentication.getName();
        Pet pet = clinicService.findPetById(petId);
        
        if (pet == null || pet.getOwner() == null || pet.getOwner().getUsername() == null) {
            return false;
        }
        
        return pet.getOwner().getUsername().equals(username);
    }

    /**
     * Checks if the authenticated user owns the visit with the given ID.
     *
     * @param visitId the ID of the visit to check
     * @param authentication the current authentication
     * @return true if the authenticated user owns this visit (through the pet's owner)
     */
    public boolean isVisitOwner(Integer visitId, Authentication authentication) {
        if (authentication == null || visitId == null) {
            return false;
        }
        
        String username = authentication.getName();
        Visit visit = clinicService.findVisitById(visitId);
        
        if (visit == null || visit.getPet() == null) {
            return false;
        }
        
        Pet pet = clinicService.findPetById(visit.getPet().getId());
        if (pet == null || pet.getOwner() == null || pet.getOwner().getUsername() == null) {
            return false;
        }
        
        return pet.getOwner().getUsername().equals(username);
    }
}

