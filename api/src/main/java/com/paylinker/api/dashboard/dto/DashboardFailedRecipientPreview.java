package com.paylinker.api.dashboard.dto;

import com.paylinker.api.entity.enums.SendFailureReason;

public record DashboardFailedRecipientPreview(
        String campaignRecipientId,
        String name,
        String department,
        String email,
        SendFailureReason failureReason
) {
}
