package com.paylinker.api.campaign.dto.response;

import lombok.Builder;

@Builder
public record CampaignSendResponse(
        String campaignId,
        int queuedCount,
        String requestedAt) {
}
