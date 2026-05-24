package com.paylinker.api.campaign.dto.response;

public record CampaignScheduleResponse(
        String campaignId,
        String status,
        String scheduledSendAt
) {}