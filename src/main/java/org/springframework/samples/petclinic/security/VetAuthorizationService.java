package org.springframework.samples.petclinic.security;

import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Authorization service for checking vet-related resource ownership.
 * Used in SpEL expressions within @PreAuthorize annotations.
 */
@Service("vetAuthz")
public class VetAuthorizationService {

    private final ClinicService clinicService;

    public VetAuthorizationService(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    /**
     * Checks if the authenticated user is the vet with the given ID.
     *
     * @param vetId the ID of the vet to check
     * @param authentication the current authentication
     * @return true if the authenticated user is this vet
     */
    public boolean isVet(Integer vetId, Authentication authentication) {
        if (authentication == null || vetId == null) {
            return false;
        }
        
        String username = authentication.getName();
        Vet vet = clinicService.findVetById(vetId);
        
        if (vet == null || vet.getUsername() == null) {
            return false;
        }
        
        return vet.getUsername().equals(username);
    }
}

