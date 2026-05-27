package com.paylinker.api.secure.dto;

import lombok.Builder;

@Builder
public record LinkSessionResponse(
        String linkSessionToken,
        String campaignRecipientId,
        String campaignId,
        String campaignName,
        String recipientName,
        Integer documentCount,
        String issuedAt,
        String expiresAt) {}
