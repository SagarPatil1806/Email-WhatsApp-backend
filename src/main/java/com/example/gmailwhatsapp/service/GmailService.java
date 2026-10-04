package com.example.gmailwhatsapp.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.WatchRequest;
import com.google.api.services.gmail.model.WatchResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import com.google.api.services.gmail.model.MessagePart;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import java.io.IOException;
import java.util.List;

@Service
public class GmailService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    // Full path, e.g. projects/your-project-id/topics/gmail-notifications
    @Value("${app.pubsub.topic}")
    private String pubsubTopic;

    private final TokenStorageService tokenStorageService;

    private final WhatsAppService whatsAppService;

    private final GeminiService geminiService;

    private final java.util.Set<String> processedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public GmailService(TokenStorageService tokenStorageService, WhatsAppService whatsAppService, GeminiService geminiService) {
        this.tokenStorageService = tokenStorageService;
        this.whatsAppService = whatsAppService;
        this.geminiService = geminiService;
    }

    private Gmail buildClient(String refreshToken) throws Exception {
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build();

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Mailbridge")
                .build();
    }

    /** Starts (or renews) Gmail push notifications for one user's inbox. */
    public void startWatch(String email) {
        String refreshToken = tokenStorageService.getRefreshToken(email);
        if (refreshToken == null) {
            System.out.println("No refresh token for " + email + ", skipping watch()");
            return;
        }
        try {
            Gmail gmail = buildClient(refreshToken);

            WatchRequest watchRequest = new WatchRequest()
                    .setTopicName(pubsubTopic)
                    .setLabelIds(List.of("INBOX"));

            WatchResponse response = gmail.users().watch("me", watchRequest).execute();

            tokenStorageService.updateHistoryId(email, response.getHistoryId());
            tokenStorageService.updateWatchExpiration(email, response.getExpiration());

            System.out.println("watch() started for " + email + ", expires " + response.getExpiration());
        } catch (Exception e) {
            System.out.println("Failed to start watch() for " + email + ": " + e.getMessage());
        }
    }

    /** Gmail watch() subscriptions expire after 7 days; call this on a schedule, comfortably before that. */
    public void renewAllWatches() {
        for (String email : tokenStorageService.getAllEmails()) {
            startWatch(email);
        }
    }

    /**
     * Called when a Pub/Sub push notification arrives. Diffs everything
     * that changed since the last known historyId and, for now, prints a
     * summary of each new message — this is where the WhatsApp send plugs
     * in next.
     */
    public void processNewMail(String email, BigInteger notifiedHistoryId) {
        String refreshToken = tokenStorageService.getRefreshToken(email);
        if (refreshToken == null) {
            System.out.println("Got a notification for an unknown user: " + email);
            return;
        }

        BigInteger lastHistoryId = tokenStorageService.getHistoryId(email);
        if (lastHistoryId == null) {
            // Nothing to diff against yet (e.g. app just restarted); just record the baseline.
            tokenStorageService.updateHistoryId(email, notifiedHistoryId);
            return;
        }

        try {
            Gmail gmail = buildClient(refreshToken);

            ListHistoryResponse historyResponse = gmail.users().history()
                    .list("me")
                    .setStartHistoryId(lastHistoryId)
                    .setHistoryTypes(List.of("messageAdded"))
                    .execute();

            List<History> historyList = historyResponse.getHistory();
            if (historyList != null) {
                for (History history : historyList) {
                    if (history.getMessagesAdded() == null) continue;
                    for (HistoryMessageAdded added : history.getMessagesAdded()) {
                        describeMessage(gmail, added.getMessage().getId());
                    }
                }
            }

            tokenStorageService.updateHistoryId(email, notifiedHistoryId);
        } catch (Exception e) {
            System.out.println("Failed to process new mail for " + email + ": " + e.getMessage());
        }
    }



    private void describeMessage(Gmail gmail, String messageId) throws IOException {
        if (processedMessageIds.size() > 1000) processedMessageIds.clear(); // keep memory bounded
        if (!processedMessageIds.add(messageId)) {
            return; // already handled by an earlier notification
        }

        Message message = gmail.users().messages().get("me", messageId)
                .setFormat("full")
                .execute();

        String from = "(unknown sender)";
        String subject = "(no subject)";
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                if ("From".equals(header.getName())) from = header.getValue();
                if ("Subject".equals(header.getName())) subject = header.getValue();
            }
        }

        String bodyText = extractPlainText(message.getPayload());
        if (bodyText.isBlank() && message.getSnippet() != null) {
            bodyText = message.getSnippet();
        }

        GeminiService.EmailAnalysis analysis = geminiService.analyze(from, subject, bodyText);

        System.out.println("NEW MAIL -> From: " + from + " | Subject: " + subject
                + " | Type: " + analysis.category() + " | Summary: " + analysis.summary());

        whatsAppService.sendNewMailAlert(from, subject, analysis.category(), analysis.summary());
    }

    private String extractPlainText(MessagePart part) {
        if (part == null) return "";
        if ("text/plain".equals(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
            return decodeBase64Url(part.getBody().getData());
        }
        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                String text = extractPlainText(child);
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private String decodeBase64Url(String data) {
        byte[] bytes = Base64.getUrlDecoder().decode(data);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
