package com.paylinker.api.dashboard.dto;

import java.util.List;

public record DashboardUnviewedResponse(
        String campaignId,
        Integer totalUnviewedCount,
        Integer maxElapsedHours,
        List<DashboardUnviewedRecipientPreview> previews
) {
}
