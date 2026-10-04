package com.example.gmailwhatsapp.config;

import com.example.gmailwhatsapp.service.GmailService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GmailWatchScheduler {

    private final GmailService gmailService;

    public GmailWatchScheduler(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    // Gmail watch() subscriptions expire after 7 days; renew every 6 to stay safe.
    @Scheduled(fixedRate = 6L * 24 * 60 * 60 * 1000)
    public void renewWatches() {
        gmailService.renewAllWatches();
    }
}
