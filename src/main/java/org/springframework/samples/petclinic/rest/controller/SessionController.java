package org.springframework.samples.petclinic.rest.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.security.OAuth2AuthenticationSuccessHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/session")
public class SessionController {

    private static final String RESERVED_KEY = OAuth2AuthenticationSuccessHandler.SESSION_KEY_AUTHENTICATED_USER;

    @GetMapping("/user")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getUser(HttpSession session, Authentication authentication) {
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = (Map<String, Object>) session.getAttribute(RESERVED_KEY);

        if (userInfo == null) {
            userInfo = buildUserInfoFromAuth(authentication);
            if (userInfo != null) {
                session.setAttribute(RESERVED_KEY, userInfo);
            }
        }

        if (userInfo == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/attributes/{key}")
    public ResponseEntity<Object> getAttribute(HttpSession session, @PathVariable String key) {
        if (RESERVED_KEY.equals(key)) {
            return ResponseEntity.badRequest().build();
        }
        Object value = session.getAttribute(key);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(value);
    }

    @PutMapping("/attributes/{key}")
    public ResponseEntity<Object> setAttribute(HttpSession session,
                                               @PathVariable String key,
                                               @RequestBody Object value) {
        if (RESERVED_KEY.equals(key)) {
            return ResponseEntity.badRequest().build();
        }
        session.setAttribute(key, value);
        return ResponseEntity.ok(value);
    }

    @DeleteMapping("/attributes/{key}")
    public ResponseEntity<Void> removeAttribute(HttpSession session, @PathVariable String key) {
        if (RESERVED_KEY.equals(key)) {
            return ResponseEntity.badRequest().build();
        }
        session.removeAttribute(key);
        return ResponseEntity.ok().build();
    }

    private Map<String, Object> buildUserInfoFromAuth(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Map<String, Object> info = new HashMap<>();
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            Map<String, Object> attrs = oAuth2User.getAttributes();
            Object email = attrs.get("email");
            info.put("username", email != null ? email.toString() : authentication.getName());
            info.put("email", email);
            info.put("firstName", attrs.get("given_name"));
            info.put("lastName", attrs.get("family_name"));
            info.put("pictureUrl", attrs.get("picture"));
        } else {
            info.put("username", authentication.getName());
        }
        info.put("roles", authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList()));
        return info;
    }
}
