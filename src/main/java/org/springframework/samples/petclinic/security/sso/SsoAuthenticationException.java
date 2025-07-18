package org.springframework.samples.petclinic.security.sso;

import org.springframework.security.core.AuthenticationException;

public class SsoAuthenticationException extends AuthenticationException {

    public SsoAuthenticationException(String msg) {
        super(msg);
    }

    public SsoAuthenticationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
