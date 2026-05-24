package com.paylinker.api.dashboard.dto;

import java.math.BigDecimal;

public record DashboardCampaignSummaryResponse(
        String campaignId,
        String campaignName,
        String status,
        String sendCompletedAt,
        Integer totalRecipientCount,
        Integer sendSuccessCount,
        Integer sendFailedCount,
        Integer viewedCount,
        Integer unviewedCount,
        BigDecimal viewRate
) {
}
