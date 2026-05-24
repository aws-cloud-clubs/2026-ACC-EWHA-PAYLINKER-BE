package com.paylinker.api.dashboard.dto;

import java.util.List;

public record DashboardSummaryResponse(
        Integer totalUnviewedCount,
        Integer totalFailedCount,
        Integer attentionRequiredCount,
        List<DashboardCampaignSummaryCard> recentCampaigns
) {
}
