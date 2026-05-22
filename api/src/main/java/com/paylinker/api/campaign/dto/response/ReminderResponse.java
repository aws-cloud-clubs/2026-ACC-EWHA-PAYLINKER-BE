package com.paylinker.api.campaign.dto.response;

import lombok.Builder;

@Builder
public record ReminderResponse(
        String campaignId,
        int queuedCount,
        int skippedExpiredCount,
        String requestedAt) {}
