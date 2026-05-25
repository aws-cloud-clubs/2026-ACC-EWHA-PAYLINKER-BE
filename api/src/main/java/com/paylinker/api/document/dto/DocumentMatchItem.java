package com.paylinker.api.document.dto;

public record DocumentMatchItem(
        String campaignRecipientId,
        String recipientName,
        String employeeNo,
        String email,
        String matchStatus,
        String matchKey,
        String documentId
) {
}
