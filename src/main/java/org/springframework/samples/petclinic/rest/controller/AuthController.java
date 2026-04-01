package org.springframework.samples.petclinic.rest.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.security.OAuth2AuthenticationSuccessHandler;
import org.springframework.samples.petclinic.security.OAuth2Properties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/auth")
public class AuthController {

    private final OAuth2Properties oauth2Properties;

    @Autowired
    public AuthController(OAuth2Properties oauth2Properties) {
        this.oauth2Properties = oauth2Properties;
    }

    @GetMapping("/login")
    public ResponseEntity<Map<String, Object>> getLoginStatus(HttpSession session) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> response = new HashMap<>();

        boolean authenticated = auth != null && auth.isAuthenticated()
            && !"anonymousUser".equals(auth.getPrincipal());

        response.put("authenticated", authenticated);

        if (authenticated) {
            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = (Map<String, Object>)
                session.getAttribute(OAuth2AuthenticationSuccessHandler.SESSION_KEY_AUTHENTICATED_USER);
            if (userInfo != null) {
                response.put("user", userInfo);
            } else {
                response.put("username", auth.getName());
                response.put("roles", auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
            }
        } else {
            String provider = oauth2Properties.getDefaultLoginProvider();
            response.put("loginUrl", "/oauth2/authorization/" + provider);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }
}
