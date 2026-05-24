package com.paylinker.api.campaign.dto.response;

import lombok.Builder;

@Builder
public record ViewHistoryItem(
        String campaignRecipientId,
        String name,
        String employeeNo,
        String email,
        Boolean isViewed,
        String firstViewedAt,
        Integer viewCount,
        String linkStatus,
        String linkExpiresAt) {}
