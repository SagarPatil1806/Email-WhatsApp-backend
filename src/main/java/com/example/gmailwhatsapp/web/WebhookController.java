package com.example.gmailwhatsapp.web;

import com.example.gmailwhatsapp.service.GmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final GmailService gmailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.webhook.secret}")
    private String webhookSecret;

    public WebhookController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    // Pub/Sub calls this. Body shape:
    // { "message": { "data": "<base64 JSON {emailAddress, historyId}>", "messageId": "...", "publishTime": "..." },
    //   "subscription": "..." }
    @PostMapping("/gmail")
    public void handleGmailPush(@RequestParam(value = "token", required = false) String token,
                                 @RequestBody Map<String, Object> body) {

        if (webhookSecret != null && !webhookSecret.isBlank() && !webhookSecret.equals(token)) {
            System.out.println("Rejected webhook call. Received token=[" + token + "] expected=[" + webhookSecret + "]");
            return;
        }

        try {
            Map<?, ?> message = (Map<?, ?>) body.get("message");
            String dataB64 = (String) message.get("data");
            String decoded = new String(Base64.getDecoder().decode(dataB64));

            Map<?, ?> payload = objectMapper.readValue(decoded, Map.class);
            String emailAddress = (String) payload.get("emailAddress");
            BigInteger historyId = new BigInteger(String.valueOf(payload.get("historyId")));
            gmailService.processNewMail(emailAddress, historyId);
        } catch (Exception e) {
            System.out.println("Failed to handle Gmail webhook: " + e.getMessage());
        }
    }
}
