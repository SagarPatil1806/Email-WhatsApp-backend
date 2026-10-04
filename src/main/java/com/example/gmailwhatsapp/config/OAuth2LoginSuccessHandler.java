package com.example.gmailwhatsapp.config;

import com.example.gmailwhatsapp.service.GmailService;
import com.example.gmailwhatsapp.service.TokenStorageService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final TokenStorageService tokenStorageService;
    private final GmailService gmailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public OAuth2LoginSuccessHandler(OAuth2AuthorizedClientService authorizedClientService,
                                      TokenStorageService tokenStorageService,
                                      GmailService gmailService) {
        this.authorizedClientService = authorizedClientService;
        this.tokenStorageService = tokenStorageService;
        this.gmailService = gmailService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName());

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        OAuth2RefreshToken refreshToken = client.getRefreshToken();
        if (refreshToken == null) {
            // Happens if the user had already granted consent before and Google
            // skipped the consent screen. Revoke access at
            // https://myaccount.google.com/permissions and log in again to fix.
            System.out.println("WARNING: no refresh token received for " + email);
        } else {
            tokenStorageService.saveRefreshToken(email, refreshToken.getTokenValue());
            System.out.println("Stored refresh token for " + email);
            gmailService.startWatch(email);
        }

        response.sendRedirect(frontendUrl + "/dashboard?connected=true");
    }
}
