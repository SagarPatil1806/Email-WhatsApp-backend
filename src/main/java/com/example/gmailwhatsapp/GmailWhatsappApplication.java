package com.example.gmailwhatsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GmailWhatsappApplication {

    public static void main(String[] args) {
        SpringApplication.run(GmailWhatsappApplication.class, args);
    }
}
