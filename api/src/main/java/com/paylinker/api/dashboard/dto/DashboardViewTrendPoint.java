package com.paylinker.api.dashboard.dto;

import java.math.BigDecimal;

public record DashboardViewTrendPoint(
        String snapshotAt,
        Integer viewedCount,
        BigDecimal viewRate
) {
}
