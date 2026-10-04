package com.example.gmailwhatsapp.service;

import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * TEMPORARY in-memory store, keyed by email. Holds each user's Gmail
 * refresh token plus the bookkeeping needed for push notifications
 * (historyId, watch expiration). Lost on restart.
 *
 * Replace with a real DB (a `users` table) before this goes past local
 * testing — swap the method bodies below for repository calls, nothing
 * else in the app needs to change.
 */
@Service
public class TokenStorageService {

    public static class UserRecord {
        String refreshToken;
        BigInteger historyId;
        Long watchExpirationEpochMillis;
    }

    private final Map<String, UserRecord> records = new ConcurrentHashMap<>();

    public void saveRefreshToken(String email, String refreshToken) {
        if (refreshToken == null) return;
        records.computeIfAbsent(email, e -> new UserRecord()).refreshToken = refreshToken;
    }

    public String getRefreshToken(String email) {
        UserRecord record = records.get(email);
        return record != null ? record.refreshToken : null;
    }

    public boolean isConnected(String email) {
        return records.containsKey(email) && records.get(email).refreshToken != null;
    }

    public void updateHistoryId(String email, BigInteger historyId) {
        UserRecord record = records.get(email);
        if (record != null) record.historyId = historyId;
    }

    public BigInteger getHistoryId(String email) {
        UserRecord record = records.get(email);
        return record != null ? record.historyId : null;
    }

    public void updateWatchExpiration(String email, Long expirationEpochMillis) {
        UserRecord record = records.get(email);
        if (record != null) record.watchExpirationEpochMillis = expirationEpochMillis;
    }

    public Set<String> getAllEmails() {
        return records.keySet();
    }
}
