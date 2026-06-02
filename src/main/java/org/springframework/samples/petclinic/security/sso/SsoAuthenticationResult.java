package org.springframework.samples.petclinic.security.sso;

import java.util.List;

public record SsoAuthenticationResult(boolean authenticated, List<String> roles){}
