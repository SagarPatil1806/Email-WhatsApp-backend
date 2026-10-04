package com.example.gmailwhatsapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    public record EmailAnalysis(String category, String summary) {}

    // Tried in order. Move to the next model if one is overloaded or unavailable.
    private static final List<String> MODELS = List.of(
            "gemini-3.8-flash",
            "gemini-flash-lite-latest",
            "gemini-2.5-flash-lite"
    );

    @Value("${gemini.api-key}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public EmailAnalysis analyze(String from, String subject, String bodyText) {
        String prompt = """
                You are classifying and summarizing an email for a WhatsApp notification.
                From: %s
                Subject: %s
                Body: %s

                Respond with ONLY compact JSON, no markdown fences, no extra text, in this exact shape:
                {"category": "Business|Education|Work|Promotion|Personal|Other", "summary": "one short sentence summarizing the email"}
                """.formatted(from, subject, truncate(bodyText, 4000));

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );

        try {
            String response = callWithFallback(requestBody);

            JsonNode root = mapper.readTree(response);
            String text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            text = text.replace("```json", "").replace("```", "").trim();

            JsonNode parsed = mapper.readTree(text);
            String category = parsed.path("category").asText("Other");
            String summary = parsed.path("summary").asText("(no summary)");
            return new EmailAnalysis(category, summary);
        } catch (Exception e) {
            System.out.println("Gemini analysis failed: " + e.getMessage());
            return new EmailAnalysis("Other", "(summary unavailable)");
        }
    }

    private String callWithFallback(Map<String, Object> requestBody) throws Exception {
        Exception last = null;
        for (String model : MODELS) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    return restClient.post()
                            .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}",
                                    model, apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(requestBody)
                            .retrieve()
                            .body(String.class);
                } catch (HttpServerErrorException e) {
                    // 5xx such as 503 "high demand": brief pause, retry once, then try the next model
                    last = e;
                    System.out.println("Gemini " + model + " returned " + e.getStatusCode() + ", attempt " + attempt);
                    Thread.sleep(500L * attempt);
                } catch (HttpClientErrorException.NotFound e) {
                    // Model name no longer available: skip straight to the next one
                    last = e;
                    System.out.println("Gemini model " + model + " not available, trying next");
                    break;
                }
            }
        }
        throw last;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}