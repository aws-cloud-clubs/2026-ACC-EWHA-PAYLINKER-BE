package com.paylinker.api.campaign.dto.response;

public record CampaignCreateResponse(
        String campaignId,
        String campaignName,
        String status,
        String createdAt
) {}