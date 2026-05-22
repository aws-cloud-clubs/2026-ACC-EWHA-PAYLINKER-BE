package com.paylinker.api.notification.dto.response;

import java.util.List;
import lombok.Builder;

@Builder
public record CheckItemListResponse(
        int totalCount,
        int openCount,
        int inProgressCount,
        int page,
        int pageSize,
        List<CheckItemSummary> items) {}
