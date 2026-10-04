package com.example.gmailwhatsapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class WhatsAppService {

    @Value("${whatsapp.access-token}")
    private String accessToken;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${whatsapp.to}")
    private String toNumber; // digits only with country code, e.g. 919689049445

    @Value("${whatsapp.api-version}")
    private String apiVersion;

    @Value("${whatsapp.template-name}")
    private String templateName;

    @Value("${whatsapp.template-language}")
    private String templateLanguage;

    private final RestClient restClient = RestClient.create();

    public void sendNewMailAlert(String from, String subject, String category, String summary) {
        Map<String, Object> payload = (templateName == null || templateName.isBlank())
                ? textPayload(from, subject, category, summary)
                : templatePayload(from, subject, category, summary);

        try {
            String response = restClient.post()
                    .uri("https://graph.facebook.com/{version}/{id}/messages", apiVersion, phoneNumberId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            System.out.println("WhatsApp message sent: " + response);
        } catch (RestClientResponseException e) {
            System.out.println("WhatsApp API error " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.out.println("Failed to send WhatsApp message: " + e.getMessage());
        }
    }

    // Free-form text: only delivered if you messaged the number in the last 24 hours
    private Map<String, Object> textPayload(String from, String subject, String category, String summary) {
        String body = "📧 *New Email*\n"
                + "*From:* " + from + "\n"
                + "*Subject:* " + subject + "\n"
                + "*Type:* " + category + "\n"
                + "*Summary:* " + summary;

        return Map.of(
                "messaging_product", "whatsapp",
                "to", toNumber,
                "type", "text",
                "text", Map.of("body", body)
        );
    }

    // Approved template: works any time. Variables map to {{1}}..{{4}} in order.
    private Map<String, Object> templatePayload(String from, String subject, String category, String summary) {
        List<Map<String, String>> parameters = List.of(
                Map.of("type", "text", "text", clean(from, 100)),
                Map.of("type", "text", "text", clean(subject, 150)),
                Map.of("type", "text", "text", clean(category, 30)),
                Map.of("type", "text", "text", clean(summary, 300))
        );

        return Map.of(
                "messaging_product", "whatsapp",
                "to", toNumber,
                "type", "template",
                "template", Map.of(
                        "name", templateName,
                        "language", Map.of("code", templateLanguage),
                        "components", List.of(Map.of("type", "body", "parameters", parameters))
                )
        );
    }

    // Template variables can't be empty or contain newlines/tabs
    private String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) return "-";
        String cleaned = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").trim();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength - 1) + "…" : cleaned;
    }
}