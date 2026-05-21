package com.paylinker.api.notification.dto.response;

import lombok.Builder;

@Builder
public record CheckItemSummary(
        String checkItemId,
        String itemType,
        String checkStatus,
        String campaignId,
        String campaignName,
        String recipientName,
        String relatedRequestId,
        String createdAt,
        String deepLink) {}
