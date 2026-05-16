package org.springframework.samples.petclinic.security;

import org.springframework.stereotype.Component;

@Component("roles")
public class Roles {

    public final String OWNER_ADMIN = "ROLE_OWNER_ADMIN";
    public final String VET_ADMIN = "ROLE_VET_ADMIN";
    public final String ADMIN = "ROLE_ADMIN";
    public final String VET = "ROLE_VET";
    public final String OWNER = "ROLE_OWNER";
    public final String USER = "ROLE_USER";
}
