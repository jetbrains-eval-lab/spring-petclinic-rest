package org.springframework.samples.petclinic.security;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public static final String SESSION_KEY_AUTHENTICATED_USER = "authenticated_user";

    public OAuth2AuthenticationSuccessHandler() {
        setDefaultTargetUrl("/api/auth/login");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            HttpSession session = request.getSession(true);
            Map<String, Object> userInfo = buildUserInfo(oAuth2User, authentication);
            session.setAttribute(SESSION_KEY_AUTHENTICATED_USER, userInfo);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private Map<String, Object> buildUserInfo(OAuth2User oAuth2User, Authentication authentication) {
        Map<String, Object> userInfo = new HashMap<>();
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String email = (String) attrs.get("email");
        String sub = attrs.get("sub") != null ? attrs.get("sub").toString() : null;

        userInfo.put("username", email != null ? email : sub);
        userInfo.put("email", email);
        userInfo.put("firstName", attrs.get("given_name"));
        userInfo.put("lastName", attrs.get("family_name"));
        userInfo.put("pictureUrl", attrs.get("picture"));
        userInfo.put("roles", authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList()));
        return userInfo;
    }
}
