package com.paylinker.api.campaign.dto.response;

import lombok.Builder;

@Builder
public record ManualResendResponse(
        String campaignId,
        int queuedCount,
        int skippedPermanentFailureCount,
        String requestedAt) {}
