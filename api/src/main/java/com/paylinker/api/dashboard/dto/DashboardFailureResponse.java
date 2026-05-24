package com.paylinker.api.dashboard.dto;

import java.util.List;

public record DashboardFailureResponse(
        String campaignId,
        Integer totalFailedCount,
        List<DashboardFailedRecipientPreview> previews
) {
}
