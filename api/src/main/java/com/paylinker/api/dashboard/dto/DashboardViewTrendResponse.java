package com.paylinker.api.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardViewTrendResponse(
        String campaignId,
        String campaignName,
        BigDecimal currentViewRate,
        Integer totalRecipientCount,
        List<DashboardViewTrendPoint> points
) {
}
