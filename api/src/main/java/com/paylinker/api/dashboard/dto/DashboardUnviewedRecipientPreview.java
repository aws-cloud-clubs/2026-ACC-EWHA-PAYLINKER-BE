package com.paylinker.api.dashboard.dto;

public record DashboardUnviewedRecipientPreview(
        String recipientId,
        String name,
        String employeeNo,
        Integer elapsedHours
) {
}
