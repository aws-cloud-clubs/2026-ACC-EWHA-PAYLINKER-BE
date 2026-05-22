package com.paylinker.api.campaign.dto.response;

import java.util.List;
import lombok.Builder;

@Builder
public record SendFailureResponse(
        String campaignId,
        int totalCount,
        int page,
        int pageSize,
        List<SendFailureItem> items) {}
