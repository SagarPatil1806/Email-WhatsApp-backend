package com.example.gmailwhatsapp.web;

import com.example.gmailwhatsapp.service.TokenStorageService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
public class AuthController {

    private final TokenStorageService tokenStorageService;

    public AuthController(TokenStorageService tokenStorageService) {
        this.tokenStorageService = tokenStorageService;
    }

    // Public endpoint the React app can call on load to check login state
    // without triggering a 401 redirect.
    @GetMapping("/api/status")
    public Map<String, Object> status(Principal principal) {
        if (principal == null) {
            return Map.of("authenticated", false);
        }
        return Map.of("authenticated", true);
    }


    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    // Requires auth (see SecurityConfig). Returns the logged-in user's basic
    // profile info plus whether we successfully captured a Gmail refresh token.
    @GetMapping("/api/me")
    public Map<String, Object> me(Principal principal) {
        OAuth2User user = (OAuth2User) ((org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken) principal).getPrincipal();
        String email = user.getAttribute("email");
        String name = user.getAttribute("name");

        return Map.of(
                "email", email,
                "name", name,
                "gmailConnected", tokenStorageService.isConnected(email)
        );
    }
}
