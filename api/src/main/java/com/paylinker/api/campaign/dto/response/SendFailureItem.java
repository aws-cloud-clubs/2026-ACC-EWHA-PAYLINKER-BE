package com.paylinker.api.campaign.dto.response;

import lombok.Builder;

@Builder
public record SendFailureItem(
        String campaignRecipientId,
        String name,
        String department,
        String email,
        String failureReason,
        String failedAt,
        Integer retryCount,
        String currentStatus,
        String lastSendJobId) {}
