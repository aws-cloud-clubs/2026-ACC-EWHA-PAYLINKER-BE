package com.paylinker.api.notification.dto.response;

import lombok.Builder;

@Builder
public record ResendRequestItem(
        String requestId,
        String campaignId,
        String campaignName,
        String recipientName,
        String employeeNo,
        String email,
        String resendReason,
        String status,
        String requestedAt,
        String processedAt,
        String processedBy) {}
