package com.paylinker.api.securelink.dto.response;

import lombok.Builder;

@Builder
public record ResendSubmitResponse(
        String requestId,
        String status   // 항상 "REQUESTED"
) {}
