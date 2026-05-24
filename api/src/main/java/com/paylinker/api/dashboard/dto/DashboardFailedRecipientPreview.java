package com.paylinker.api.dashboard.dto;

public record DashboardFailedRecipientPreview(
        String campaignRecipientId,
        String name,
        String department,
        String email,
        String failureReason
) {
}
