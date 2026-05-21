package com.paylinker.api.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResendRequestActionResponse(
        String requestId,
        String status,
        String processedAt,
        String newSendJobId,
        String newLinkExpiresAt) {}
