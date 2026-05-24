package com.paylinker.api.campaign.dto.response;

import com.paylinker.api.entity.enums.CampaignStatus;

public record CampaignDetailResponse(
        String campaignId,
        String campaignName,
        CampaignStatus status,
        String emailSubject,
        String emailDescription,
        Integer linkTtlHours,
        Boolean allowOneTimeLink,
        Boolean allowResendRequest,
        Integer resendRequestLimit,
        Integer maxRecipients,
        Integer maxDailyCount,
        String scheduledSendAt,
        String sendStartedAt,
        String sendCompletedAt,
        String cancelledAt,
        int totalRecipientCount,
        int sendSuccessCount,
        int sendFailedCount,
        int viewedCount,
        int unviewedCount,
        String createdAt
) {}