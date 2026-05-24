package com.paylinker.api.campaign.dto.response;

import com.paylinker.api.entity.enums.CampaignStatus;
import java.util.List;

public record CampaignListResponse(
        int totalCount,
        int page,
        int pageSize,
        List<CampaignListItem> items
) {
    public record CampaignListItem(
            String campaignId,
            String campaignName,
            CampaignStatus status,
            String scheduledSendAt,
            String sendCompletedAt,
            int totalRecipientCount,
            int sendSuccessCount,
            int sendFailedCount,
            int viewedCount,
            String createdAt
    ) {}
}