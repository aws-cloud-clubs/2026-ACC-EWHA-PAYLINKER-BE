package com.paylinker.api.document.dto;

import com.paylinker.api.entity.enums.DocumentMatchStatus;

public record DocumentMatchItem(
        String campaignRecipientId,
        String recipientName,
        String employeeNo,
        String email,
        DocumentMatchStatus matchStatus,
        String matchKey,
        String documentId
) {
}
