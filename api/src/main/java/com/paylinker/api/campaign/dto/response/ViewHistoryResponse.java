package com.paylinker.api.campaign.dto.response;

import java.util.List;
import lombok.Builder;

@Builder
public record ViewHistoryResponse(
        String campaignId,
        int totalCount,
        int viewedCount,
        int unviewedCount,
        int page,
        int pageSize,
        List<ViewHistoryItem> items) {}
