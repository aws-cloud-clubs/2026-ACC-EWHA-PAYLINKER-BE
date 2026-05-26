package com.paylinker.api.document.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record RecipientDocumentResponse(
        String campaignName,
        String emailSubject,
        String emailDescription,
        String recipientName,
        List<RecipientDocumentItem> documents,
        String viewedAt,
        String expiresAt,
        Boolean allowResendRequest) {}
