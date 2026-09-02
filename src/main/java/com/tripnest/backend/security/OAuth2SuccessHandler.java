package com.tripnest.backend.security;

import com.tripnest.backend.entity.Role;
import com.tripnest.backend.entity.User;
import com.tripnest.backend.repository.RoleRepository;
import com.tripnest.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name  = oAuth2User.getAttribute("name");

        // User pehle se hai ya naya banao
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    Role role = roleRepository
                            .findByRoleName(Role.RoleName.TRAVELER)
                            .orElseThrow(() -> new RuntimeException("Role not found"));

                    User newUser = User.builder()
                            .name(name)
                            .email(email)
                            .password("OAUTH2_USER") // password nahi hota OAuth users ka
                            .role(role)
                            .isActive(true)
                            .build();

                    return userRepository.save(newUser);
                });

        // JWT token banao
        String token = jwtUtil.generateToken(user.getEmail());

        // Frontend pe redirect karo token ke saath
        String redirectUrl = frontendUrl + "/oauth2/success?token=" + token
                + "&email=" + email
                + "&role=" + user.getRole().getRoleName().name();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}