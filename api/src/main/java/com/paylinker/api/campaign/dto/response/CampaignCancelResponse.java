package com.paylinker.api.campaign.dto.response;

public record CampaignCancelResponse(
        String campaignId,
        String status,
        String cancelledAt
) {}