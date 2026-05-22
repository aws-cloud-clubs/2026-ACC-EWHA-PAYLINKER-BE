package com.paylinker.api.notification.dto.response;

import java.util.List;
import lombok.Builder;

@Builder
public record ResendRequestListResponse(
        int totalCount,
        int pendingCount,
        int page,
        int pageSize,
        List<ResendRequestItem> items) {}
