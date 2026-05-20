package com.paylinker.api.auth;

import java.time.Instant;

public record LinkSession(
        String token,
        String campaignRecipientId,
        String campaignId,
        String recipientEmail,
        Instant expiresAt) {}
